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
const data = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-lb-'));
let gateway, baseUrl, tokenUser1, tokenUser2;

function hash(value) {
  const salt = 'lb-test-salt';
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

async function api(route, method = 'GET', body, token) {
  const response = await fetch(baseUrl + route, {
    method,
    headers: {
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'content-type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: response.status, json: await response.json() };
}

before(async () => {
  fs.writeFileSync(path.join(data, 'users.json'), JSON.stringify({
    seq: 2,
    users: [
      { id: 1, nick: 'TopWatcher', email: 'top@test.local', pass: hash('Pass-1'), emailVerified: true, avatar: 2 },
      { id: 2, nick: 'CasualFan', email: 'fan@test.local', pass: hash('Pass-2'), emailVerified: true, avatar: 5 },
    ],
  }));
  fs.writeFileSync(path.join(data, 'auth-secret'), 'e'.repeat(64));

  const p = await port();
  baseUrl = `http://127.0.0.1:${p}`;
  gateway = spawn(process.execPath, [path.join(root, 'server', 'gateway.js')], {
    cwd: root,
    env: { ...process.env, ANIPULSE_DATA_DIR: data, ANIPULSE_PORT: String(p), KODIK_TOKEN: 'test' },
  });

  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('start timeout')), 10000);
    gateway.stdout.on('data', value => {
      if (String(value).includes('AniPulse gateway')) {
        clearTimeout(timeout);
        resolve();
      }
    });
    gateway.once('error', reject);
  });

  const login1 = await api('/alapi/auth/login', 'POST', { login: 'TopWatcher', password: 'Pass-1' });
  tokenUser1 = login1.json.token;
  const login2 = await api('/alapi/auth/login', 'POST', { login: 'CasualFan', password: 'Pass-2' });
  tokenUser2 = login2.json.token;
});

after(() => {
  if (gateway && !gateway.killed) gateway.kill();
  fs.rmSync(data, { recursive: true, force: true });
});

test('leaderboard returns sorted ranks and stats based on synced progress', async () => {
  const progressUser1 = [];
  for (let i = 1; i <= 15; i++) {
    progressUser1.push({
      animeId: 100 + i,
      episode: 1,
      positionMs: 1400000,
      durationMs: 1400000,
      watched: true,
      updatedAt: Date.now(),
    });
  }
  await api('/alapi/sync', 'POST', { progress: progressUser1, favorites: [{ animeId: 101, status: 'completed' }] }, tokenUser1);

  const progressUser2 = [];
  for (let i = 1; i <= 5; i++) {
    progressUser2.push({
      animeId: 200 + i,
      episode: 1,
      positionMs: 1400000,
      durationMs: 1400000,
      watched: true,
      updatedAt: Date.now(),
    });
  }
  await api('/alapi/sync', 'POST', { progress: progressUser2 }, tokenUser2);

  const lbRes = await api('/alapi/leaderboard', 'GET', null, tokenUser1);
  assert.equal(lbRes.status, 200);
  assert.equal(lbRes.json.leaderboard.length, 2);
  assert.equal(lbRes.json.leaderboard[0].nick, 'TopWatcher');
  assert.equal(lbRes.json.leaderboard[0].rank, 1);
  assert.equal(lbRes.json.leaderboard[0].episodesWatched, 15);
  assert.equal(lbRes.json.leaderboard[0].levelTitle, 'Любитель');
  assert.equal(lbRes.json.leaderboard[0].completedTitles, 1);

  assert.equal(lbRes.json.leaderboard[1].nick, 'CasualFan');
  assert.equal(lbRes.json.leaderboard[1].rank, 2);
  assert.equal(lbRes.json.leaderboard[1].episodesWatched, 5);
  assert.equal(lbRes.json.leaderboard[1].levelTitle, 'Новичок');

  assert.equal(lbRes.json.myRank.rank, 1);
  assert.equal(lbRes.json.myRank.nick, 'TopWatcher');
});

test('stats/me returns detailed breakdown for current user', async () => {
  const statsRes = await api('/alapi/stats/me', 'GET', null, tokenUser2);
  assert.equal(statsRes.status, 200);
  assert.equal(statsRes.json.nick, 'CasualFan');
  assert.equal(statsRes.json.episodesWatched, 5);
  assert.equal(statsRes.json.rank, 2);
  assert.equal(statsRes.json.level, 1);
  assert.equal(statsRes.json.nextTarget, 10);
});
