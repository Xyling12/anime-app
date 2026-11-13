'use strict';

const fs = require('fs');
const path = require('path');

const DAY_MS = 86400000;
const DAY_RETENTION_DAYS = 120;
const INSTALL_RETENTION_DAYS = 180;
const FLUSH_DELAY_MS = 5000;
const MAX_DEFER_MS = 30000;

const storeCache = new Map();

function emptyStore() {
  return { installs: {}, archivedInstalls: 0, days: {}, totalDownloads: 0, lastWeeklyReport: null };
}

function dayKey(timestamp = Date.now()) {
  return new Date(timestamp).toISOString().slice(0, 10);
}

function load(file) {
  try {
    const value = JSON.parse(fs.readFileSync(file, 'utf8'));
    if (value && value.installs && value.days) {
      if (typeof value.archivedInstalls !== 'number') value.archivedInstalls = 0;
      return value;
    }
    return emptyStore();
  } catch (error) {
    if (error.code === 'ENOENT') return emptyStore();
    throw error;
  }
}

function save(file, value) {
  const temporary = file + '.tmp';
  fs.writeFileSync(temporary, JSON.stringify(value));
  fs.renameSync(temporary, file);
}

function cacheKey(file) {
  return path.resolve(String(file));
}

function readDiskEntry(resolved) {
  const store = load(resolved);
  let mtimeMs = 0;
  try { mtimeMs = fs.statSync(resolved).mtimeMs; } catch (_) {}
  return { store, dirty: false, dirtySince: 0, timer: null, mtimeMs };
}

function getEntry(file) {
  const key = cacheKey(file);
  let entry = storeCache.get(key);
  if (!entry) {
    entry = readDiskEntry(key);
    storeCache.set(key, entry);
    return entry;
  }
  if (!entry.dirty) {
    try {
      const stat = fs.statSync(key);
      if (stat.mtimeMs !== entry.mtimeMs) {
        entry = readDiskEntry(key);
        storeCache.set(key, entry);
      }
    } catch (error) {
      if (error.code === 'ENOENT') {
        entry = { store: emptyStore(), dirty: false, dirtySince: 0, timer: null, mtimeMs: 0 };
        storeCache.set(key, entry);
      }
    }
  }
  return entry;
}

function flush(file) {
  const key = cacheKey(file);
  const entry = storeCache.get(key);
  if (!entry) return false;
  if (entry.timer) {
    clearTimeout(entry.timer);
    entry.timer = null;
  }
  if (!entry.dirty) return false;
  if (!fs.existsSync(path.dirname(key))) {
    storeCache.delete(key);
    return false;
  }
  save(key, entry.store);
  try { entry.mtimeMs = fs.statSync(key).mtimeMs; } catch (_) {}
  entry.dirty = false;
  entry.dirtySince = 0;
  return true;
}

function flushAll() {
  for (const key of Array.from(storeCache.keys())) {
    try { flush(key); } catch (_) {}
  }
}

process.once('beforeExit', flushAll);

function scheduleFlush(file, entry, immediate = false) {
  const key = cacheKey(file);
  if (!entry.dirty) entry.dirtySince = Date.now();
  entry.dirty = true;
  if (immediate || Date.now() - entry.dirtySince >= MAX_DEFER_MS) {
    flush(key);
    return;
  }
  if (!entry.timer) {
    entry.timer = setTimeout(() => {
      entry.timer = null;
      try { flush(key); } catch (_) {}
    }, FLUSH_DELAY_MS);
    if (typeof entry.timer.unref === 'function') entry.timer.unref();
  }
}

function cleanText(value, max) {
  return String(value || '').replace(/[^a-zA-Z0-9._-]/g, '').slice(0, max);
}

function ensureDay(store, key) {
  if (!store.days[key]) store.days[key] = { active: {}, firstOpens: 0, downloads: 0, sessionMs: 0 };
  return store.days[key];
}

function prune(store, now) {
  const cutoff = dayKey(now - DAY_RETENTION_DAYS * DAY_MS);
  for (const key of Object.keys(store.days)) {
    if (key < cutoff) delete store.days[key];
  }
  const installCutoff = now - INSTALL_RETENTION_DAYS * DAY_MS;
  let archived = Number(store.archivedInstalls) || 0;
  for (const [id, install] of Object.entries(store.installs)) {
    const seen = Number(install && install.lastSeen);
    if (Number.isFinite(seen) && seen < installCutoff) {
      delete store.installs[id];
      archived += 1;
    }
  }
  store.archivedInstalls = archived;
}

