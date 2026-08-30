'use strict';

const fs = require('fs');

function emptyStore() {
  return { installs: {}, days: {}, totalDownloads: 0, lastWeeklyReport: null };
}

function dayKey(timestamp = Date.now()) {
  return new Date(timestamp).toISOString().slice(0, 10);
}

function load(file) {
  try {
    const value = JSON.parse(fs.readFileSync(file, 'utf8'));
    return value && value.installs && value.days ? value : emptyStore();
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

function cleanText(value, max) {
  return String(value || '').replace(/[^a-zA-Z0-9._-]/g, '').slice(0, max);
}

function ensureDay(store, key) {
  if (!store.days[key]) store.days[key] = { active: {}, firstOpens: 0, downloads: 0, sessionMs: 0 };
  return store.days[key];
}

function prune(store, now) {
  const cutoff = dayKey(now - 120 * 86400000);
  for (const key of Object.keys(store.days)) if (key < cutoff) delete store.days[key];
}

function recordHeartbeat(file, body, userId = null, now = Date.now()) {
  const installId = cleanText(body && body.installId, 80);
  if (!/^[a-f0-9-]{16,80}$/i.test(installId)) return null;
  const platform = ['android', 'web'].includes(body && body.platform) ? body.platform : 'android';
  const version = cleanText(body && body.version, 40) || 'unknown';
  const event = ['start', 'heartbeat', 'stop'].includes(body && body.event) ? body.event : 'heartbeat';
  const store = load(file);
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
  save(file, store);
  return { ok: true, firstOpen };
}

function recordDownload(file, now = Date.now()) {
  const store = load(file);
  store.totalDownloads = (Number(store.totalDownloads) || 0) + 1;
  ensureDay(store, dayKey(now)).downloads += 1;
  prune(store, now);
  save(file, store);
}

function anonymizeUser(file, userId) {
  const store = load(file);
  let changed = false;
  for (const install of Object.values(store.installs)) {
    if (Number(install.userId) === Number(userId)) {
      install.userId = null;
      changed = true;
    }
  }
  if (changed) save(file, store);
}

function period(store, days, now) {
  const active = new Set();
  let firstOpens = 0;
  let downloads = 0;
  let sessionMs = 0;
  const byPlatform = { android: new Set(), web: new Set() };
  for (let offset = 0; offset < days; offset++) {
    const day = store.days[dayKey(now - offset * 86400000)];
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
  const store = load(file);
  const online = Object.values(store.installs).filter(item => item.online && now - Number(item.lastSeen) <= 5 * 60000);
  const versions = {};
  for (const item of Object.values(store.installs)) {
    if (now - Number(item.lastSeen) <= 30 * 86400000) versions[item.version || 'unknown'] = (versions[item.version || 'unknown'] || 0) + 1;
  }
  return {
    generatedAt: now,
    online: online.length,
    onlineAndroid: online.filter(item => item.platform === 'android').length,
    onlineWeb: online.filter(item => item.platform === 'web').length,
    today: period(store, 1, now),
    last7Days: period(store, 7, now),
    last30Days: period(store, 30, now),
    totalInstalls: Object.keys(store.installs).length,
    totalDownloads: Number(store.totalDownloads) || 0,
    versions,
  };
}

function weeklyMarker(now = Date.now()) {
  const date = new Date(now);
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  return dayKey(now - daysSinceMonday * 86400000);
}

function claimWeeklyReport(file, now = Date.now()) {
  const store = load(file);
  const marker = weeklyMarker(now);
  if (store.lastWeeklyReport === marker) return false;
  store.lastWeeklyReport = marker;
  save(file, store);
  return true;
}

module.exports = { anonymizeUser, claimWeeklyReport, dayKey, recordDownload, recordHeartbeat, summary, weeklyMarker };
