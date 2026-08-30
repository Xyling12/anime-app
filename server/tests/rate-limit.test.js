'use strict';

// Регрессия на обход rate-limit переполнением карты счётчиков.
// Раньше tooMany() при map.size > 5000 делал map.clear(): ботнету достаточно было
// засорить карту адресами, чтобы разом обнулить счётчики попыток входа для всех
// и продолжить перебор паролей. Проверяем, что блокировка переживает флуд.

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { after, before, test } = require('node:test');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..', '..');
const data = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-ratelimit-'));
let gateway, baseUrl;

function hash(value) {
  const salt = 'rate-limit-test-salt';
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

// Заливаем карту счётчиков через дешёвый эндпоинт: он тоже пишет в ipHits, но не
// считает scrypt. Через /auth/login флуд занял бы ~3 минуты, и минутное окно
// блокировки истекло бы само — тест перестал бы проверять то, ради чего написан.
async function heartbeat(ip) {
  const response = await fetch(baseUrl + '/alapi/analytics/heartbeat', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-forwarded-for': ip },
    body: '{}',
  });
  return response.status;
}

// Без реального прокси шлюз берёт последний элемент X-Forwarded-For — это и есть
// «адрес клиента», поэтому в тесте им можно управлять напрямую.
async function login(ip, guess = 'nope') {
  const response = await fetch(baseUrl + '/alapi/auth/login', {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-forwarded-for': ip },
    body: JSON.stringify({ login: 'Victim', password: guess }),
  });
  return response.status;
}

before(async () => {
  fs.writeFileSync(path.join(data, 'users.json'), JSON.stringify({
    seq: 1,
    users: [{ id: 1, nick: 'Victim', email: 'victim@example.test', pass: hash('Correct-Pass-2026'), emailVerified: true }],
  }));
  fs.writeFileSync(path.join(data, 'auth-secret'), 'e'.repeat(64));
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

test('a flood of fresh addresses does not reset an existing login block', async () => {
  const attacker = '203.0.113.77';

  // Пять неудачных попыток исчерпывают минутный лимит для этого адреса.
  for (let i = 0; i < 5; i++) assert.equal(await login(attacker), 401);
  assert.equal(await login(attacker), 429, 'адрес должен быть заблокирован после 5 попыток');

  // Переполняем карту счётчиков уникальными адресами — это и был вектор обхода.
  // Пачками, чтобы уложиться заведомо быстрее минутного окна блокировки.
  const FLOOD = 6000;
  for (let start = 0; start < FLOOD; start += 200) {
    await Promise.all(
      Array.from({ length: 200 }, (_, k) => heartbeat(`198.51.100.${(start + k) % 254}:${start + k}`)),
    );
  }

  // Ключевая проверка: блокировка обязана пережить флуд.
  assert.equal(await login(attacker), 429, 'флуд не должен снимать блокировку');

  // И правильный пароль тоже не проходит, пока адрес заблокирован (fail-closed).
  assert.equal(await login(attacker, 'Correct-Pass-2026'), 429);
});
