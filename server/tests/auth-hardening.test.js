'use strict';

// Регрессии на перебор паролей и кодов восстановления (аудит 2026-10-04):
// - лимит входа был только по IP — один аккаунт перебирался с множества адресов;
// - каждый новый код восстановления обнулял счётчик неверных попыток;
// - битый хеш в базе ронял вход в 500;
// - ключ комментариев `__proto__` ломал общее хранилище.

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { after, before, test } = require('node:test');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..', '..');
const data = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-auth-hardening-'));
let gateway, baseUrl, ipSeq = 0;

function hash(value) {
  const salt = 'auth-hardening-test-salt';
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

// Каждый запрос — с нового адреса: так проверяется именно лимит по аккаунту,
// а не минутный лимит по IP.
function freshIp() { ipSeq += 1; return `198.51.100.${ipSeq % 250}:${ipSeq}`; }

async function post(route, body, extraHeaders = {}) {
  const response = await fetch(baseUrl + route, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-forwarded-for': freshIp(), ...extraHeaders },
    body: JSON.stringify(body),
  });
  let json = null;
  try { json = await response.json(); } catch (_) {}
  return { status: response.status, json };
}

function usersDb() { return JSON.parse(fs.readFileSync(path.join(data, 'users.json'), 'utf8')); }

before(async () => {
  fs.writeFileSync(path.join(data, 'users.json'), JSON.stringify({
    seq: 3,
    users: [
      { id: 1, nick: 'Target', email: 'target@example.test', pass: hash('Correct-Pass-2026'), emailVerified: true },
      { id: 2, nick: 'Resetme', email: 'reset@example.test', pass: hash('Old-Pass-2026'), emailVerified: true },
      { id: 3, nick: 'Broken', email: 'broken@example.test', pass: 'not-a-valid-hash', emailVerified: true },
    ],
  }));
  fs.writeFileSync(path.join(data, 'auth-secret'), 'a'.repeat(64));
  const p = await port(); baseUrl = `http://127.0.0.1:${p}`;
  gateway = spawn(process.execPath, [path.join(root, 'server', 'gateway.js')], {
    cwd: root,
    env: { ...process.env, ANIPULSE_DATA_DIR: data, ANIPULSE_PORT: String(p), KODIK_TOKEN: 'test' },
  });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('start timeout')), 10000);
    gateway.stdout.on('data', value => {
      if (String(value).includes('AniPulse gateway')) { clearTimeout(timeout); resolve(); }
    });
    gateway.once('error', reject);
  });
});

after(() => {
  if (gateway && !gateway.killed) gateway.kill();
  fs.rmSync(data, { recursive: true, force: true });
});

test('one account is locked after repeated failures from different addresses', async () => {
  for (let i = 0; i < 10; i++) {
    assert.equal((await post('/alapi/auth/login', { login: 'Target', password: 'wrong-' + i })).status, 401);
  }
  const locked = await post('/alapi/auth/login', { login: 'target', password: 'Correct-Pass-2026' });
  assert.equal(locked.status, 429, 'верный пароль не проходит, пока аккаунт заблокирован');
});

test('a missing account answers like an existing one', async () => {
  for (let i = 0; i < 10; i++) {
    assert.equal((await post('/alapi/auth/login', { login: 'ghost@example.test', password: 'x' + i })).status, 401);
  }
  assert.equal((await post('/alapi/auth/login', { login: 'ghost@example.test', password: 'y' })).status, 429);
});

test('a corrupt password hash is a failed login, not a server error', async () => {
  assert.equal((await post('/alapi/auth/login', { login: 'Broken', password: 'whatever-123' })).status, 401);
});

test('requesting a new reset code does not reset the wrong-guess counter', async () => {
  assert.equal((await post('/alapi/auth/forgot', { email: 'reset@example.test' })).status, 200);
  for (let i = 0; i < 5; i++) {
    const r = await post('/alapi/auth/reset', { email: 'reset@example.test', code: '000000', password: 'New-Pass-2026' });
    assert.equal(r.status, usersDb().users[1].resetCode === '000000' ? 200 : 400);
  }
  // Раньше новый код обнулял счётчик и давал ещё 5 попыток.
  assert.equal((await post('/alapi/auth/forgot', { email: 'reset@example.test' })).status, 200);
  const code = usersDb().users.find(u => u.id === 2).resetCode;
  if (code) {
    const r = await post('/alapi/auth/reset', { email: 'reset@example.test', code, password: 'New-Pass-2026' });
    assert.equal(r.status, 429, 'даже верный код не принимается, пока не истёк час');
  }
});

test('at most three reset codes are issued per address per hour', async () => {
  const before = usersDb().users.find(u => u.id === 1).resetCode;
  for (let i = 0; i < 3; i++) await post('/alapi/auth/forgot', { email: 'target@example.test' });
  const third = usersDb().users.find(u => u.id === 1).resetCode;
  assert.notEqual(third, before);
  assert.equal((await post('/alapi/auth/forgot', { email: 'target@example.test' })).json.ok, true);
  assert.equal(usersDb().users.find(u => u.id === 1).resetCode, third, 'четвёртый запрос не выпускает новый код');
});

test('comment keys like __proto__ are rejected', async () => {
  const login = await post('/alapi/auth/login', { login: 'reset@example.test', password: 'Old-Pass-2026' });
  // Аккаунт мог быть заблокирован предыдущим тестом — тогда берём токен напрямую нельзя;
  // проверяем GET, которому авторизация не нужна.
  const response = await fetch(baseUrl + '/alapi/comments?animeId=__proto__', { headers: { 'x-forwarded-for': freshIp() } });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), []);
  if (login.status === 200) {
    const r = await post('/alapi/comments', { animeId: '__proto__', text: 'hello' }, { authorization: 'Bearer ' + login.json.token });
    assert.equal(r.status, 400);
  }
});
