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
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-moderation-'));
const password = 'AuditPass-2026'; // gitleaks:allow — фикстура теста, не настоящий пароль
let gateway;
let baseUrl;
let adminToken;

function passwordHash(value) {
  const salt = 'moderation-test-salt';
  return `${salt}:${crypto.scryptSync(value, salt, 32).toString('hex')}`;
}

function writeJson(name, value) {
  fs.writeFileSync(path.join(dataDir, name), JSON.stringify(value));
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

async function api(route, { method = 'GET', body, token = adminToken } = {}) {
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
    seq: 4,
    users: [
      {
        id: 1,
        nick: 'Moderator',
        email: 'moderator@example.test',
        pass: passwordHash(password),
        linked: {},
        admin: true,
        emailVerified: true,
      },
      {
        id: 2,
        nick: 'Offender',
        email: 'offender@example.test',
        pass: passwordHash(password),
        linked: {},
        emailVerified: true,
        bio: 'unsafe profile',
        favoriteGenre: 'unsafe genre',
        customAvatar: true,
        avatar: -1,
      },
      {
        id: 3,
        nick: 'TargetUser',
        email: 'target@example.test',
        pass: passwordHash(password),
        linked: {},
        emailVerified: true,
      },
      {
        id: 4,
        nick: 'GoneUser',
        email: 'replacement@example.test',
        pass: passwordHash(password),
        linked: {},
        emailVerified: true,
      },
    ],
  });
  fs.writeFileSync(path.join(dataDir, 'auth-secret'), 'a'.repeat(64));
  writeJson('chat.json', {
    seq: 14,
    messages: [
      { id: 11, userId: 2, nick: 'Offender', text: 'chat evidence', at: 100 },
      { id: 14, userId: 3, nick: 'TargetUser', text: 'target evidence', at: 103 },
    ],
  });
  writeJson('comments.json', {
    '42': [{ id: 12, userId: 2, nick: 'Offender', text: 'comment evidence', at: 101 }],
  });
  writeJson('dms.json', {
    seq: 13,
    threads: {
      '1:2': [{ id: 13, from: 'Offender', to: 'Moderator', text: 'dm evidence', at: 102 }],
    },
    lastRead: {},
  });
  writeJson('reports.json', {
    seq: 14,
    items: [
      {
        id: 1, reporterId: 1, reporterNick: 'Moderator', type: 'chat',
        targetId: '11', targetNick: 'Offender', reason: 'chat report',
        snapshot: { id: 11, userId: 2, nick: 'Offender', text: 'chat evidence', at: 100 },
        status: 'open', createdAt: Date.now() - 6000,
      },
      {
        id: 2, reporterId: 1, reporterNick: 'Moderator', type: 'comment',
        targetId: '12', targetNick: 'Offender', animeId: '42', reason: 'comment report',
        snapshot: { id: 12, userId: 2, nick: 'Offender', text: 'comment evidence', at: 101 },
        status: 'open', createdAt: Date.now() - 5000,
      },
      {
        id: 3, reporterId: 1, reporterNick: 'Moderator', type: 'dm',
        targetId: '13', targetNick: 'TargetUser', targetUserId: 2, reason: 'dm report',
        snapshot: { id: 13, userId: 2, from: 'Offender', to: 'Moderator', text: 'dm evidence', at: 102 },
        status: 'open', createdAt: Date.now() - 4000,
      },
      {
        id: 4, reporterId: 1, reporterNick: 'Moderator', type: 'profile', targetUserId: 2,
        targetId: 'Offender', targetNick: 'TargetUser', reason: 'profile report',
        snapshot: { userId: 2, nick: 'Offender' }, status: 'open', createdAt: Date.now() - 3000,
      },
      {
        id: 5, reporterId: 1, reporterNick: 'Moderator', type: 'comment',
        targetId: '12', targetNick: 'Offender', animeId: '42', reason: 'comment removal',
        snapshot: { id: 12, userId: 2, nick: 'Offender', text: 'comment evidence', at: 101 },
        status: 'open', createdAt: Date.now() - 2000,
      },
      {
        id: 6, reporterId: 1, reporterNick: 'Moderator', type: 'profile', targetUserId: 98,
        targetId: 'GoneUser', targetNick: 'GoneUser', reason: 'deleted original account',
        snapshot: { userId: 98, nick: 'GoneUser' }, status: 'open', createdAt: Date.now() - 1000,
      },
      {
        id: 7, reporterId: 1, reporterNick: 'Moderator', type: 'chat',
        targetId: '999', targetNick: 'Expired', reason: 'expired retention record',
        snapshot: { id: 999, userId: 99, nick: 'Expired', text: 'expired evidence' },
        status: 'resolved', createdAt: Date.now() - (3 * 365 + 1) * 24 * 60 * 60 * 1000,
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
    gateway.stdout.on('data', (chunk) => {
      if (String(chunk).includes('AniPulse gateway')) {
        clearTimeout(timeout);
        resolve();
      }
    });
    gateway.stderr.on('data', (chunk) => {
      const message = String(chunk);
      if (message.trim()) reject(new Error(message));
    });
  });

  const login = await api('/alapi/auth/login', {
    method: 'POST',
    token: null,
    body: { login: 'Moderator', password },
  });
  assert.equal(login.status, 200);
  adminToken = login.json.token;
});