function recordHeartbeat(file, body, userId = null, now = Date.now()) {
  const installId = cleanText(body && body.installId, 80);
  if (!/^[a-f0-9-]{16,80}$/i.test(installId)) return null;
  const platform = ['android', 'web'].includes(body && body.platform) ? body.platform : 'android';
  const version = cleanText(body && body.version, 40) || 'unknown';
  const event = ['start', 'heartbeat', 'stop'].includes(body && body.event) ? body.event : 'heartbeat';
  const entry = getEntry(file);
  const store = entry.store;
  const existing = store.installs[installId];
  const firstOpen = !existing;
  const install = existing || { firstSeen: now, totalMs: 0 };
  const previousSeen = Number(install.lastSeen) || 0;
  const delta = previousSeen && now > previousSeen && now - previousSeen <= 5 * 60000 ? now - previousSeen : 0;
  install.lastSeen = now;
  install.platform = platform;
  install.version = version;
  install.userId = userId || install.userId || null;
  install.totalMs = (Number(install.totalMs) || 0) + delta;
  install.online = event !== 'stop';
  store.installs[installId] = install;
  const day = ensureDay(store, dayKey(now));
  day.active[installId] = platform;
  day.sessionMs += delta;
  if (firstOpen) day.firstOpens += 1;
  prune(store, now);
  // Буферизуем обычные 60с heartbeat'ы, но сразу сбрасываем первый запуск.
  scheduleFlush(file, entry, firstOpen);
  return { ok: true, firstOpen };
}

function recordDownload(file, now = Date.now()) {
  const entry = getEntry(file);
  const store = entry.store;
  store.totalDownloads = (Number(store.totalDownloads) || 0) + 1;
  ensureDay(store, dayKey(now)).downloads += 1;
  prune(store, now);
  scheduleFlush(file, entry, true);
}

function anonymizeUser(file, userId) {
  const entry = getEntry(file);
  const store = entry.store;
  let changed = false;
  for (const install of Object.values(store.installs)) {
    if (Number(install.userId) === Number(userId)) {
      install.userId = null;
      changed = true;
    }
  }
  if (changed) scheduleFlush(file, entry, true);
}

function period(store, days, now) {
  const active = new Set();
  let firstOpens = 0;
  let downloads = 0;
  let sessionMs = 0;
  const byPlatform = { android: new Set(), web: new Set() };
  for (let offset = 0; offset < days; offset++) {
    const day = store.days[dayKey(now - offset * DAY_MS)];
    if (!day) continue;
    firstOpens += Number(day.firstOpens) || 0;
    downloads += Number(day.downloads) || 0;
    sessionMs += Number(day.sessionMs) || 0;
    for (const [id, platform] of Object.entries(day.active || {})) {
      active.add(id);
      if (byPlatform[platform]) byPlatform[platform].add(id);
    }
  }
  return {
    active: active.size,
    android: byPlatform.android.size,
    web: byPlatform.web.size,
    firstOpens,
    downloads,
    sessionMinutes: Math.round(sessionMs / 60000),
    averageSessionMinutes: active.size ? Math.round(sessionMs / 60000 / active.size) : 0,
  };
}

function summary(file, now = Date.now()) {
  const store = getEntry(file).store;
  const online = Object.values(store.installs).filter(item => item.online && now - Number(item.lastSeen) <= 5 * 60000);
  const versions = {};
  for (const item of Object.values(store.installs)) {
    if (now - Number(item.lastSeen) <= 30 * DAY_MS) versions[item.version || 'unknown'] = (versions[item.version || 'unknown'] || 0) + 1;
  }
  return {
    generatedAt: now,
    online: online.length,
    onlineAndroid: online.filter(item => item.platform === 'android').length,
    onlineWeb: online.filter(item => item.platform === 'web').length,
    today: period(store, 1, now),
    last7Days: period(store, 7, now),
    last30Days: period(store, 30, now),
    totalInstalls: (Number(store.archivedInstalls) || 0) + Object.keys(store.installs).length,
    totalDownloads: Number(store.totalDownloads) || 0,
    versions,
  };
}

function weeklyMarker(now = Date.now()) {
  const date = new Date(now);
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  return dayKey(now - daysSinceMonday * DAY_MS);
}

function claimWeeklyReport(file, now = Date.now()) {
  const entry = getEntry(file);
  const store = entry.store;
  const marker = weeklyMarker(now);
  if (store.lastWeeklyReport === marker) return false;
  store.lastWeeklyReport = marker;
  scheduleFlush(file, entry, true);
  return true;
}

module.exports = {
  anonymizeUser,
  claimWeeklyReport,
  dayKey,
  flush,
  flushAll,
  recordDownload,
  recordHeartbeat,
  summary,
  weeklyMarker,
};
