'use strict';

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { after, before, test } = require('node:test');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..', '..');
const data = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-sync-'));
let gateway, baseUrl, token;

function hash(value) {
  const salt = 'sync-test-salt';
  return `${salt}:${crypto.scryptSync(value, salt, 32).toString('hex')}`;
}
async function port() {
  return new Promise((resolve, reject) => {
    const server = http.createServer(); server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const selected = server.address().port;
      server.close(() => resolve(selected));
    });
  });
}
async function api(route, method = 'GET', body) {
  const response = await fetch(baseUrl + route, {
    method, headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), ...(body ? { 'content-type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: response.status, json: await response.json() };
}

before(async () => {
  fs.writeFileSync(path.join(data, 'users.json'), JSON.stringify({ seq: 1, users: [{ id: 1, nick: 'SyncUser', email: 'sync@example.test', pass: hash('Pass-2026'), emailVerified: true }] }));
  fs.writeFileSync(path.join(data, 'auth-secret'), 'd'.repeat(64));
  const p = await port(); baseUrl = `http://127.0.0.1:${p}`;
  gateway = spawn(process.execPath, [path.join(root, 'server', 'gateway.js')], { cwd: root, env: { ...process.env, ANIPULSE_DATA_DIR: data, ANIPULSE_PORT: String(p), KODIK_TOKEN: 'test' } });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('start timeout')), 10000);
    gateway.stdout.on('data', value => { if (String(value).includes('AniPulse gateway')) { clearTimeout(timeout); resolve(); } });
    gateway.once('error', reject);
  });
  const login = await api('/alapi/auth/login', 'POST', { login: 'SyncUser', password: 'Pass-2026' });
  token = login.json.token; assert.equal(login.status, 200);
});
after(() => { if (gateway && !gateway.killed) gateway.kill(); fs.rmSync(data, { recursive: true, force: true }); });

test('sync keeps the newest progress and favorite tombstones', async () => {
  let result = await api('/alapi/sync', 'POST', { progress: [{ animeId: 42, episode: 1, positionMs: 5000, durationMs: 100000, updatedAt: 100 }], favorites: [{ animeId: 42, title: 'Title', status: 'watching', updatedAt: 100 }] });
  assert.equal(result.status, 200); assert.equal(result.json.progress[0].positionMs, 5000);
  result = await api('/alapi/sync', 'POST', { progress: [{ animeId: 42, episode: 1, positionMs: 1000, durationMs: 100000, updatedAt: 50 }], favorites: [{ animeId: 42, deleted: true, updatedAt: 200 }] });
  assert.equal(result.json.progress[0].positionMs, 5000);
  assert.equal(result.json.favorites[0].deleted, true);
});

test('sync requires authentication and rejects malformed identifiers', async () => {
  const saved = token; token = null;
  assert.equal((await api('/alapi/sync')).status, 401); token = saved;
  const result = await api('/alapi/sync', 'POST', { progress: [{ animeId: -1, episode: 0 }], favorites: [{ animeId: 0 }] });
  assert.equal(result.json.progress.length, 1);
  assert.equal(result.json.progress[0].animeId, 42);
  assert.equal(result.json.favorites.length, 1);
  assert.equal(result.json.favorites[0].animeId, 42);
});
