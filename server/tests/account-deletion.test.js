'use strict';

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { after, before, test } = require('node:test');
const { spawn } = require('node:child_process');

const projectRoot = path.resolve(__dirname, '..', '..');
const gatewayPath = path.join(projectRoot, 'server', 'gateway.js');
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-account-deletion-'));
const password = 'DeletionPass-2026';
let gateway;
let baseUrl;
let aliceToken;

function passwordHash(value) {
  const salt = 'account-deletion-test-salt';
  return `${salt}:${crypto.scryptSync(value, salt, 32).toString('hex')}`;
}

function writeJson(name, value) {
  fs.writeFileSync(path.join(dataDir, name), JSON.stringify(value));
}

function readJson(name) {
  return JSON.parse(fs.readFileSync(path.join(dataDir, name), 'utf8'));
}

async function freePort() {
  return new Promise((resolve, reject) => {
    const server = http.createServer();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      server.close(() => resolve(port));
    });
  });
}

async function api(route, { method = 'GET', body, token = aliceToken } = {}) {
  const response = await fetch(`${baseUrl}${route}`, {
    method,
    headers: {
      ...(body ? { 'content-type': 'application/json' } : {}),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let json = null;
  try { json = JSON.parse(text); } catch {}
  return { status: response.status, json, text };
}

before(async () => {
  writeJson('users.json', {
    seq: 2,
    users: [
      {
        id: 1, nick: 'Alice', email: 'alice@example.test', pass: passwordHash(password),
        linked: { yandex: 'external-alice' }, emailVerified: true, customAvatar: true,
      },
      {
        id: 2, nick: 'Bob', email: 'bob@example.test', pass: passwordHash(password),
        linked: {}, emailVerified: true,
      },
    ],
  });
  fs.writeFileSync(path.join(dataDir, 'auth-secret'), 'c'.repeat(64));
  writeJson('chat.json', { seq: 2, messages: [
    { id: 1, userId: 1, nick: 'Alice', text: 'delete me' },
    {
      id: 2, userId: 2, nick: 'Bob', text: 'keep me',
      replyTo: { id: 1, userId: 1, nick: 'Alice', text: 'copied private text' },
    },
  ] });
  writeJson('comments.json', { 42: [
    { id: 1, userId: 1, nick: 'Alice', text: 'delete me' },
    { id: 2, userId: 2, nick: 'Bob', text: 'keep me' },
  ] });
  writeJson('ratings.json', { 42: { 1: 10, 2: 8 } });
  writeJson('dms.json', {
    seq: 1,
    threads: { '1:2': [{ id: 1, from: 'Alice', to: 'Bob', text: 'private' }] },
    lastRead: { '1:2': { 1: 1, 2: 1 } },
  });
  writeJson('friends.json', {
    1: { friends: ['2'], incoming: [] },
    2: { friends: ['1'], incoming: ['1'] },
  });
  writeJson('blocks.json', { 1: [2], 2: [1] });
  writeJson('notifications.json', {
    seq: 2,
    byUser: {
      1: [{ id: 1, type: 'system', from: 'AniPulse', text: 'own notification' }],
      2: [{ id: 2, type: 'mention', from: 'Alice', fromUserId: 1, text: 'private excerpt' }],
    },
  });
  writeJson('sync.json', {
    users: {
      1: { progress: { '42:1': { animeId: 42, episode: 1, updatedAt: 1 } }, favorites: {} },
      2: { progress: {}, favorites: {} },
    },
  });
  writeJson('reports.json', {
    seq: 2,
    items: [
      {
        id: 1, reporterId: 1, reporterNick: 'Alice', type: 'chat', targetId: '2',
        targetUserId: 2, targetNick: 'Bob', snapshot: { id: 2, userId: 2, nick: 'Bob', text: 'evidence' },
        status: 'open',
      },
      {
        id: 2, reporterId: 2, reporterNick: 'Bob', type: 'profile', targetId: 'Alice',
        targetUserId: 1, targetNick: 'Alice',
        snapshot: { userId: 1, nick: 'Alice', from: 'Alice', to: 'Bob', text: 'retained moderation evidence' },
        resolvedBy: 'Alice', status: 'resolved',
      },
    ],
  });

  const port = await freePort();
  baseUrl = `http://127.0.0.1:${port}`;
  gateway = spawn(process.execPath, [gatewayPath], {
    cwd: projectRoot,
    env: {
      ...process.env,
      ANIPULSE_DATA_DIR: dataDir,
      ANIPULSE_PORT: String(port),
      KODIK_TOKEN: 'test-token',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('gateway start timeout')), 10_000);
    gateway.once('error', reject);
    gateway.stdout.on('data', chunk => {
      if (String(chunk).includes('AniPulse gateway')) {
        clearTimeout(timeout);
        resolve();
      }
    });
    gateway.stderr.on('data', chunk => {
      const message = String(chunk);
      if (message.trim()) reject(new Error(message));
    });
  });

  const login = await api('/alapi/auth/login', {
    method: 'POST',
    token: null,
    body: { login: 'Alice', password },
  });
  assert.equal(login.status, 200);
  aliceToken = login.json.token;
  const linkCode = await api('/alapi/auth/link-code', { method: 'POST' });
  assert.equal(linkCode.status, 200);
  fs.writeFileSync(path.join(dataDir, 'avatars', '1.img'), 'alice avatar');
});

after(() => {
  if (gateway && !gateway.killed) gateway.kill();
  fs.rmSync(dataDir, { recursive: true, force: true });
});

test('a failed auxiliary cleanup leaves the account authenticated so deletion can be retried', async () => {
  fs.writeFileSync(path.join(dataDir, 'notifications.json'), '{corrupt');
  const failed = await api('/alapi/auth/delete-account', {
    method: 'POST',
    body: { confirm: 'DELETE' },
  });
  assert.equal(failed.status, 500);
  assert.equal(readJson('users.json').users.some(user => user.id === 1), true);

  writeJson('notifications.json', {
    seq: 2,
    byUser: {
      1: [{ id: 1, type: 'system', from: 'AniPulse', text: 'own notification' }],
      2: [{ id: 2, type: 'mention', from: 'Alice', fromUserId: 1, text: 'private excerpt' }],
    },
  });
});

test('deletion erases account data and anonymizes retained moderation evidence', async () => {
  const deleted = await api('/alapi/auth/delete-account', {
    method: 'POST',
    body: { confirm: 'DELETE' },
  });
  assert.equal(deleted.status, 200);
  assert.equal(readJson('users.json').users.some(user => user.id === 1), false);
  assert.equal(readJson('sync.json').users['1'], undefined);
  assert.deepEqual(readJson('sync.json').users['2'], { progress: {}, favorites: {} });
  assert.equal(readJson('chat.json').messages.some(message => message.userId === 1), false);
  assert.deepEqual(readJson('chat.json').messages[0].replyTo, {
    id: 1, userId: null, nick: 'Удалённый аккаунт', text: 'Сообщение удалено',
  });
  assert.equal(readJson('comments.json')['42'].some(comment => comment.userId === 1), false);
  assert.equal(Object.hasOwn(readJson('ratings.json')['42'], '1'), false);
  assert.equal(Object.hasOwn(readJson('dms.json').threads, '1:2'), false);
  assert.deepEqual(readJson('friends.json')['2'], { friends: [], incoming: [] });
  assert.deepEqual(readJson('blocks.json')['2'], []);
  assert.deepEqual(readJson('notifications.json').byUser['2'], []);
  assert.equal(fs.existsSync(path.join(dataDir, 'avatars', '1.img')), false);

  const reports = readJson('reports.json').items;
  assert.equal(reports[0].reporterId, null);
  assert.equal(reports[0].reporterNick, 'Удалённый аккаунт');
  assert.equal(reports[1].targetUserId, null);
  assert.equal(reports[1].targetNick, 'Удалённый аккаунт');
  assert.equal(reports[1].targetId, 'Удалённый аккаунт');
  assert.equal(reports[1].snapshot.userId, null);
  assert.equal(reports[1].snapshot.nick, 'Удалённый аккаунт');
  assert.equal(reports[1].snapshot.from, 'Удалённый аккаунт');
  assert.equal(reports[1].resolvedBy, 'Удалённый аккаунт');
  assert.equal(reports[1].snapshot.text, 'retained moderation evidence');

  const oauth = readJson('oauth-state.json');
  assert.equal(Object.values(oauth.codes).some(code => Number(code.userId) === 1), false);
  const oldToken = await api('/alapi/notifications');
  assert.equal(oldToken.status, 401);
});

test('a deleted nickname and email can be registered again with a new immutable id', async () => {
  const registered = await api('/alapi/auth/register', {
    method: 'POST',
    token: null,
    body: {
      nick: 'Alice',
      email: 'alice@example.test',
      password,
      acceptTerms: true,
      privacyConsent: true,
    },
  });
  assert.equal(registered.status, 200);
  const replacement = readJson('users.json').users.find(user => user.nick === 'Alice');
  assert.equal(replacement.id, 3);
});
