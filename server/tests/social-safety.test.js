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
const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-social-safety-'));
const password = 'SafetyPass-2026';
let gateway;
let baseUrl;
const tokens = {};

function passwordHash(value) {
  const salt = 'social-safety-test-salt';
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

async function api(route, { method = 'GET', body, token } = {}) {
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
  const futureBan = Date.now() + 24 * 3600 * 1000;
  writeJson('users.json', {
    seq: 3,
    users: [
      {
        id: 1, nick: 'Alice', email: 'alice@example.test', pass: passwordHash(password),
        linked: {}, emailVerified: true, createdAt: 100, lastSeen: Date.now(),
        bio: 'Alice profile', favoriteGenre: 'Drama',
      },
      {
        id: 2, nick: 'Bob', email: 'bob@example.test', pass: passwordHash(password),
        linked: {}, emailVerified: true, createdAt: 101, lastSeen: Date.now(),
        bio: 'Bob profile',
      },
      {
        id: 3, nick: 'Banned', email: 'banned@example.test', pass: passwordHash(password),
        linked: {}, emailVerified: true, createdAt: 102, lastSeen: Date.now(),
        bio: 'hidden while banned', customAvatar: true, bannedUntil: futureBan,
      },
    ],
  });
  fs.writeFileSync(path.join(dataDir, 'auth-secret'), 'b'.repeat(64));
  writeJson('chat.json', {
    seq: 3,
    messages: [
      { id: 1, userId: 1, nick: 'Alice', text: 'alice chat', at: 100 },
      { id: 2, userId: 2, nick: 'Bob', text: 'bob secret reply source', at: 101 },
      {
        id: 3, userId: 3, nick: 'Banned', text: 'third-party reply', at: 102,
        replyTo: { id: 2, nick: 'Bob', text: 'bob secret reply source' },
      },
    ],
  });
  writeJson('comments.json', {
    '42': [
      { id: 11, userId: 1, nick: 'Alice', text: 'alice comment', at: 100 },
      { id: 12, userId: 2, nick: 'Bob', text: 'bob comment', at: 101 },
    ],
  });
  writeJson('friends.json', {});
  writeJson('blocks.json', {});
  writeJson('notifications.json', { seq: 0, byUser: {} });
  writeJson('dms.json', {
    seq: 1,
    threads: {
      '1:2': [{ id: 1, from: 'Alice', to: 'Bob', text: 'old DM evidence', at: 99 }],
    },
    lastRead: {},
  });
  writeJson('reports.json', { seq: 0, items: [] });
  writeJson('ratings.json', {});

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

  for (const nick of ['Alice', 'Bob', 'Banned']) {
    const login = await api('/alapi/auth/login', {
      method: 'POST',
      body: { login: nick, password },
    });
    assert.equal(login.status, 200);
    tokens[nick] = login.json.token;
  }
  fs.writeFileSync(path.join(dataDir, 'avatars', '3.img'), 'reported custom avatar');
});

after(() => {
  if (gateway && !gateway.killed) gateway.kill();
  fs.rmSync(dataDir, { recursive: true, force: true });
});

test('guest never receives exact presence', async () => {
  const card = await api('/alapi/user?nick=Alice');
  assert.equal(card.status, 200);
  assert.equal(card.json.online, false);
  assert.equal(card.json.lastSeen, null);
});

test('friends see only an online boolean, never exact lastSeen', async () => {
  const add = await api('/alapi/friends/add', {
    method: 'POST', token: tokens.Alice, body: { nick: 'Bob' },
  });
  assert.equal(add.status, 200);
  const accept = await api('/alapi/friends/accept', {
    method: 'POST', token: tokens.Bob, body: { nick: 'Alice' },
  });
  assert.equal(accept.status, 200);
  const card = await api('/alapi/user?nick=Bob', { token: tokens.Alice });
  assert.equal(card.status, 200);
  assert.equal(card.json.online, true);
  assert.equal(card.json.lastSeen, null);
  const dms = await api('/alapi/dm/list', { token: tokens.Alice });
  assert.equal(dms.json[0].withOnline, true);
});

test('blocking removes relationships and interaction notifications', async () => {
  const blocked = await api('/alapi/blocks', {
    method: 'POST', token: tokens.Alice, body: { nick: 'Bob', action: 'block' },
  });
  assert.deepEqual({ status: blocked.status, blocked: blocked.json.blocked }, { status: 200, blocked: true });

  for (const nick of ['Alice', 'Bob']) {
    const friends = await api('/alapi/friends', { token: tokens[nick] });
    assert.equal(friends.json.friends.length, 0);
    assert.equal(friends.json.incoming.length, 0);
    const notifications = await api('/alapi/notifications', { token: tokens[nick] });
    assert.equal(notifications.json.length, 0);
  }
});