after(() => {
  if (gateway && !gateway.killed) gateway.kill();
  fs.rmSync(dataDir, { recursive: true, force: true });
});

test('admin sees the immutable evidence snapshot', async () => {
  const response = await api('/alapi/admin/reports');
  assert.equal(response.status, 200);
  assert.equal(response.json.length, 6);
  const dm = response.json.find((report) => report.type === 'dm');
  assert.deepEqual(
    { from: dm.snapshot.from, to: dm.snapshot.to, text: dm.snapshot.text },
    { from: 'Offender', to: 'Moderator', text: 'dm evidence' },
  );
  const stored = JSON.parse(fs.readFileSync(path.join(dataDir, 'reports.json'))).items;
  assert.equal(stored.some(report => report.id === 7), false);
});

test('a reporter cannot forge the target user who will be banned', async () => {
  const login = await api('/alapi/auth/login', {
    method: 'POST',
    token: null,
    body: { login: 'Offender', password },
  });
  assert.equal(login.status, 200);
  const submitted = await api('/alapi/reports', {
    method: 'POST',
    token: login.json.token,
    body: {
      type: 'chat',
      targetId: '14',
      targetNick: 'Moderator',
      reason: 'forged target test',
    },
  });
  assert.equal(submitted.status, 200);
  const reports = await api('/alapi/admin/reports');
  const created = reports.json.find((report) => report.id === submitted.json.reportId);
  assert.equal(created.targetNick, 'TargetUser');
  assert.equal(created.snapshot.nick, 'TargetUser');
});

test('remove deletes the exact chat message and closes the report', async () => {
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 1, action: 'remove', resolution: 'removed by test' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.removed, 1);
  const messages = JSON.parse(fs.readFileSync(path.join(dataDir, 'chat.json'))).messages;
  assert.equal(messages.some((message) => message.id === 11), false);
  assert.equal(messages.some((message) => message.id === 14), true);
  const report = JSON.parse(fs.readFileSync(path.join(dataDir, 'reports.json'))).items
    .find((item) => item.id === 1);
  assert.equal(report.status, 'resolved');
  assert.equal(report.snapshot.text, 'chat evidence');
  assert.equal(report.action.name, 'remove');
});

test('reject preserves reported content', async () => {
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 2, action: 'reject', resolution: 'not confirmed' },
  });
  assert.equal(response.status, 200);
  assert.equal(JSON.parse(fs.readFileSync(path.join(dataDir, 'comments.json')))['42'].length, 1);
  const report = JSON.parse(fs.readFileSync(path.join(dataDir, 'reports.json'))).items
    .find((item) => item.id === 2);
  assert.equal(report.status, 'rejected');
});

test('remove and ban deletes a DM and applies a 24 hour ban', async () => {
  const before = Date.now();
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 3, action: 'remove_ban_24h', resolution: 'confirmed harassment' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.removed, 1);
  assert.ok(response.json.bannedUntil >= before + 23 * 3600 * 1000);
  const dms = JSON.parse(fs.readFileSync(path.join(dataDir, 'dms.json')));
  assert.equal(dms.threads['1:2'].length, 0);
  const offender = JSON.parse(fs.readFileSync(path.join(dataDir, 'users.json'))).users
    .find((user) => user.nick === 'Offender');
  assert.equal(offender.bannedUntil, response.json.bannedUntil);
  const target = JSON.parse(fs.readFileSync(path.join(dataDir, 'users.json'))).users
    .find((user) => user.nick === 'TargetUser');
  assert.equal(target.bannedUntil, undefined);
});

test('profile removal clears public profile content', async () => {
  const avatarPath = path.join(dataDir, 'avatars', '2.img');
  fs.writeFileSync(avatarPath, 'reported avatar');
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 4, action: 'remove', resolution: 'unsafe profile removed' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.removed, 1);
  const offender = JSON.parse(fs.readFileSync(path.join(dataDir, 'users.json'))).users
    .find((user) => user.nick === 'Offender');
  assert.equal(offender.bio, '');
  assert.equal(offender.favoriteGenre, '');
  assert.equal(offender.customAvatar, false);
  assert.equal(offender.avatar, 0);
  assert.equal(fs.existsSync(avatarPath), false);
});

test('remove deletes the exact reported comment', async () => {
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 5, action: 'remove', resolution: 'comment removed' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.removed, 1);
  assert.equal(JSON.parse(fs.readFileSync(path.join(dataDir, 'comments.json')))['42'].length, 0);
});

test('repeating the same closed action is idempotent', async () => {
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 1, action: 'remove', resolution: 'duplicate action' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.json.repeated, true);
});

test('a closed report rejects a different second action', async () => {
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 1, action: 'ban_24h', resolution: 'different action' },
  });
  assert.equal(response.status, 409);
});

test('a reused nick is never treated as the deleted reported account', async () => {
  const response = await api('/alapi/admin/reports/action', {
    method: 'POST',
    body: { id: 6, action: 'ban_24h', resolution: 'must not ban replacement account' },
  });
  assert.equal(response.status, 400);
  const replacement = JSON.parse(fs.readFileSync(path.join(dataDir, 'users.json'))).users
    .find((user) => user.id === 4);
  assert.equal(replacement.nick, 'GoneUser');
  assert.equal(replacement.bannedUntil, undefined);
});
