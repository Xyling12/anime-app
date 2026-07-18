#!/usr/bin/env node
// Мониторинг Kodik-механики (запускается кроном раз в час).
// Проверяет полный путь: kodik-find (маппинг) → kodik (извлечение m3u8) на эталонном
// тайтле («Атака титанов», shikimori 16498). При поломке шлёт письмо владельцу на
// адрес из bugreport.json (как баг-репорты); письма не чаще раза в 6 часов, повторное
// «всё починилось» — одно при восстановлении. Состояние в kodik-status.json.
const http = require('http');
const fs = require('fs');

const STATUS_FILE = '/opt/anipulse/kodik-status.json';
const SIX_H = 6 * 60 * 60 * 1000;

function get(path) {
  return new Promise((resolve, reject) => {
    const req = http.get({ host: '127.0.0.1', port: 8090, path, timeout: 30000 }, (res) => {
      let d = ''; res.on('data', c => d += c);
      res.on('end', () => resolve({ status: res.statusCode, body: d }));
    });
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
  });
}

async function mail(subject, text) {
  try {
    const nodemailer = require('/opt/anipulse/node_modules/nodemailer');
    const smtp = JSON.parse(fs.readFileSync('/opt/anipulse/smtp.json', 'utf8'));
    const to = (() => { try { return JSON.parse(fs.readFileSync('/opt/anipulse/bugreport.json', 'utf8')).to; } catch (e) { return smtp.user; } })() || smtp.user;
    const t = nodemailer.createTransport({ host: smtp.host || 'smtp.yandex.ru', port: 465, secure: true, auth: { user: smtp.user, pass: smtp.pass } });
    await t.sendMail({ from: `AniPulse Monitor <${smtp.user}>`, to, subject, text });
  } catch (e) { console.error('mail failed:', e.message); }
}

(async () => {
  let ok = false, detail = '';
  try {
    const find = await get('/alapi/kodik-find?shikimoriId=16498');
    const link = (JSON.parse(find.body) || {}).link;
    if (!link) throw new Error('kodik-find: нет ссылки (' + find.body.slice(0, 120) + ')');
    const ext = await get('/alapi/kodik?link=' + encodeURIComponent(link) + '&episode=1');
    const q = JSON.parse(ext.body);
    if (!q || !Object.keys(q).some(k => Number(k) > 0)) throw new Error('kodik: пустые качества (' + ext.body.slice(0, 120) + ')');
    ok = true;
  } catch (e) { detail = e.message; }

  let st = { ok: true, lastMailAt: 0 };
  try { st = JSON.parse(fs.readFileSync(STATUS_FILE, 'utf8')); } catch (e) {}
  const now = Date.now();

  if (!ok && (st.ok || now - (st.lastMailAt || 0) > SIX_H)) {
    await mail('⚠️ AniPulse: Kodik сломался', 'Проверка извлечения видео не прошла:\n' + detail + '\n\nВероятно, Kodik сменил механику (эндпоинт/шифр). Нужно чинить gateway.js.');
    st.lastMailAt = now;
  }
  if (ok && !st.ok) {
    await mail('✅ AniPulse: Kodik снова работает', 'Извлечение видео восстановилось.');
  }
  st.ok = ok;
  fs.writeFileSync(STATUS_FILE, JSON.stringify(st));
  console.log(ok ? 'kodik OK' : 'kodik FAIL: ' + detail);
})();