test('a bilateral block prevents friendship and DMs from both sides', async () => {
  for (const [from, to] of [['Alice', 'Bob'], ['Bob', 'Alice']]) {
    const friend = await api('/alapi/friends/add', {
      method: 'POST', token: tokens[from], body: { nick: to },
    });
    assert.equal(friend.status, 403);
    const dm = await api('/alapi/dm', {
      method: 'POST', token: tokens[from], body: { to, text: 'must be blocked' },
    });
    assert.equal(dm.status, 403);
    const thread = await api(`/alapi/dm?with=${to}`, { token: tokens[from] });
    assert.equal(thread.status, 403);
  }
});

test('blocked users are hidden symmetrically from chat, comments, and profiles', async () => {
  const aliceChat = await api('/alapi/chat', { token: tokens.Alice });
  assert.equal(aliceChat.json.some(m => m.nick === 'Bob'), false);
  const nestedReply = aliceChat.json.find(m => m.id === 3).replyTo;
  assert.equal(nestedReply.text, 'Сообщение скрыто');
  assert.equal(nestedReply.nick, 'Скрыто');

  const bobChat = await api('/alapi/chat', { token: tokens.Bob });
  assert.equal(bobChat.json.some(m => m.nick === 'Alice'), false);
  const aliceComments = await api('/alapi/comments?animeId=42', { token: tokens.Alice });
  const bobComments = await api('/alapi/comments?animeId=42', { token: tokens.Bob });
  assert.equal(aliceComments.json.some(c => c.nick === 'Bob'), false);
  assert.equal(bobComments.json.some(c => c.nick === 'Alice'), false);

  const seenByAlice = await api('/alapi/user?nick=Bob', { token: tokens.Alice });
  const seenByBob = await api('/alapi/user?nick=Alice', { token: tokens.Bob });
  assert.equal(seenByAlice.json.blocked, true);
  assert.equal(seenByAlice.json.bio, '');
  assert.equal(seenByAlice.json.online, false);
  assert.equal(seenByBob.json.blockedByTarget, true);
  assert.equal(seenByBob.json.bio, '');
  assert.equal(seenByBob.json.lastSeen, null);
});

test('unblock restores interaction but never restores friendship automatically', async () => {
  const unblocked = await api('/alapi/blocks', {
    method: 'POST', token: tokens.Alice, body: { nick: 'Bob', action: 'unblock' },
  });
  assert.deepEqual({ status: unblocked.status, blocked: unblocked.json.blocked }, { status: 200, blocked: false });
  const friends = await api('/alapi/friends', { token: tokens.Alice });
  assert.equal(friends.json.friends.length, 0);
  const add = await api('/alapi/friends/add', {
    method: 'POST', token: tokens.Bob, body: { nick: 'Alice' },
  });
  assert.equal(add.status, 200);
  const dms = await api('/alapi/dm/list', { token: tokens.Alice });
  assert.equal(dms.json[0].withOnline, false);
});

test('a ban blocks publishing but preserves safety and cleanup actions', async () => {
  const cannotContactBanned = [
    ['/alapi/dm', { to: 'Banned', text: 'must not be delivered' }],
    ['/alapi/friends/add', { nick: 'Banned' }],
  ];
  for (const [route, body] of cannotContactBanned) {
    const response = await api(route, { method: 'POST', token: tokens.Alice, body });
    assert.equal(response.status, 403, route);
  }
  const mention = await api('/alapi/chat', {
    method: 'POST', token: tokens.Alice, body: { text: '@Banned must not notify' },
  });
  assert.equal(mention.status, 200);
  const bannedNotifications = await api('/alapi/notifications', { token: tokens.Banned });
  assert.equal(bannedNotifications.json.length, 0);

  const forbidden = [
    ['/alapi/profile', { bio: 'new public bio' }],
    ['/alapi/rating', { animeId: '42', score: 10 }],
    ['/alapi/avatar-upload', { image: 'invalid' }],
    ['/alapi/friends/add', { nick: 'Alice' }],
  ];
  for (const [route, body] of forbidden) {
    const response = await api(route, { method: 'POST', token: tokens.Banned, body });
    assert.equal(response.status, 403, route);
  }

  const preset = await api('/alapi/avatar', {
    method: 'POST', token: tokens.Banned, body: { avatar: 0 },
  });
  assert.equal(preset.status, 200);
  const block = await api('/alapi/blocks', {
    method: 'POST', token: tokens.Banned, body: { nick: 'Alice', action: 'block' },
  });
  assert.equal(block.status, 200);
  const report = await api('/alapi/reports', {
    method: 'POST',
    token: tokens.Banned,
    body: { type: 'chat', targetId: '1', reason: 'safety report while banned' },
  });
  assert.equal(report.status, 200);
  const avatar = await api('/alapi/avatar-img?nick=Banned');
  assert.equal(avatar.status, 404);
});
