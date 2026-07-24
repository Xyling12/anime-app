// AniPulse API-шлюз: обход блокировок РФ + ddos-guard + кэш + постеры + Kodik (маппинг по shikimori_id + извлечение HD).
const http = require('http');
const https = require('https');
const fs = require('fs');
const pathModule = require('path');
const { URL } = require('url');
const DATA_DIR = process.env.ANIPULSE_DATA_DIR || '/opt/anipulse';
const dataPath = (name) => pathModule.join(DATA_DIR, name);

const UPSTREAMS = {
  shikimori: 'https://shikimori.io',
  anilibria: 'https://anilibria.top',
  animego:   'https://animego.me',
  jikan:     'https://api.jikan.moe',
  malcdn:    'https://cdn.myanimelist.net',
  anime365:  'https://smotret-anime.online',
  aniskip:   'https://api.aniskip.com',
  anilistcdn:'https://s4.anilist.co',
};
const KODIK_API = 'https://kodik-api.com';
const KODIK_TOKEN = process.env.KODIK_TOKEN || (() => {
  try { return fs.readFileSync(dataPath('kodik-token'), 'utf8').trim(); } catch (_) { return ''; }
})();
const UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';
const cache = new Map();
const posterCache = new Map();
const TTL_MS = 60 * 1000;
const IMG_TTL_MS = 30 * 60 * 1000; // картинки в серверном кэше держим дольше текста
const POSTER_TTL_MS = 24 * 60 * 60 * 1000;

// Периодическая чистка кэшей: записи раньше только помечались просроченными,
// но не удалялись из Map — память росла к MemoryMax=150M юнита, под давлением
// часть запросов картинок начинала фейлиться (репорт «не все постеры грузятся»).
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of cache) { if (v.exp <= now) cache.delete(k); }
  for (const [k, v] of posterCache) { if (v.exp <= now) posterCache.delete(k); }
}, 5 * 60 * 1000).unref();

function fetchFollow(urlStr, { cookies = {}, redirects = 0, method = 'GET', body = null, headers = {}, maxBytes = 8 * 1024 * 1024 } = {}) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('too many redirects'));
    const u = new URL(urlStr);
    const cookieHeader = Object.entries(cookies).map(([k, v]) => `${k}=${v}`).join('; ');
    const h = { 'User-Agent': UA, 'Accept': 'application/json, text/html, image/*, */*', ...headers };
    if (cookieHeader) h['Cookie'] = cookieHeader;
    if (body) { h['Content-Type'] = 'application/x-www-form-urlencoded'; h['Content-Length'] = Buffer.byteLength(body); }
    const req = https.request(u, { method, headers: h }, (res) => {
      (res.headers['set-cookie'] || []).forEach((c) => {
        const [pair] = c.split(';'); const idx = pair.indexOf('=');
        if (idx > 0) cookies[pair.slice(0, idx).trim()] = pair.slice(idx + 1).trim();
      });
      if ([301,302,303,307,308].includes(res.statusCode) && res.headers.location) {
        res.resume(); return resolve(fetchFollow(new URL(res.headers.location, u).toString(), { cookies, redirects: redirects+1 }));
      }
      const ch = []; let received = 0;
      res.on('data', d => {
        received += d.length;
        if (received > maxBytes) return req.destroy(new Error('upstream response too large'));
        ch.push(d);
      });
      res.on('end', () => resolve({ status: res.statusCode, body: Buffer.concat(ch), ctype: res.headers['content-type'] || 'application/json' }));
    });
    req.on('error', reject);
    req.setTimeout(25000, () => req.destroy(new Error('timeout')));
    if (body) req.write(body);
    req.end();
  });
}

// Расшифровка src Kodik: Caesar-сдвиг (авто-подбор 1..25) + base64.
function kodikDecode(src) {
  for (let s = 1; s <= 25; s++) {
    let rot = '';
    for (const c of src) {
      const code = c.charCodeAt(0);
      if (code >= 97 && code <= 122) rot += String.fromCharCode((code-97+s)%26+97);
      else if (code >= 65 && code <= 90) rot += String.fromCharCode((code-65+s)%26+65);
      else rot += c;
    }
    try { const d = Buffer.from(rot, 'base64').toString('utf8'); if (d.includes('//') || d.startsWith('http')) return d; } catch (e) {}
  }
  return null;
}

// /alapi/kodik-find?shikimoriId=X -> {link, translation, quality} (маппинг по shikimori_id)
async function handleKodikFind(id, res) {
  try {
    const r = await fetchFollow(`${KODIK_API}/get-player?shikimoriID=${id}&token=${KODIK_TOKEN}&title=x`);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(r.body.toString());
  } catch (e) { res.writeHead(502); res.end('kodik-find error: ' + e.message); }
}


// /alapi/kodik-dubs?shikimoriId=X -> [{title,type,link}] — ВСЕ озвучки Kodik (бесплатно).
async function handleKodikDubs(id, res){
  try{
    const r=await fetchFollow(`${KODIK_API}/get-player?shikimoriID=${id}&token=${KODIK_TOKEN}&title=x`);
    const j=JSON.parse(r.body.toString());
    if(!j.found||!j.link){ res.writeHead(200,{'Content-Type':'application/json'}); return res.end('[]'); }
    const pageUrl=(j.link.startsWith('//')?'https:'+j.link:j.link);
    const page=(await fetchFollow(pageUrl)).body.toString();
    const opts=[...page.matchAll(/<option\b[^>]*?data-media-id="(\d+)"[^>]*?data-media-hash="([0-9a-f]+)"[^>]*?data-media-type="serial"[^>]*?data-title="([^"]*)"[^>]*?>/g)];
    const typeMatch=(v)=>{ const m=page.match(new RegExp('data-id="'+v+'"[^>]*data-translation-type="([a-z]+)"')); return m?m[1]:'voice'; };
    const seen=new Set(); const out=[];
    for(const o of opts){
      const [_,mid,mhash,title]=o;
      const key=mid+':'+mhash; if(seen.has(key))continue; seen.add(key);
      const vm=o[0].match(/value="(\d+)"/);
      out.push({title:title||'Kodik', type: vm?typeMatch(vm[1]):'voice', link:`//kodikplayer.com/serial/${mid}/${mhash}/720p`});
    }
    // если опций нет (одиночный перевод) — вернём дефолт
    if(out.length===0) out.push({title:j.translation||'Kodik',type:'voice',link:j.link});
    res.writeHead(200,{'Content-Type':'application/json'}); res.end(JSON.stringify(out));
  }catch(e){ res.writeHead(502); res.end('kodik-dubs error: '+e.message); }
}

// /alapi/kodik?link=<kodikplayer url>&episode=N -> {"480":"m3u8", "720":..}
// link приходит от клиента (query-параметр) — без белого списка это открытый SSRF
// (сервер сходит по любому https-хосту от имени VPS). Разрешаем только сам Kodik.
function isAllowedKodikHost(host) {
  return /^([a-z0-9-]+\.)*kodikplayer\.com$/i.test(host);
}
async function handleKodik(link, episode, res) {
  try {
    let pageUrl = link.startsWith('//') ? 'https:' + link : link;
    const host0 = (pageUrl.match(/^https?:\/\/([^/]+)/) || [])[1] || '';
    if (!isAllowedKodikHost(host0)) { res.writeHead(400); return res.end('kodik error: недопустимый хост'); }
    if (episode) {
      const sep = pageUrl.includes('?') ? '&' : '?';
      pageUrl += `${sep}season=1&episode=${episode}`;
    }
    const host = (pageUrl.match(/https?:\/\/([^/]+)\//) || [])[1] || 'kodikplayer.com';
    const page = (await fetchFollow(pageUrl)).body.toString();
    const vt = page.match(/vInfo\.type\s*=\s*'([^']+)'/);
    const vh = page.match(/vInfo\.hash\s*=\s*'([^']+)'/);
    const vi = page.match(/vInfo\.id\s*=\s*'([^']+)'/);
    const pm = page.match(/urlParams\s*=\s*'([^']+)'/);
    if (!vt || !vh || !vi || !pm) { res.writeHead(502); return res.end('no vInfo'); }
    const params = JSON.parse(pm[1]);
    const form = new URLSearchParams({ type: vt[1], hash: vh[1], id: vi[1], bad_user: 'true', info: '{}' });
    for (const k of ['d','d_sign','pd','pd_sign','ref','ref_sign']) if (params[k] != null) form.append(k, params[k]);
    const ftor = (await fetchFollow('https://' + host + '/ftor', {
      method: 'POST', body: form.toString(),
      headers: { 'Referer': pageUrl, 'X-Requested-With': 'XMLHttpRequest' },
    })).body.toString();
    const links = JSON.parse(ftor).links || {};
    const out = {};
    for (const q of Object.keys(links)) {
      const src = links[q][0] && links[q][0].src; if (!src) continue;
      let url = src.startsWith('//') ? 'https:'+src : (src.startsWith('http') ? src : kodikDecode(src));
      if (url) { if (url.startsWith('//')) url = 'https:'+url; out[q] = url; }
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(out));
  } catch (e) { res.writeHead(502); res.end('kodik error: ' + e.message); }
}

// Jikan лимитирует ~3 запроса/сек: экран «Эфир» стреляет 20 постерами разом,
// без очереди большинство получало 429 → пустые карточки. Очередь с зазором 400мс
// + короткий негативный кэш (не долбим Jikan по тайтлам без постера).
function anilistCover(id) {
  return new Promise((resolve) => {
    const body = JSON.stringify({ query: 'query($m:Int){Media(idMal:$m,type:ANIME){coverImage{large}}}', variables: { m: Number(id) } });
    const req = https.request('https://graphql.anilist.co', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Accept': 'application/json', 'User-Agent': UA, 'Content-Length': Buffer.byteLength(body) } }, (r) => {
      const ch = []; r.on('data', d => ch.push(d));
      r.on('end', () => { try { resolve(JSON.parse(Buffer.concat(ch).toString()).data.Media.coverImage.large || null); } catch (e) { resolve(null); } });
    });
    req.on('error', () => resolve(null));
    req.setTimeout(15000, () => { req.destroy(); resolve(null); });
    req.end(body);
  });
}
let jikanChain = Promise.resolve();
function handlePoster(id, res) {
  const hit = posterCache.get(id);
  if (hit && hit.exp > Date.now()) {
    if (!hit.path) { res.writeHead(404); return res.end('no poster'); }
    res.writeHead(302, { Location: hit.path }); return res.end();
  }
  jikanChain = jikanChain.then(async () => {
    try {
      const h2 = posterCache.get(id); // мог появиться, пока ждали очередь
      if (h2 && h2.exp > Date.now()) {
        if (!h2.path) { res.writeHead(404); return res.end('no poster'); }
        res.writeHead(302, { Location: h2.path }); return res.end();
      }
      let path = null;
      try {
        const r = await fetchFollow(`${UPSTREAMS.jikan}/v4/anime/${id}`);
        const img = JSON.parse(r.body.toString())?.data?.images?.jpg?.large_image_url || null;
        if (img) path = '/alapi/malcdn' + new URL(img).pathname;
      } catch (e) {}
      // Jikan/MAL нестабилен (массовые 504) — второй источник: AniList по тому же MAL id.
      if (!path) {
        const al = await anilistCover(id);
        if (al) path = '/alapi/anilistcdn' + new URL(al).pathname;
      }
      if (!path) {
        posterCache.set(id, { path: null, exp: Date.now() + 10 * 60 * 1000 });
        res.writeHead(404); return res.end('no poster');
      }
      posterCache.set(id, { path, exp: Date.now() + POSTER_TTL_MS });
      res.writeHead(302, { Location: path }); res.end();
    } catch (e) { try { res.writeHead(502); res.end('poster error: ' + e.message); } catch (_) {} }
    await new Promise(r => setTimeout(r, 400)); // зазор под лимит Jikan
  });
}

// OTA-обновления: манифест версии + сам APK (кладётся в /opt/anipulse при релизе).
function handleAppVersion(res) {
  try { return jsonRes(res, 200, JSON.parse(fs.readFileSync(dataPath('app-version.json'), 'utf8'))); }
  catch (e) { return jsonRes(res, 200, { versionCode: 0 }); }
}
function handleApkDownload(res) {
  try {
    const b = fs.readFileSync(dataPath('AniPulse-latest.apk'));
    res.writeHead(200, { 'Content-Type': 'application/vnd.android.package-archive', 'Content-Disposition': 'attachment; filename="AniPulse.apk"', 'Content-Length': b.length });
    res.end(b);
  } catch (e) { res.writeHead(404); res.end('no apk'); }
}


// /alapi/anilibria-updates -> свежие серии AniLibria: [{title,titleEn,episode,freshAt,poster}]
let updatesCache = { exp: 0, body: null };
async function handleAnilibriaUpdates(res) {
  try {
    if (updatesCache.body && updatesCache.exp > Date.now()) {
      res.writeHead(200, { 'Content-Type': 'application/json', 'X-Cache': 'HIT' });
      return res.end(updatesCache.body);
    }
    const r = await fetchFollow(UPSTREAMS.anilibria + '/api/v1/anime/releases/latest?limit=30');
    const list = JSON.parse(r.body.toString());
    const out = list.map(x => ({
      title: (x.name && x.name.main) || '',
      titleEn: (x.name && x.name.english) || null,
      episode: (x.latest_episode && x.latest_episode.ordinal) || null,
      episodesTotal: x.episodes_total || null,
      freshAt: x.fresh_at || x.updated_at || null,
      poster: x.poster ? '/alapi/anilibria' + ((x.poster.optimized && x.poster.optimized.src) || x.poster.src) : null,
    }));
    const body = JSON.stringify(out);
    updatesCache = { exp: Date.now() + 10 * 60 * 1000, body };
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(body);
  } catch (e) { res.writeHead(502); res.end('anilibria-updates error: ' + e.message); }
}


// ===== Аккаунты AniPulse (регистрация ник/почта/пароль, токены HMAC) =====
const crypto = require('crypto');
const USERS_FILE = dataPath('users.json');
const SECRET_FILE = dataPath('auth-secret');
if (!fs.existsSync(SECRET_FILE)) fs.writeFileSync(SECRET_FILE, crypto.randomBytes(32).toString('hex'), { mode: 0o600 });
const AUTH_SECRET = fs.readFileSync(SECRET_FILE, 'utf8').trim();
// Rate limiting: попытки входа по IP, спам-лимиты по пользователю
const ipHits = new Map();   // ip -> [timestamps]
const userHits = new Map(); // key -> [timestamps]
function tooMany(map, key, limit, windowMs) {
  const now = Date.now();
  const arr = (map.get(key) || []).filter(t => now - t < windowMs);
  if (arr.length >= limit) { map.set(key, arr); return true; }
  arr.push(now); map.set(key, arr);
  if (map.size > 5000) map.clear(); // защита от разрастания памяти
  return false;
}
function clientIp(req) {
  // Caddy ДОПИСЫВАЕТ реальный IP в конец X-Forwarded-For, не удаляя то, что прислал клиент —
  // поэтому доверяем ПОСЛЕДНЕМУ элементу, а не первому (иначе клиент подделывает [0] и обходит rate-limit).
  const xff = (req.headers['x-forwarded-for'] || '').split(',').map(s => s.trim()).filter(Boolean);
  return xff[xff.length - 1] || req.socket.remoteAddress || 'unknown';
}
// Как и loadJson ниже: отсутствие файла — норма (дефолт), битый файл — throw,
// чтобы следующий saveUsers не затёр всю базу аккаунтов пустым дефолтом.
function loadUsers() {
  let raw;
  try { raw = fs.readFileSync(USERS_FILE, 'utf8'); } catch (e) { return { seq: 0, users: [] }; }
  try { return JSON.parse(raw); } catch (e) { throw new Error(`corrupt users store: ${e.message}`); }
}
function saveUsers(db) { fs.writeFileSync(USERS_FILE + '.tmp', JSON.stringify(db, null, 1)); fs.renameSync(USERS_FILE + '.tmp', USERS_FILE); }
function hashPassword(pw, salt) {
  salt = salt || crypto.randomBytes(16).toString('hex');
  return salt + ':' + crypto.scryptSync(pw, salt, 32).toString('hex');
}
function checkPassword(pw, stored) {
  const [salt] = stored.split(':');
  return crypto.timingSafeEqual(Buffer.from(hashPassword(pw, salt)), Buffer.from(stored));
}
function makeToken(userId) {
  const db = loadUsers();
  const u = db.users.find(x => x.id === userId);
  const tv = (u && u.tv) || 0;
  const exp = Date.now() + 180 * 24 * 3600 * 1000;
  const payload = userId + '.' + exp;
  const sig = crypto.createHmac('sha256', AUTH_SECRET).update(payload + '.' + tv).digest('hex');
  return payload + '.' + sig;
}
function verifyToken(token) {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  if (Number(parts[1]) < Date.now()) return null;
  const userId = Number(parts[0]);
  const u = loadUsers().users.find(x => x.id === userId);
  if (!u) return null;
  const tv = u.tv || 0;
  const sig = crypto.createHmac('sha256', AUTH_SECRET).update(parts[0] + '.' + parts[1] + '.' + tv).digest('hex');
  const a = Buffer.from(sig), b = Buffer.from(parts[2]);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  return userId;
}
const MAX_BODY = 64 * 1024; // защита от гигантских тел
function readBody(req, maxBytes = MAX_BODY) {
  return new Promise((resolve) => {
    const ch = []; let size = 0;
    req.on('data', d => { size += d.length; if (size > maxBytes) { req.destroy(); resolve(null); return; } ch.push(d); });
    req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(ch).toString())); } catch (e) { resolve(null); } });
  });
}
function jsonRes(res, code, obj) { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj)); }
function authUserEarly(req) {
  const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
  if (!userId) return null;
  return loadUsers().users.find(u => u.id === userId) || null;
}
const SMTP_FILE = dataPath('smtp.json');
let _mailer = null;
function sendMail(to, subject, text) {
  try {
    if (!_mailer) {
      const cfg = JSON.parse(fs.readFileSync(SMTP_FILE, 'utf8'));
      _mailer = require('nodemailer').createTransport({ host: 'smtp.yandex.ru', port: 465, secure: true, auth: { user: cfg.user, pass: cfg.pass } });
      _mailer._from = cfg.user;
    }
    _mailer.sendMail({ from: 'AniPulse <' + _mailer._from + '>', to, subject, text }, () => {});
  } catch (e) {}
}
const BUGREPORT_FILE = dataPath('bugreport.json');
function bugReportTo() {
  try { return JSON.parse(fs.readFileSync(BUGREPORT_FILE, 'utf8')).to; } catch (e) {}
  try { return JSON.parse(fs.readFileSync(SMTP_FILE, 'utf8')).user; } catch (e) { return null; }
}
async function handleBugReport(req, res) {
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  if (tooMany(ipHits, 'bug:' + clientIp(req), 3, 60 * 60 * 1000)) return jsonRes(res, 429, { error: 'Слишком много репортов, попробуйте позже' });
  const to = bugReportTo();
  if (!to) return jsonRes(res, 503, { error: 'Отправка отчётов временно недоступна' });
  const b = await readBody(req);
  const text = sanitizeText(b && b.text, 2000);
  if (!text) return jsonRes(res, 400, { error: 'Опишите проблему' });
  const contact = sanitizeText(b && b.contact, 120);
  const device = sanitizeText(b && b.device, 80);
  const osVersion = sanitizeText(b && b.osVersion, 40);
  const user = authUserEarly(req);
  const lines = [
    user ? 'Аккаунт: ' + user.nick + ' (' + user.email + ')' : 'Аккаунт: гость',
    contact ? 'Контакт для ответа: ' + contact : null,
    device ? 'Устройство: ' + device : null,
    osVersion ? 'Android: ' + osVersion : null,
    '',
    text,
  ].filter(Boolean);
  sendMail(to, 'Баг-репорт AniPulse', lines.join('\n'));
  return jsonRes(res, 200, { ok: true });
}
function newVerifyCode(u) {
  u.verifyCode = String(crypto.randomInt(100000, 1000000));
  u.verifyExp = Date.now() + 15 * 60 * 1000;
  sendMail(u.email, 'Код подтверждения AniPulse', 'Ваш код: ' + u.verifyCode + '\n\nКод действует 15 минут.');
}

async function handleAuth(req, res, path) {
  if (path === 'exchange' && req.method === 'POST') {
    if (tooMany(ipHits, 'oauth-exchange:' + clientIp(req), 10, 60 * 1000)) return jsonRes(res, 429, { error: 'rate limit' });
    const b = await readBody(req);
    const grant = b && consumeOAuthCode(String(b.code || ''), 'login');
    const user = grant && loadUsers().users.find(u => u.id === grant.userId);
    if (!user) return jsonRes(res, 400, { error: 'Код использован или устарел' });
    return jsonRes(res, 200, { token: makeToken(user.id), nick: user.nick, email: user.email });
  }
  if (path === 'link-code' && req.method === 'POST') {
    const user = authUserEarly(req);
    if (!user) return jsonRes(res, 401, { error: 'auth' });
    if (tooMany(userHits, 'oauth-link:' + user.id, 5, 10 * 60 * 1000)) return jsonRes(res, 429, { error: 'rate limit' });
    return jsonRes(res, 200, { code: createOAuthCode('link', { userId: user.id }, 5 * 60 * 1000) });
  }
  if ((path === 'register' || path === 'login') && req.method === 'POST') {
    if (tooMany(ipHits, 'auth:' + clientIp(req), 5, 60 * 1000)) {
      return jsonRes(res, 429, { error: 'Слишком много попыток, подождите минуту' });
    }
  }
  if (path === 'logoutall' && req.method === 'POST') {
    const user = authUserEarly(req);
    if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
    const db = loadUsers();
    const u = db.users.find(x => x.id === user.id);
    u.tv = (u.tv || 0) + 1; saveUsers(db);
    return jsonRes(res, 200, { ok: true });
  }
  if (path === 'delete-account' && req.method === 'POST') {
    const user = authUserEarly(req);
    if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
    const b = await readBody(req);
    if (!b || b.confirm !== 'DELETE') return jsonRes(res, 400, { error: 'Подтвердите удаление' });
    deleteAccountData(user);
    return jsonRes(res, 200, { ok: true });
  }
  if (path === 'register' && req.method === 'POST') {
    const b = await readBody(req);
    if (!b || !b.nick || !b.email || !b.password) return jsonRes(res, 400, { error: 'Заполните все поля' });
    if (b.acceptTerms !== true || b.privacyConsent !== true) {
      return jsonRes(res, 400, { error: 'Примите условия и дайте отдельное согласие на обработку данных' });
    }
    const nick = String(b.nick).trim(), email = String(b.email).trim().toLowerCase();
    if (nick.length < 3 || nick.length > 24) return jsonRes(res, 400, { error: 'Ник: 3-24 символа' });
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return jsonRes(res, 400, { error: 'Некорректная почта' });
    if (String(b.password).length < 8 || String(b.password).length > 128) return jsonRes(res, 400, { error: 'Пароль: 8–128 символов' });
    const db = loadUsers();
    if (db.users.some(u => u.nick.toLowerCase() === nick.toLowerCase())) return jsonRes(res, 409, { error: 'Ник занят' });
    if (db.users.some(u => u.email === email)) return jsonRes(res, 409, { error: 'Почта уже зарегистрирована' });
    const now = Date.now();
    const user = {
      id: ++db.seq, nick, email, pass: hashPassword(String(b.password)), linked: {}, createdAt: now,
      termsAcceptedAt: now, termsVersion: '2026-07-22',
      privacyConsentAt: now, privacyConsentVersion: '2026-07-22',
    };
    user.emailVerified = false; newVerifyCode(user);
    db.users.push(user); saveUsers(db);
    return jsonRes(res, 200, { token: makeToken(user.id), nick: user.nick, email: user.email });
  }
  if (path === 'login' && req.method === 'POST') {
    const b = await readBody(req);
    if (!b || !b.login || !b.password) return jsonRes(res, 400, { error: 'Заполните все поля' });
    const login = String(b.login).trim().toLowerCase();
    const db = loadUsers();
    const user = db.users.find(u => u.email === login || u.nick.toLowerCase() === login);
    if (!user || !checkPassword(String(b.password), user.pass)) return jsonRes(res, 401, { error: 'Неверный логин или пароль' });
    // Новая версия клиента просит отдельные отметки и обновляет согласия при каждом осознанном входе.
    // Старые beta-сборки временно не блокируем, чтобы OTA-миграция не заперла уже созданные аккаунты.
    if (b.acceptTerms === true && b.privacyConsent === true) {
      const now = Date.now();
      user.termsAcceptedAt = now; user.termsVersion = '2026-07-22';
      user.privacyConsentAt = now; user.privacyConsentVersion = '2026-07-22';
      saveUsers(db);
    }
    return jsonRes(res, 200, { token: makeToken(user.id), nick: user.nick, email: user.email });
  }
  if (path === 'verify' && req.method === 'POST') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'auth' });
    const b = await readBody(req);
    const db = loadUsers(); const u = db.users.find(x => x.id === user.id);
    if (u.emailVerified !== false) return jsonRes(res, 200, { ok: true });
    if ((u.verifyAttempts || 0) >= 5) { delete u.verifyCode; delete u.verifyExp; saveUsers(db); return jsonRes(res, 429, { error: 'Слишком много попыток — запросите новый код' }); }
    if (!b || String(b.code) !== u.verifyCode || (u.verifyExp || 0) < Date.now()) {
      u.verifyAttempts = (u.verifyAttempts || 0) + 1; saveUsers(db);
      return jsonRes(res, 400, { error: 'Неверный или просроченный код' });
    }
    u.emailVerified = true; delete u.verifyCode; delete u.verifyExp; delete u.verifyAttempts; saveUsers(db);
    return jsonRes(res, 200, { ok: true });
  }
  if (path === 'resend' && req.method === 'POST') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'auth' });
    if (tooMany(userHits, 'vr:' + user.id, 1, 60 * 1000)) return jsonRes(res, 429, { error: 'Не чаще раза в минуту' });
    const db = loadUsers(); const u = db.users.find(x => x.id === user.id);
    if (u.emailVerified !== false) return jsonRes(res, 200, { ok: true });
    newVerifyCode(u); saveUsers(db);
    return jsonRes(res, 200, { ok: true });
  }
  if (path === 'forgot' && req.method === 'POST') {
    // Всегда отвечаем ok — не раскрываем, зарегистрирована ли почта.
    if (tooMany(ipHits, 'fg:' + clientIp(req), 3, 60 * 1000)) return jsonRes(res, 429, { error: 'Слишком часто, подождите минуту' });
    const b = await readBody(req);
    const email = String((b && b.email) || '').trim().toLowerCase();
    if (!email) return jsonRes(res, 400, { error: 'Укажите почту' });
    const db = loadUsers();
    const u = db.users.find(x => x.email === email);
    if (u) {
      u.resetCode = String(crypto.randomInt(100000, 1000000));
      u.resetExp = Date.now() + 15 * 60 * 1000;
      u.resetAttempts = 0;
      saveUsers(db);
      sendMail(u.email, 'Восстановление пароля AniPulse', 'Код для смены пароля: ' + u.resetCode + '\n\nКод действует 15 минут. Если это были не вы — просто проигнорируйте письмо.');
    }
    return jsonRes(res, 200, { ok: true });
  }
  if (path === 'reset' && req.method === 'POST') {
    if (tooMany(ipHits, 'rs:' + clientIp(req), 5, 60 * 1000)) return jsonRes(res, 429, { error: 'Слишком много попыток' });
    const b = await readBody(req);
    const email = String((b && b.email) || '').trim().toLowerCase();
    const code = String((b && b.code) || '');
    const password = String((b && b.password) || '');
    if (!email || !code) return jsonRes(res, 400, { error: 'Укажите почту и код' });
    if (password.length < 8 || password.length > 128) return jsonRes(res, 400, { error: 'Пароль: 8–128 символов' });
    const db = loadUsers();
    const u = db.users.find(x => x.email === email);
    // Лимит попыток на аккаунт — не даём подобрать 6-значный код перебором даже при обходе IP-лимита.
    if (u && (u.resetAttempts || 0) >= 5) { delete u.resetCode; delete u.resetExp; saveUsers(db); return jsonRes(res, 429, { error: 'Слишком много попыток — запросите код заново' }); }
    if (!u || u.resetCode !== code || (u.resetExp || 0) < Date.now()) {
      if (u) { u.resetAttempts = (u.resetAttempts || 0) + 1; saveUsers(db); }
      return jsonRes(res, 400, { error: 'Неверный или просроченный код' });
    }
    u.pass = hashPassword(password);
    delete u.resetCode; delete u.resetExp; delete u.resetAttempts;
    u.emailVerified = true; // владение почтой доказано кодом
    u.tv = (u.tv || 0) + 1; // отзыв всех старых токенов
    saveUsers(db);
    return jsonRes(res, 200, { token: makeToken(u.id), nick: u.nick, email: u.email });
  }
  if (path === 'me') {
    const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
    if (!userId) return jsonRes(res, 401, { error: 'Не авторизован' });
    const user = loadUsers().users.find(u => u.id === userId);
    if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
    return jsonRes(res, 200, { nick: user.nick, email: user.email, avatar: avatarOf(user), linked: Object.keys(user.linked || {}), emailVerified: user.emailVerified !== false, admin: !!user.admin });
  }
  jsonRes(res, 404, { error: 'not found' });
}


// ===== Соцчасть: общий чат, комментарии к тайтлам, свой рейтинг 1-10 =====
const CHAT_FILE = dataPath('chat.json');
const COMMENTS_FILE = dataPath('comments.json');
const RATINGS_FILE = dataPath('ratings.json');
const BLOCKS_FILE = dataPath('blocks.json');
const REPORTS_FILE = dataPath('reports.json');
// «Файла нет» → дефолт (норма при первом запуске). «Файл есть, но не парсится» → throw:
// иначе следующий saveJson молча затёр бы всё хранилище дефолтом (полная потеря чата/ЛС и т.п.).
// throw ловится общим обработчиком route() → клиент получит 500, данные останутся нетронуты.
function loadJson(f, def) {
  let raw;
  try { raw = fs.readFileSync(f, 'utf8'); } catch (e) { return def; }
  try { return JSON.parse(raw); } catch (e) { throw new Error(`corrupt json store ${f}: ${e.message}`); }
}
function saveJson(f, obj) { fs.writeFileSync(f + '.tmp', JSON.stringify(obj)); fs.renameSync(f + '.tmp', f); }
function authUser(req) {
  const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
  if (!userId) return null;
  const u = loadUsers().users.find(x => x.id === userId) || null;
  if (u) touchLastSeen(u);
  return u;
}
function sanitizeText(t, max) { return String(t || '').replace(/\s+/g, ' ').trim().slice(0, max); }
function blocksDb() { return loadJson(BLOCKS_FILE, {}); }
function blockedIds(db, userId) { return (db[String(userId)] || []).map(Number); }
function hasBlocked(db, userId, targetId) { return blockedIds(db, userId).includes(Number(targetId)); }
function blockedEither(db, a, b) { return hasBlocked(db, a, b) || hasBlocked(db, b, a); }
function userIdByNick(nick) {
  const u = loadUsers().users.find(x => x.nick && x.nick.toLowerCase() === String(nick || '').toLowerCase());
  return u ? u.id : null;
}

function deleteAccountData(user) {
  const db = loadUsers();
  db.users = db.users.filter(u => u.id !== user.id); saveUsers(db);

  const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
  chat.messages = chat.messages.filter(m => m.userId !== user.id); saveJson(CHAT_FILE, chat);

  const comments = loadJson(COMMENTS_FILE, {});
  for (const key of Object.keys(comments)) comments[key] = comments[key].filter(c => c.userId !== user.id);
  saveJson(COMMENTS_FILE, comments);

  const ratings = loadJson(RATINGS_FILE, {});
  for (const votes of Object.values(ratings)) delete votes[user.id];
  saveJson(RATINGS_FILE, ratings);

  const dms = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
  for (const key of Object.keys(dms.threads || {})) {
    if (key.split(':').map(Number).includes(user.id)) { delete dms.threads[key]; delete dms.lastRead[key]; }
  }
  saveJson(DM_FILE, dms);

  const friends = friendsDb(); delete friends[user.id];
  for (const value of Object.values(friends)) {
    value.friends = (value.friends || []).filter(id => id !== user.id);
    value.incoming = (value.incoming || []).filter(id => id !== user.id);
  }
  saveJson(FRIENDS_FILE, friends);

  const notifications = loadJson(NOTIF_FILE, { seq: 0, byUser: {} });
  delete notifications.byUser[user.id]; saveJson(NOTIF_FILE, notifications);

  const blocks = blocksDb(); delete blocks[user.id];
  for (const key of Object.keys(blocks)) blocks[key] = blockedIds(blocks, key).filter(id => id !== user.id);
  saveJson(BLOCKS_FILE, blocks);

  const reports = loadJson(REPORTS_FILE, { seq: 0, items: [] });
  for (const r of reports.items) {
    if (r.reporterId === user.id) { r.reporterId = null; r.reporterNick = 'Удалённый аккаунт'; }
    if (r.targetNick && r.targetNick.toLowerCase() === String(user.nick).toLowerCase()) r.targetNick = 'Удалённый аккаунт';
  }
  saveJson(REPORTS_FILE, reports);
  try { fs.unlinkSync(`${AVATARS_DIR}/${user.id}.img`); } catch (_) {}
}
// ===== Соцчасть v2: уведомления, @упоминания, ЛС =====
const NOTIF_FILE = dataPath('notifications.json');
const DM_FILE = dataPath('dms.json');

function addNotification(toUserId, notif) {
  const all = loadJson(NOTIF_FILE, { seq: 0, byUser: {} });
  notif.id = ++all.seq;
  notif.at = Date.now();
  const list = all.byUser[toUserId] || [];
  list.push(notif);
  all.byUser[toUserId] = list.slice(-100);
  saveJson(NOTIF_FILE, all);
}

// Разбирает @ники в тексте и шлёт уведомления существующим пользователям.
function notifyMentions(text, fromUser, source) {
  const nicks = [...new Set((text.match(/@[\w.-]{2,24}/g) || []).map(s => s.slice(1).toLowerCase()))];
  if (!nicks.length) return;
  const users = loadUsers().users;
  const blocks = blocksDb();
  for (const n of nicks) {
    const u = users.find(x => x.nick && x.nick.toLowerCase() === n);
    if (!u || u.id === fromUser.id || blockedEither(blocks, u.id, fromUser.id)) continue;
    addNotification(u.id, { type: 'mention', from: fromUser.nick, text: String(text).slice(0, 200), source });
  }
}

async function handleNotifications(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'auth' });
  const all = loadJson(NOTIF_FILE, { seq: 0, byUser: {} });
  const list = all.byUser[user.id] || [];
  if (req.method === 'POST' && req.url.startsWith('/alapi/notifications/read')) {
    list.forEach(n => { n.read = true; });
    all.byUser[user.id] = list;
    saveJson(NOTIF_FILE, all);
    return jsonRes(res, 200, { ok: true });
  }
  if (req.method === 'GET') {
    const after = Number((req.url.match(/[?&]after=(\d+)/) || [])[1] || 0);
    return jsonRes(res, 200, list.filter(n => n.id > after).slice(-50));
  }
  jsonRes(res, 405, { error: 'method' });
}

function dmKey(a, b) { return [a, b].sort((x, y) => x - y).join(':'); }

async function handleDm(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы писать ЛС' });
    if (user.emailVerified === false) return jsonRes(res, 403, { error: 'Подтвердите почту: Профиль → код из письма' });
  // ВАЖНО: в POST-ветке файл читается ЗАНОВО после await readBody — снапшот,
  // взятый до await, затирал бы параллельные записи (lost update + дубли id).
  if (req.method === 'GET' && req.url.startsWith('/alapi/dm/list')) {
    const all = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
    const users = loadUsers().users;
    const blocks = blocksDb();
    const out = [];
    for (const [key, msgs] of Object.entries(all.threads)) {
      const ids = key.split(':').map(Number);
      if (!ids.includes(user.id) || !msgs.length) continue;
      const otherId = ids[0] === user.id ? ids[1] : ids[0];
      if (blockedEither(blocks, user.id, otherId)) continue;
      const other = users.find(u => u.id === otherId);
      const last = msgs[msgs.length - 1];
      const lastRead = (all.lastRead[key] || {})[user.id] || 0;
      out.push({
        withNick: other ? other.nick : '?',
        withAvatar: other ? avatarOf(other) : 0,
        withOnline: other ? isOnline(other) : false,
        lastText: last.text, lastAt: last.at,
        unread: msgs.filter(m => m.id > lastRead && m.from !== user.nick).length,
      });
    }
    out.sort((a, b) => b.lastAt - a.lastAt);
    return jsonRes(res, 200, out);
  }
  if (req.method === 'GET') {
    const all = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
    const withNick = decodeURIComponent(String((req.url.match(/[?&]with=([^&]+)/) || [])[1] || ''));
    const other = loadUsers().users.find(u => u.nick && u.nick.toLowerCase() === withNick.toLowerCase());
    if (!other) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    if (blockedEither(blocksDb(), user.id, other.id)) return jsonRes(res, 403, { error: 'Переписка недоступна: один из вас заблокировал другого' });
    const after = Number((req.url.match(/[?&]after=(\d+)/) || [])[1] || 0);
    const key = dmKey(user.id, other.id);
    const msgs = (all.threads[key] || []).filter(m => m.id > after).slice(-100)
      .map(m => ({ ...m, fromAvatar: avatarOf(m.from && m.from.toLowerCase() === user.nick.toLowerCase() ? user : other) }));
    const lr = all.lastRead[key] || {};
    const maxId = (all.threads[key] || []).reduce((a, m) => Math.max(a, m.id), 0);
    if ((lr[user.id] || 0) < maxId) { lr[user.id] = maxId; all.lastRead[key] = lr; saveJson(DM_FILE, all); }
    return jsonRes(res, 200, msgs);
  }
  if (req.method === 'POST') {
    if (isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
    const b = await readBody(req);
    const toNick = sanitizeText(b && b.to, 24);
    let text = sanitizeText(b && b.text, 500);
    if (!toNick || !text) return jsonRes(res, 400, { error: 'Пустое сообщение' });
    if (hasViolence(text)) return jsonRes(res, 400, { error: 'Сообщение нарушает правила и не отправлено' });
    text = filterProfanity(text);
    if (tooMany(userHits, 'dm:' + user.id, 10, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро — до 10 ЛС в минуту' });
    const other = loadUsers().users.find(u => u.nick && u.nick.toLowerCase() === toNick.toLowerCase());
    if (!other) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    if (other.id === user.id) return jsonRes(res, 400, { error: 'Нельзя писать себе' });
    if (blockedEither(blocksDb(), user.id, other.id)) return jsonRes(res, 403, { error: 'Переписка недоступна: один из вас заблокировал другого' });
    const all = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
    const key = dmKey(user.id, other.id);
    const msg = { id: ++all.seq, from: user.nick, fromAvatar: avatarOf(user), to: other.nick, text, at: Date.now() };
    const list = all.threads[key] || [];
    list.push(msg);
    all.threads[key] = list.slice(-500);
    saveJson(DM_FILE, all);
    addNotification(other.id, { type: 'dm', from: user.nick, text: String(text).slice(0, 200) });
    return jsonRes(res, 200, msg);
  }
  jsonRes(res, 405, { error: 'method' });
}
// ===== конец соцчасти v2 =====

// ===== Соцчасть v3: онлайн-статус, публичная карточка, профиль, друзья =====
const FRIENDS_FILE = dataPath('friends.json');
const ONLINE_MS = 2 * 60 * 1000; // активность за 2 минуты = онлайн

// Отметка активности: не чаще раза в 60с на пользователя, чтобы не писать файл на каждый запрос.
// Карта чистится от устаревших записей, иначе росла бы бессрочно (по записи на юзера навсегда).
const lastSeenMem = new Map();
function touchLastSeen(user) {
  const now = Date.now();
  if (lastSeenMem.size > 5000) {
    for (const [id, t] of lastSeenMem) { if (t < now - 60 * 1000) lastSeenMem.delete(id); }
  }
  if ((lastSeenMem.get(user.id) || 0) > now - 60 * 1000) return;
  lastSeenMem.set(user.id, now);
  const db = loadUsers();
  const u = db.users.find(x => x.id === user.id);
  if (u) { u.lastSeen = now; saveUsers(db); }
}

function isOnline(u) { return (u.lastSeen || 0) > Date.now() - ONLINE_MS; }

function friendsDb() { return loadJson(FRIENDS_FILE, {}); }
function friendsOf(db, id) { return db[id] || (db[id] = { friends: [], incoming: [] }); }

function publicUser(u) {
  return {
    nick: u.nick, avatar: avatarOf(u), bio: u.bio || '',
    createdAt: u.createdAt || null, lastSeen: u.lastSeen || null, online: isOnline(u),
    favoriteGenre: u.favoriteGenre || null, stats: u.stats || null,
  };
}

async function handleUserCard(req, res) {
  const nick = decodeURIComponent(String((req.url.match(/[?&]nick=([^&]+)/) || [])[1] || ''));
  const db = loadUsers();
  const u = db.users.find(x => x.nick && x.nick.toLowerCase() === nick.toLowerCase());
  if (!u) return jsonRes(res, 404, { error: 'Пользователь не найден' });
  const out = publicUser(u);
  // активность в соцчасти
  const comments = loadJson(COMMENTS_FILE, {});
  out.commentsCount = Object.values(comments).reduce((a, list) => a + list.filter(c => c.userId === u.id).length, 0);
  const ratings = loadJson(RATINGS_FILE, {});
  out.ratingsCount = Object.values(ratings).filter(v => v[u.id] != null).length;
  // отношения с запрашивающим
  const me = authUser(req);
  if (me && me.id !== u.id) {
    const fdb = friendsDb();
    const mine = friendsOf(fdb, me.id), theirs = friendsOf(fdb, u.id);
    out.friendState = mine.friends.includes(u.id) ? 'friends'
      : mine.incoming.includes(u.id) ? 'incoming'
      : theirs.incoming.includes(me.id) ? 'outgoing' : 'none';
    out.blocked = hasBlocked(blocksDb(), me.id, u.id);
  } else if (me) out.friendState = 'self';
  return jsonRes(res, 200, out);
}

async function handleProfileUpdate(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'auth' });
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  if (tooMany(userHits, 'profile:' + user.id, 20, 60 * 1000)) return jsonRes(res, 429, { error: 'rate limit' });
  const b = await readBody(req);
  const db = loadUsers();
  const u = db.users.find(x => x.id === user.id);
  if (!u) return jsonRes(res, 404, { error: 'user' });
  if (b && typeof b.bio === 'string') u.bio = sanitizeText(b.bio, 200);
  if (b && typeof b.favoriteGenre === 'string') u.favoriteGenre = sanitizeText(b.favoriteGenre, 40);
  if (b && b.stats && typeof b.stats === 'object') {
    u.stats = {
      watchedEpisodes: Math.min(1_000_000, Math.max(0, Math.trunc(Number(b.stats.watchedEpisodes) || 0))),
      watchMinutes: Math.min(10_000_000, Math.max(0, Math.trunc(Number(b.stats.watchMinutes) || 0))),
      startedTitles: Math.min(100_000, Math.max(0, Math.trunc(Number(b.stats.startedTitles) || 0))),
      favoritesCount: Math.min(100_000, Math.max(0, Math.trunc(Number(b.stats.favoritesCount) || 0))),
    };
  }
  saveUsers(db);
  return jsonRes(res, 200, { ok: true });
}

async function handleFriends(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Войдите' });
  const db = loadUsers();
  if (req.method === 'GET') {
    const fdb = friendsDb();
    const mine = friendsOf(fdb, user.id);
    const toCard = id => { const u = db.users.find(x => x.id === id); return u ? publicUser(u) : null; };
    const friends = mine.friends.map(toCard).filter(Boolean)
      .sort((a, b) => (b.online ? 1 : 0) - (a.online ? 1 : 0));
    const incoming = mine.incoming.map(toCard).filter(Boolean);
    return jsonRes(res, 200, { friends, incoming });
  }
  if (req.method === 'POST') {
    const b = await readBody(req);
    // Файл дружбы читаем ПОСЛЕ await readBody: снапшот, взятый до await,
    // затирал бы параллельные заявки (lost update / односторонняя дружба).
    const fdb = friendsDb();
    const mine = friendsOf(fdb, user.id);
    const nick = sanitizeText(b && b.nick, 24);
    const other = db.users.find(x => x.nick && x.nick.toLowerCase() === nick.toLowerCase());
    if (!other) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    if (other.id === user.id) return jsonRes(res, 400, { error: 'Это вы' });
    const theirs = friendsOf(fdb, other.id);
    // Идемпотентное добавление: двойной тап/повторный запрос не плодит дубли в списках.
    const addOnce = (arr, id) => { if (!arr.includes(id)) arr.push(id); };
    if (req.url.startsWith('/alapi/friends/add')) {
      if (mine.friends.includes(other.id)) return jsonRes(res, 200, { state: 'friends' });
      if (mine.incoming.includes(other.id)) {
        // встречная заявка — сразу дружба
        mine.incoming = mine.incoming.filter(i => i !== other.id);
        addOnce(mine.friends, other.id); addOnce(theirs.friends, user.id);
        theirs.incoming = theirs.incoming.filter(i => i !== user.id);
        saveJson(FRIENDS_FILE, fdb);
        addNotification(other.id, { type: 'friend_accept', from: user.nick, text: 'Теперь вы друзья!' });
        return jsonRes(res, 200, { state: 'friends' });
      }
      if (!theirs.incoming.includes(user.id)) {
        if (tooMany(userHits, 'fr:' + user.id, 10, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро' });
        theirs.incoming.push(user.id);
        saveJson(FRIENDS_FILE, fdb);
        addNotification(other.id, { type: 'friend_request', from: user.nick, text: 'Хочет добавить вас в друзья' });
      }
      return jsonRes(res, 200, { state: 'outgoing' });
    }
    if (req.url.startsWith('/alapi/friends/accept')) {
      if (!mine.incoming.includes(other.id)) return jsonRes(res, 400, { error: 'Нет заявки' });
      mine.incoming = mine.incoming.filter(i => i !== other.id);
      addOnce(mine.friends, other.id); addOnce(theirs.friends, user.id);
      theirs.incoming = theirs.incoming.filter(i => i !== user.id);
      saveJson(FRIENDS_FILE, fdb);
      addNotification(other.id, { type: 'friend_accept', from: user.nick, text: 'Принял(а) вашу заявку — теперь вы друзья!' });
      return jsonRes(res, 200, { state: 'friends' });
    }
    if (req.url.startsWith('/alapi/friends/decline')) {
      mine.incoming = mine.incoming.filter(i => i !== other.id);
      saveJson(FRIENDS_FILE, fdb);
      return jsonRes(res, 200, { state: 'none' });
    }
    if (req.url.startsWith('/alapi/friends/remove')) {
      mine.friends = mine.friends.filter(i => i !== other.id);
      theirs.friends = theirs.friends.filter(i => i !== user.id);
      saveJson(FRIENDS_FILE, fdb);
      return jsonRes(res, 200, { state: 'none' });
    }
  }
  jsonRes(res, 405, { error: 'method' });
}
// ===== конец соцчасти v3 =====

// ===== Соцчасть v4: модерация (админ, бан, удаление), мат-фильтр, спойлеры =====
// Мат-фильтр: корни → замена слова на ***. Призывы к насилию — блок отправки.
const PROFANITY_ROOTS = ['хуй', 'хуе', 'хуё', 'хуя', 'пизд', 'ебат', 'ебан', 'ебал', 'ебут', 'еби', 'ёбан', 'заеб', 'заёб', 'уебк', 'уёбк', 'блядь', 'бляд', 'сука', 'мудак', 'мудач', 'долбоеб', 'долбоёб', 'пидор', 'пидар', 'гандон', 'шлюх', 'fuck', 'shit', 'bitch'];
const VIOLENCE_ROOTS = ['убей себя', 'убью тебя', 'сдохни', 'выпились', 'зарежу', 'kill yourself', 'kys'];

function filterProfanity(text) {
  let t = String(text);
  for (const root of PROFANITY_ROOTS) {
    const re = new RegExp('[а-яa-zё]*' + root + '[а-яa-zё]*', 'gi');
    t = t.replace(re, m => '*'.repeat(Math.min(m.length, 5)));
  }
  return t;
}
function hasViolence(text) {
  const low = String(text).toLowerCase();
  return VIOLENCE_ROOTS.some(r => low.includes(r));
}

// Автодетект спойлеров в комментариях
const SPOILER_HINTS = ['спойлер', 'умрёт', 'умрет', 'умирает', 'погибнет', 'погибает', 'убьют', 'убивает', 'концовк', 'финале', 'окажется предателем'];
function looksSpoiler(text) {
  const low = String(text).toLowerCase();
  return SPOILER_HINTS.some(r => low.includes(r));
}

function isBanned(u) { return (u.bannedUntil || 0) > Date.now(); }
function banMessage(u) {
  return 'Вы заблокированы до ' + new Date(u.bannedUntil).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' });
}

async function handleAdmin(req, res) {
  const user = authUser(req);
  if (!user || !user.admin) return jsonRes(res, 403, { error: 'Только для администратора' });
  if (req.method === 'GET' && req.url.startsWith('/alapi/admin/reports')) {
    const all = loadJson(REPORTS_FILE, { seq: 0, items: [] });
    const status = new URL('http://x' + req.url).searchParams.get('status') || 'open';
    return jsonRes(res, 200, all.items.filter(x => status === 'all' || x.status === status).slice(-200).reverse());
  }
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  const b = await readBody(req);
  if (req.url.startsWith('/alapi/admin/reports/resolve')) {
    const all = loadJson(REPORTS_FILE, { seq: 0, items: [] });
    const report = all.items.find(x => x.id === Number(b && b.id));
    if (!report) return jsonRes(res, 404, { error: 'Жалоба не найдена' });
    report.status = ['resolved', 'rejected'].includes(b && b.status) ? b.status : 'resolved';
    report.resolvedAt = Date.now(); report.resolvedBy = user.nick;
    report.resolution = sanitizeText(b && b.resolution, 300);
    saveJson(REPORTS_FILE, all);
    return jsonRes(res, 200, { ok: true });
  }
  if (req.url.startsWith('/alapi/admin/delete-chat')) {
    const id = Number(b && b.id) || 0;
    const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
    const before = chat.messages.length;
    chat.messages = chat.messages.filter(m => m.id !== id);
    saveJson(CHAT_FILE, chat);
    return jsonRes(res, 200, { removed: before - chat.messages.length });
  }
  if (req.url.startsWith('/alapi/admin/delete-comment')) {
    const animeId = String((b && b.animeId) || '');
    const id = Number(b && b.id) || 0;
    const all = loadJson(COMMENTS_FILE, {});
    const list = all[animeId] || [];
    all[animeId] = list.filter(c => c.id !== id);
    saveJson(COMMENTS_FILE, all);
    return jsonRes(res, 200, { removed: list.length - all[animeId].length });
  }
  if (req.url.startsWith('/alapi/admin/ban')) {
    const nick = sanitizeText(b && b.nick, 24);
    const hours = Number(b && b.hours);
    const db = loadUsers();
    const u = db.users.find(x => x.nick && x.nick.toLowerCase() === nick.toLowerCase());
    if (!u) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    if (u.admin) return jsonRes(res, 400, { error: 'Нельзя банить админа' });
    u.bannedUntil = hours > 0 ? Date.now() + hours * 3600 * 1000 : 0;
    saveUsers(db);
    if (hours > 0) addNotification(u.id, { type: 'system', from: 'AniPulse', text: 'Вы заблокированы на ' + hours + ' ч за нарушение правил' });
    return jsonRes(res, 200, { bannedUntil: u.bannedUntil });
  }
  jsonRes(res, 405, { error: 'method' });
}
// ===== конец v4 =====

async function handleChat(req, res) {
  if (req.method === 'GET') {
    const after = Number((req.url.match(/[?&]after=(\d+)/) || [])[1] || 0);
    const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
    // Аватар отдаём АКТУАЛЬНЫЙ по нику, а не снапшот на момент отправки —
    // иначе после смены аватарки старые сообщения показывали старую.
    const avByNick = {};
    for (const u of loadUsers().users) if (u.nick) avByNick[u.nick.toLowerCase()] = avatarOf(u);
    const viewer = authUser(req);
    const hidden = viewer ? blockedIds(blocksDb(), viewer.id) : [];
    const out = chat.messages.filter(m => m.id > after && !hidden.includes(Number(m.userId))).slice(-100).map(({ userId, ...rest }) => ({
      ...rest,
      avatar: avByNick[String(rest.nick || '').toLowerCase()] !== undefined ? avByNick[String(rest.nick || '').toLowerCase()] : (rest.avatar || 0),
    }));
    return jsonRes(res, 200, out);
  }
  if (req.method === 'POST') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы писать в чат' });
    if (user.emailVerified === false) return jsonRes(res, 403, { error: 'Подтвердите почту: Профиль → код из письма' });
    if (isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
    const b = await readBody(req);
    let text = sanitizeText(b && b.text, 500);
    if (!text) return jsonRes(res, 400, { error: 'Пустое сообщение' });
    if (hasViolence(text)) return jsonRes(res, 400, { error: 'Сообщение нарушает правила и не отправлено' });
    text = filterProfanity(text);
    if (tooMany(userHits, 'chat:' + user.id, 5, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро — до 5 сообщений в минуту' });
    const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
    const msg = { id: ++chat.seq, userId: user.id, nick: user.nick, avatar: avatarOf(user), text, at: Date.now() };
    const rid = Number(b && b.replyTo) || 0;
    if (rid) { const orig = chat.messages.find(m => m.id === rid); if (orig) msg.replyTo = { id: orig.id, nick: orig.nick, text: String(orig.text).slice(0, 80) }; }
    chat.messages.push(msg);
    if (chat.messages.length > 500) chat.messages = chat.messages.slice(-500);
    saveJson(CHAT_FILE, chat);
    notifyMentions(text, user, "chat");
    return jsonRes(res, 200, msg);
  }
  if (req.method === 'DELETE') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы удалить сообщение' });
    const id = Number((req.url.match(/[?&]id=(\d+)/) || [])[1] || 0);
    const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
    const before = chat.messages.length;
    chat.messages = chat.messages.filter(m => m.id !== id || (m.userId !== user.id && !user.admin));
    saveJson(CHAT_FILE, chat);
    return jsonRes(res, 200, { removed: before - chat.messages.length });
  }
  jsonRes(res, 405, { error: 'method' });
}
async function handleComments(req, res) {
  const animeId = decodeURIComponent(String((req.url.match(/[?&]animeId=([\w:.%-]+)/) || [])[1] || ''));
  if (req.method === 'GET') {
    if (!animeId) return jsonRes(res, 400, { error: 'animeId required' });
    const all = loadJson(COMMENTS_FILE, {});
    // userId — внутреннее поле (нужно только для admin delete-comment по id, не по userId);
    // публично не отдаём, чтобы не облегчать перечисление аккаунтов по нику↔id.
    const avByNick = {};
    for (const u of loadUsers().users) if (u.nick) avByNick[u.nick.toLowerCase()] = avatarOf(u);
    const viewer = authUser(req);
    const hidden = viewer ? blockedIds(blocksDb(), viewer.id) : [];
    const out = (all[animeId] || []).filter(c => !hidden.includes(Number(c.userId))).slice(-100).map(({ userId, ...rest }) => ({
      ...rest,
      avatar: avByNick[String(rest.nick || '').toLowerCase()] !== undefined ? avByNick[String(rest.nick || '').toLowerCase()] : (rest.avatar || 0),
    }));
    return jsonRes(res, 200, out);
  }
  if (req.method === 'POST') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы комментировать' });
    if (user.emailVerified === false) return jsonRes(res, 403, { error: 'Подтвердите почту: Профиль → код из письма' });
    if (isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
    const b = await readBody(req);
    const id = String((b && b.animeId) || '').slice(0, 40);
    let text = sanitizeText(b && b.text, 1000);
    if (!id || !text) return jsonRes(res, 400, { error: 'Пустой комментарий' });
    if (hasViolence(text)) return jsonRes(res, 400, { error: 'Комментарий нарушает правила и не отправлен' });
    text = filterProfanity(text);
    if (tooMany(userHits, 'cm:' + user.id, 3, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро — до 3 комментариев в минуту' });
    const all = loadJson(COMMENTS_FILE, {});
    const list = all[id] || [];
    const cm = { id: Date.now() + Math.floor(Math.random() * 1000), userId: user.id, nick: user.nick, avatar: avatarOf(user), text, at: Date.now() };
    if ((b && b.spoiler) || looksSpoiler(text)) cm.spoiler = true;
    list.push(cm);
    all[id] = list.slice(-300);
    saveJson(COMMENTS_FILE, all);
    notifyMentions(text, user, "comment:" + id);
    return jsonRes(res, 200, cm);
  }
  if (req.method === 'DELETE') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы удалить комментарий' });
    const id = Number((req.url.match(/[?&]id=(\d+)/) || [])[1] || 0);
    const all = loadJson(COMMENTS_FILE, {});
    const list = all[animeId] || [];
    all[animeId] = list.filter(c => c.id !== id || (c.userId !== user.id && !user.admin));
    saveJson(COMMENTS_FILE, all);
    return jsonRes(res, 200, { removed: list.length - all[animeId].length });
  }
  jsonRes(res, 405, { error: 'method' });
}
async function handleRating(req, res) {
  // Батч для бейджей на постерах: GET /alapi/ratings?ids=1,2,3 -> { id: {avg,count} }
  if (req.method === "GET" && req.url.startsWith("/alapi/ratings")) {
    const ids = decodeURIComponent(String((req.url.match(/[?&]ids=([^&]+)/) || [])[1] || ""))
      .split(",").map(x => x.trim()).filter(x => x && !isNaN(x)).slice(0, 100);
    const all = loadJson(RATINGS_FILE, {});
    const out = {};
    for (const id of ids) {
      const vals = Object.values(all[id] || {});
      if (!vals.length) continue;
      out[id] = { avg: Math.round(vals.reduce((a, b) => a + b, 0) / vals.length * 10) / 10, count: vals.length };
    }
    return jsonRes(res, 200, out);
  }

  const animeId = String((req.url.match(/[?&]animeId=(\d+)/) || [])[1] || '');
  if (req.method === 'GET') {
    if (!animeId) return jsonRes(res, 400, { error: 'animeId required' });
    const all = loadJson(RATINGS_FILE, {});
    const votes = all[animeId] || {};
    const vals = Object.values(votes);
    const user = authUser(req);
    return jsonRes(res, 200, {
      avg: vals.length ? Math.round(vals.reduce((a, b) => a + b, 0) / vals.length * 10) / 10 : null,
      count: vals.length,
      my: user ? (votes[user.id] || null) : null,
    });
  }
  if (req.method === 'POST') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы оценивать' });
    if (user.emailVerified === false) return jsonRes(res, 403, { error: 'Подтвердите почту' });
    if (tooMany(userHits, 'rating:' + user.id, 30, 60 * 1000)) return jsonRes(res, 429, { error: 'rate limit' });
    const b = await readBody(req);
    const id = String((b && b.animeId) || '');
    const score = Number(b && b.score);
    if (!/^\d{1,12}$/.test(id) || !Number.isInteger(score) || !(score >= 1 && score <= 10)) return jsonRes(res, 400, { error: 'Оценка 1-10' });
    const all = loadJson(RATINGS_FILE, {});
    const votes = all[id] || {};
    votes[user.id] = Math.round(score);
    all[id] = votes;
    saveJson(RATINGS_FILE, all);
    const vals = Object.values(votes);
    return jsonRes(res, 200, { avg: Math.round(vals.reduce((a, b) => a + b, 0) / vals.length * 10) / 10, count: vals.length, my: votes[user.id] });
  }
  if (req.method === 'DELETE') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'Войдите, чтобы удалить оценку' });
    if (!animeId) return jsonRes(res, 400, { error: 'animeId required' });
    const all = loadJson(RATINGS_FILE, {});
    const votes = all[animeId] || {};
    delete votes[user.id];
    all[animeId] = votes;
    saveJson(RATINGS_FILE, all);
    const vals = Object.values(votes);
    return jsonRes(res, 200, { avg: vals.length ? Math.round(vals.reduce((a, b) => a + b, 0) / vals.length * 10) / 10 : null, count: vals.length, my: null });
  }
  jsonRes(res, 405, { error: 'method' });
}
// смена аватара (пресет 0-11)
async function handleAvatar(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
  const b = await readBody(req);
  const avatar = Number(b && b.avatar);
  if (!(avatar >= 0 && avatar <= 11)) return jsonRes(res, 400, { error: 'avatar 0-11' });
  const db = loadUsers();
  const u = db.users.find(x => x.id === user.id);
  if (!u) return jsonRes(res, 401, { error: 'Не авторизован' });
  u.avatar = avatar;
  u.customAvatar = false; // выбор пресета отключает кастомную аватарку
  saveUsers(db);
  return jsonRes(res, 200, { avatar });
}

// ===== Кастомные аватарки: загрузка своего изображения =====
const AVATARS_DIR = dataPath('avatars');
if (!fs.existsSync(AVATARS_DIR)) fs.mkdirSync(AVATARS_DIR, { mode: 0o700 });
const AVATAR_MAX_B64 = 400 * 1024; // ~300КБ картинки; клиент жмёт до 256x256 JPEG (~20-40КБ)

function jpegDimensions(buf) {
  if (buf.length < 4 || buf[0] !== 0xFF || buf[1] !== 0xD8) return null;
  let p = 2;
  while (p + 9 < buf.length) {
    if (buf[p] !== 0xFF) { p++; continue; }
    const marker = buf[p + 1]; p += 2;
    if (marker === 0xD9 || marker === 0xDA) break;
    if (p + 2 > buf.length) return null;
    const length = buf.readUInt16BE(p);
    if (length < 2 || p + length > buf.length) return null;
    if ([0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF].includes(marker)) {
      return { height: buf.readUInt16BE(p + 3), width: buf.readUInt16BE(p + 5) };
    }
    p += length;
  }
  return null;
}

// ===== Модерация: жалобы и пользовательские блокировки =====
function removeRelationship(a, b) {
  const fdb = friendsDb();
  const aa = friendsOf(fdb, a), bb = friendsOf(fdb, b);
  aa.friends = aa.friends.filter(id => id !== b); aa.incoming = aa.incoming.filter(id => id !== b);
  bb.friends = bb.friends.filter(id => id !== a); bb.incoming = bb.incoming.filter(id => id !== a);
  saveJson(FRIENDS_FILE, fdb);
}

function reportSnapshot(type, targetId, animeId, reporterId) {
  if (type === 'chat') {
    return loadJson(CHAT_FILE, { messages: [] }).messages.find(m => m.id === Number(targetId)) || null;
  }
  if (type === 'comment') {
    const all = loadJson(COMMENTS_FILE, {});
    return (all[String(animeId || '')] || []).find(m => m.id === Number(targetId)) || null;
  }
  if (type === 'dm') {
    const all = loadJson(DM_FILE, { threads: {} });
    for (const [key, list] of Object.entries(all.threads || {})) {
      if (!key.split(':').map(Number).includes(reporterId)) continue;
      const found = list.find(m => m.id === Number(targetId));
      if (found) return found;
    }
    return null;
  }
  return type === 'profile' ? { nick: sanitizeText(targetId, 24) } : null;
}

async function handleModeration(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Войдите в аккаунт' });
  if (req.method === 'GET' && req.url.startsWith('/alapi/blocks')) {
    const ids = blockedIds(blocksDb(), user.id);
    const users = loadUsers().users;
    return jsonRes(res, 200, ids.map(id => users.find(u => u.id === id)?.nick).filter(Boolean));
  }
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  const b = await readBody(req);
  if (req.url.startsWith('/alapi/blocks')) {
    const nick = sanitizeText(b && b.nick, 24);
    const targetId = userIdByNick(nick);
    if (!targetId) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    if (targetId === user.id) return jsonRes(res, 400, { error: 'Нельзя заблокировать себя' });
    const all = blocksDb();
    const mine = blockedIds(all, user.id);
    const block = b.action !== 'unblock';
    all[String(user.id)] = block ? [...new Set([...mine, targetId])] : mine.filter(id => id !== targetId);
    saveJson(BLOCKS_FILE, all);
    if (block) removeRelationship(user.id, targetId);
    return jsonRes(res, 200, { blocked: block });
  }
  if (req.url.startsWith('/alapi/reports')) {
    if (tooMany(userHits, 'report:' + user.id, 10, 24 * 60 * 60 * 1000)) {
      return jsonRes(res, 429, { error: 'Не более 10 жалоб в сутки' });
    }
    const type = String((b && b.type) || '');
    if (!['chat', 'comment', 'dm', 'profile'].includes(type)) return jsonRes(res, 400, { error: 'Неверный тип жалобы' });
    const targetId = String((b && b.targetId) || '');
    const snapshot = reportSnapshot(type, targetId, b && b.animeId, user.id);
    if (!snapshot) return jsonRes(res, 404, { error: 'Объект жалобы не найден' });
    const reason = sanitizeText(b && b.reason, 80);
    if (!reason) return jsonRes(res, 400, { error: 'Укажите причину' });
    const all = loadJson(REPORTS_FILE, { seq: 0, items: [] });
    const report = {
      id: ++all.seq, reporterId: user.id, reporterNick: user.nick, type,
      targetId, targetNick: sanitizeText((b && b.targetNick) || snapshot.nick || snapshot.from, 24),
      animeId: sanitizeText(b && b.animeId, 40), reason,
      details: sanitizeText(b && b.details, 500), snapshot, status: 'open', createdAt: Date.now(),
    };
    all.items.push(report); all.items = all.items.slice(-2000); saveJson(REPORTS_FILE, all);
    return jsonRes(res, 200, { ok: true, reportId: report.id });
  }
  return jsonRes(res, 404, { error: 'not found' });
}

/** Аватар пользователя для публичных ответов: -1 = кастомный (клиент грузит /alapi/avatar-img). */
function avatarOf(u) { return u.customAvatar ? -1 : (u.avatar || 0); }

async function handleAvatarUpload(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
  if (user.emailVerified === false) return jsonRes(res, 403, { error: 'Подтвердите почту: Профиль → код из письма' });
  if (tooMany(userHits, 'av:' + user.id, 5, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро' });
  const b = await readBody(req, AVATAR_MAX_B64 + 4096);
  const b64 = b && typeof b.image === 'string' ? b.image : null;
  if (!b64 || b64.length > AVATAR_MAX_B64) return jsonRes(res, 400, { error: 'Картинка не больше 300КБ' });
  let buf;
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(b64)) return jsonRes(res, 400, { error: 'Битые данные' });
  try { buf = Buffer.from(b64, 'base64'); } catch (e) { return jsonRes(res, 400, { error: 'Битые данные' }); }
  // Официальный клиент всегда предварительно обрезает и перекодирует аватар в JPEG.
  // Один формат уменьшает поверхность парсеров; дополнительно проверяем структуру и размеры.
  const isJpeg = buf.length > 3 && buf[0] === 0xFF && buf[1] === 0xD8 && buf[2] === 0xFF;
  const dimensions = isJpeg && jpegDimensions(buf);
  if (!dimensions || dimensions.width < 32 || dimensions.height < 32 || dimensions.width > 512 || dimensions.height > 512) {
    return jsonRes(res, 400, { error: 'Нужен корректный JPEG размером до 512×512' });
  }
  // Имя файла — только числовой id пользователя, никакого пользовательского ввода в пути.
  fs.writeFileSync(`${AVATARS_DIR}/${Number(user.id)}.img`, buf, { mode: 0o600 });
  const db = loadUsers();
  const u = db.users.find(x => x.id === user.id);
  if (!u) return jsonRes(res, 401, { error: 'Не авторизован' });
  u.customAvatar = true;
  u.avatarRev = (u.avatarRev || 0) + 1; // для сброса клиентского кэша картинки
  saveUsers(db);
  return jsonRes(res, 200, { ok: true, avatar: -1, avatarRev: u.avatarRev });
}

function handleAvatarImg(req, res) {
  const nick = decodeURIComponent(String((req.url.match(/[?&]nick=([^&]+)/) || [])[1] || ''));
  const u = loadUsers().users.find(x => x.nick && x.nick.toLowerCase() === nick.toLowerCase());
  if (!u || !u.customAvatar) { res.writeHead(404); return res.end('no avatar'); }
  let buf;
  try { buf = fs.readFileSync(`${AVATARS_DIR}/${Number(u.id)}.img`); } catch (e) { res.writeHead(404); return res.end('no avatar'); }
  res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Cache-Control': 'public, max-age=86400', 'X-Content-Type-Options': 'nosniff' });
  res.end(buf);
}


// ===== OAuth: Яндекс (ключи в /opt/anipulse/oauth.json, chmod 600) + VK ID (PKCE, без секрета) =====
const OAUTH_FILE = dataPath('oauth.json');
function oauthCfg() { try { return JSON.parse(fs.readFileSync(OAUTH_FILE, 'utf8')); } catch (e) { return {}; } }
const OAUTH_REDIRECT_BASE = 'https://anipulsetv.ru/alapi/auth';
// OAuth-состояния (PKCE/nonce) хранятся в ФАЙЛЕ, а не в памяти: рестарт сервера
// (деплой) раньше стирал их — если вкладка авторизации VK была открыта до рестарта,
// колбэк приходил с валидным кодом, но неизвестным state («Ссылка устарела»).
const OAUTH_STATE_FILE = dataPath('oauth-state.json');
function oauthStateAll() {
  const all = loadJson(OAUTH_STATE_FILE, { pkce: {}, yandex: {}, codes: {} });
  all.pkce = all.pkce || {}; all.yandex = all.yandex || {}; all.codes = all.codes || {};
  return all;
}
function oauthStateSweepSave(all) {
  const now = Date.now();
  for (const k of Object.keys(all.pkce)) if (all.pkce[k].exp < now) delete all.pkce[k];
  for (const k of Object.keys(all.yandex)) if (all.yandex[k].exp < now) delete all.yandex[k];
  for (const k of Object.keys(all.codes || {})) if (all.codes[k].exp < now) delete all.codes[k];
  saveJson(OAUTH_STATE_FILE, all);
}
function oauthCodeKey(code) { return crypto.createHash('sha256').update(String(code)).digest('hex'); }
function createOAuthCode(type, value, ttlMs = 60 * 1000) {
  const code = crypto.randomBytes(32).toString('base64url');
  const all = oauthStateAll();
  all.codes[oauthCodeKey(code)] = { type, ...value, exp: Date.now() + ttlMs };
  oauthStateSweepSave(all);
  return code;
}
function consumeOAuthCode(code, type) {
  if (!/^[A-Za-z0-9_-]{40,128}$/.test(code)) return null;
  const all = oauthStateAll(), key = oauthCodeKey(code), value = all.codes[key];
  delete all.codes[key]; oauthStateSweepSave(all);
  return value && value.type === type && value.exp > Date.now() ? value : null;
}
const pkceStore = {
  set(k, v) { const a = oauthStateAll(); a.pkce[k] = v; oauthStateSweepSave(a); },
  get(k) { const v = oauthStateAll().pkce[k]; return v && v.exp > Date.now() ? v : undefined; },
  delete(k) { const a = oauthStateAll(); delete a.pkce[k]; oauthStateSweepSave(a); },
};
function uniqueNick(db, base) {
  let nick = String(base || 'user').replace(/[^\wа-яА-ЯёЁ .-]/g, '').trim().slice(0, 20) || 'user';
  let candidate = nick, i = 1;
  while (db.users.some(u => u.nick.toLowerCase() === candidate.toLowerCase())) candidate = nick + (++i);
  return candidate;
}
function socialLogin(provider, extId, displayName, res, state) {
  const db = loadUsers();
  // Источник входа: веб (state 'web'/'web.<...>') → редирект на сайт; иначе deep link Android.
  let web = false, realState = state || '';
  if (realState === 'web') { web = true; realState = ''; }
  else if (realState.startsWith('web.')) { web = true; realState = realState.slice(4); }
  const consentAccepted = realState === 'consent.2026-07-22';
  if (realState && realState.startsWith('link.')) {
    const linkGrant = consumeOAuthCode(realState.slice(5), 'link');
    const userId = linkGrant && linkGrant.userId;
    const u = userId && db.users.find(x => x.id === userId);
    if (u) {
      u.linked = u.linked || {}; u.linked[provider] = String(extId); saveUsers(db);
      const loc = web ? 'https://anipulsetv.ru/profile?linked=' + provider : 'https://anipulsetv.ru/auth/android-callback?linked=' + provider;
      res.writeHead(302, { Location: loc }); return res.end();
    }
  }
  let user = db.users.find(u => u.linked && u.linked[provider] === String(extId));
  if (!user) {
    if (!consentAccepted) {
      res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
      return res.end('Для создания аккаунта вернитесь в AniPulse и примите условия и согласие на обработку данных.');
    }
    const now = Date.now();
    user = {
      id: ++db.seq,
      nick: uniqueNick(db, displayName),
      email: provider + '_' + extId + '@social.anipulse',
      pass: hashPassword(crypto.randomBytes(16).toString('hex')),
      linked: {}, createdAt: now,
      termsAcceptedAt: now, termsVersion: '2026-07-22',
      privacyConsentAt: now, privacyConsentVersion: '2026-07-22',
    };
    user.linked[provider] = String(extId);
    db.users.push(user); saveUsers(db);
  } else if (consentAccepted && (!user.termsAcceptedAt || !user.privacyConsentAt)) {
    const now = Date.now();
    user.termsAcceptedAt = now; user.termsVersion = '2026-07-22';
    user.privacyConsentAt = now; user.privacyConsentVersion = '2026-07-22';
    saveUsers(db);
  }
  const code = createOAuthCode('login', { userId: user.id });
  const loc = web
    ? 'https://anipulsetv.ru/auth/callback?code=' + encodeURIComponent(code)
    : 'https://anipulsetv.ru/auth/android-callback?code=' + encodeURIComponent(code);
  res.writeHead(302, { Location: loc });
  res.end();
}
// CSRF-защита колбэка Яндекса: клиентский state не проверялся на возврате (login CSRF) —
// заводим свой серверный nonce и связываем его с исходным (link-)state, как у VK.
const yandexStateStore = {
  set(k, v) { const a = oauthStateAll(); a.yandex[k] = v; oauthStateSweepSave(a); },
  get(k) { const v = oauthStateAll().yandex[k]; return v && v.exp > Date.now() ? v : undefined; },
  delete(k) { const a = oauthStateAll(); delete a.yandex[k]; oauthStateSweepSave(a); },
};
async function handleOAuthYandex(req, res, isCallback) {
  try {
    const cfg = (oauthCfg().yandex) || {};
    if (!cfg.client_id) { res.writeHead(503); return res.end('yandex oauth not configured'); }
    const q = new URL('http://x' + req.url).searchParams;
    if (!isCallback) {
      const nonce = crypto.randomBytes(16).toString('hex');
      yandexStateStore.set(nonce, { linkState: q.get('state') || '', exp: Date.now() + 10 * 60 * 1000 });
      const url = 'https://oauth.yandex.ru/authorize?response_type=code&client_id=' + cfg.client_id +
        '&redirect_uri=' + encodeURIComponent(OAUTH_REDIRECT_BASE + '/yandex/callback') +
        '&state=' + encodeURIComponent(nonce);
      res.writeHead(302, { Location: url }); return res.end();
    }
    const code = q.get('code'), nonce = q.get('state') || '';
    const saved = yandexStateStore.get(nonce);
    if (!code || !saved) { res.writeHead(400); return res.end('no code/state'); }
    yandexStateStore.delete(nonce);
    const body = 'grant_type=authorization_code&code=' + encodeURIComponent(code) +
      '&client_id=' + cfg.client_id + '&client_secret=' + cfg.client_secret;
    const tokenResp = JSON.parse((await fetchFollow('https://oauth.yandex.ru/token', { method: 'POST', body })).body.toString());
    if (!tokenResp.access_token) { res.writeHead(502); return res.end('yandex token error'); }
    const info = JSON.parse((await fetchFollow('https://login.yandex.ru/info?format=json', {
      headers: { 'Authorization': 'OAuth ' + tokenResp.access_token },
    })).body.toString());
    if (!info.id) { res.writeHead(502); return res.end('yandex info error'); }
    socialLogin('yandex', info.id, info.display_name || info.real_name || info.login, res, saved.linkState);
  } catch (e) { res.writeHead(502); res.end('oauth error: ' + e.message); }
}
async function handleOAuthVk(req, res, isCallback) {
  try {
    const cfg = (oauthCfg().vk) || {};
    if (!cfg.client_id) { res.writeHead(503); return res.end('vk oauth not configured'); }
    const q = new URL('http://x' + req.url).searchParams;
    if (!isCallback) {
      const verifier = crypto.randomBytes(32).toString('base64url');
      const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
      const state = crypto.randomBytes(16).toString('hex');
      // Код привязки хранится только на сервере и не отправляется OAuth-провайдеру.
      pkceStore.set(state, { verifier, link: q.get('state') || '', exp: Date.now() + 10 * 60 * 1000 });
      const url = 'https://id.vk.com/authorize?response_type=code&client_id=' + cfg.client_id +
        '&redirect_uri=' + encodeURIComponent(OAUTH_REDIRECT_BASE + '/vk/callback') +
        '&state=' + encodeURIComponent(state) +
        '&code_challenge=' + challenge + '&code_challenge_method=s256&scope=vkid.personal_info';
      res.writeHead(302, { Location: url }); return res.end();
    }
    const code = q.get('code'), state = q.get('state') || '', deviceId = q.get('device_id') || '';
    const saved = pkceStore.get(state);
    if (!code || !saved) {
      // Диагностика в journal (без секретов): что именно пришло от VK.
      console.error('vk callback rejected:', JSON.stringify({
        hasCode: !!code, hasState: !!state, knownState: !!saved,
        gotState: state.slice(0, 16),
        stored: Object.keys(oauthStateAll().pkce).map(k => k.slice(0, 16)),
        vkError: q.get('error'), vkErrorDesc: q.get('error_description'),
      }));
      const human = q.get('error')
        ? 'VK отклонил вход: ' + (q.get('error_description') || q.get('error'))
        : (!saved && state)
          ? 'Ссылка уже использована или устарела (10 мин) — вернитесь в приложение и попробуйте ещё раз'
          : 'VK не вернул код авторизации — попробуйте ещё раз';
      res.writeHead(400, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end(human);
    }
    pkceStore.delete(state);
    const body = 'grant_type=authorization_code&code=' + encodeURIComponent(code) +
      '&code_verifier=' + saved.verifier + '&client_id=' + cfg.client_id +
      '&device_id=' + encodeURIComponent(deviceId) + '&state=' + encodeURIComponent(state) +
      '&redirect_uri=' + encodeURIComponent(OAUTH_REDIRECT_BASE + '/vk/callback');
    const tokenResp = JSON.parse((await fetchFollow('https://id.vk.com/oauth2/auth', { method: 'POST', body })).body.toString());
    if (!tokenResp.access_token) { res.writeHead(502); return res.end('vk token error: ' + JSON.stringify(tokenResp).slice(0, 200)); }
    const infoResp = JSON.parse((await fetchFollow('https://id.vk.com/oauth2/user_info', {
      method: 'POST',
      body: 'client_id=' + cfg.client_id + '&access_token=' + encodeURIComponent(tokenResp.access_token),
    })).body.toString());
    const u = infoResp.user || {};
    if (!u.user_id) { res.writeHead(502); return res.end('vk info error'); }
    const linkState = saved.link || ''; // из своего хранилища, не из эха VK
    socialLogin('vk', u.user_id, [u.first_name, u.last_name].filter(Boolean).join(' '), res, linkState);
  } catch (e) { res.writeHead(502); res.end('oauth error: ' + e.message); }
}

// ===== Публичные юридические страницы =====
const LEGAL_CONTACT = 'anipulse.noreply@yandex.ru';
const LEGAL_UPDATED = '22 июля 2026';
const LEGAL_FILE = dataPath('legal.json');
function legalCfg() {
  return loadJson(LEGAL_FILE, {
    operatorName: 'НЕ ЗАПОЛНЕНО', operatorAddress: 'НЕ ЗАПОЛНЕНО', operatorInn: '',
    serverCountry: 'Российская Федерация', contact: LEGAL_CONTACT,
  });
}

function legalPage(title, bodyHtml) {
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} — AniPulse</title>
<style>
  body{background:#0C0C12;color:#F2F0F7;font-family:-apple-system,Segoe UI,Roboto,sans-serif;
    max-width:720px;margin:0 auto;padding:32px 20px 64px;line-height:1.6;}
  h1{font-size:26px;background:linear-gradient(90deg,#7C4DFF,#FF4D8D);-webkit-background-clip:text;
    background-clip:text;color:transparent;}
  h2{font-size:18px;margin-top:32px;color:#F2F0F7;}
  p,li{color:#C9C6D6;font-size:15px;}
  a{color:#FF4D8D;}
  .updated{color:#8B889C;font-size:13px;margin-bottom:24px;}
  .notice{border:1px solid #FF4D8D;border-radius:12px;padding:12px;color:#F2F0F7;background:#24131d;}
</style></head><body>
<h1>AniPulse</h1>
<div class="updated">Обновлено: ${LEGAL_UPDATED}</div>
${bodyHtml}
</body></html>`;
}

function handlePrivacyPage(res) {
  const cfg = legalCfg(), contact = cfg.contact || LEGAL_CONTACT;
  const html = legalPage('Политика конфиденциальности', `
<h2>1. Оператор</h2>
<p>Оператор персональных данных: <b>${cfg.operatorName}</b>. Адрес: ${cfg.operatorAddress}.${cfg.operatorInn ? ` ИНН: ${cfg.operatorInn}.` : ''} Контакт: <a href="mailto:${contact}">${contact}</a>.</p>
<h2>Какие данные мы собираем</h2>
<p>При регистрации: ник, почта, пароль (хранится только в виде необратимого хеша scrypt — мы никогда не видим и не храним пароль в открытом виде). При входе через VK/Яндекс — идентификатор вашего аккаунта в этом сервисе, без пароля.</p>
<p>По желанию: аватар (готовый пресет или загруженное и обрезанное пользователем изображение), короткое «о себе», любимый жанр.</p>
<p>Статистика просмотра (число серий, минут, тайтлов, избранного) синхронизируется с сервером как агрегированные числа для карточки профиля. Сам список просмотренного и прогресс серий хранятся только в локальной базе приложения на защищённом хранилище Android и на сервер не передаются. Токен входа и настройки приложения хранятся отдельно в зашифрованном хранилище.</p>
<p>Контент, который вы создаёте сами: сообщения в чате и личных сообщениях, комментарии, оценки тайтлов, заявки в друзья — хранится на нашем сервере, чтобы работать для всех пользователей.</p>
<p>Автоматически: IP-адрес (только для защиты от злоупотреблений — ограничение частоты запросов, не хранится долговременно), при добровольной отправке баг-репорта — модель устройства и версия Android.</p>
<h2>Как мы используем данные</h2>
<ul>
<li>Вход и работа аккаунта, восстановление пароля, подтверждение почты</li>
<li>Работа социальных функций (чат, ЛС, друзья, комментарии, рейтинги)</li>
<li>Защита от спама и злоупотреблений (ограничения частоты, бан за нарушение правил)</li>
<li>Уведомления о новых сериях, сообщениях и упоминаниях</li>
</ul>
<h2>Кому мы передаём данные</h2>
<p>Никому не продаём и не передаём третьим лицам для рекламы. Для работы приложения используются:</p>
<ul>
<li><b>Shikimori</b> — каталог и описания тайтлов (без передачи ваших персональных данных)</li>
<li><b>Kodik, AniLibria и их CDN</b> — источники видео. При прямом воспроизведении источник получает IP-адрес устройства и стандартные технические сведения сетевого запроса; отдельные узлы Kodik могут находиться за пределами России. Ник, email, сообщения и токен AniPulse этим источникам не передаются</li>
<li><b>VK, Яндекс</b> — только если вы сами выбрали вход через них, по их собственным политикам конфиденциальности</li>
<li><b>Яндекс.Почта</b> — для отправки писем с кодом подтверждения/восстановления пароля</li>
</ul>
<p>Правовые основания: отдельное согласие пользователя, исполнение пользовательского соглашения и законный интерес в защите сервиса от злоупотреблений.</p>
<p>При прямом обращении к каталогу, CDN или источнику видео соответствующий сервис технически получает ваш IP-адрес и стандартные сетевые данные запроса. Для Kodik такая передача может осуществляться в Нидерланды. AniPulse не передаёт этим сервисам ваш ник, почту, сообщения или токен входа. Трансграничное воспроизведение допускается только после выполнения применимых требований законодательства и получения необходимого согласия пользователя.</p>
<h2>Хранение и безопасность</h2>
<p>Основная база данных находится в стране: ${cfg.serverCountry}. Пароли хешируются scrypt, токены можно отозвать, а локальный токен хранится в защищённом хранилище Android.</p>
<h2>Сроки хранения</h2>
<ul><li>аккаунт и профиль — до удаления аккаунта;</li><li>чат, комментарии, ЛС и оценки — до удаления автором, лимита хранилища или аккаунта;</li><li>жалобы и решения модерации — до 3 лет в обезличенном виде;</li><li>технические журналы защиты — не более 30 дней.</li></ul>
<h2>Ваши права</h2>
<p>Вы можете получить сведения об обработке, исправить данные, отозвать согласие и удалить аккаунт из экрана «Аккаунт». По запросу на ${contact} ответ или удаление выполняются не позднее 30 календарных дней, если закон не требует хранить отдельные сведения дольше.</p>
<h2>Контакты</h2>
<p>По вопросам конфиденциальности: <a href="mailto:${contact}">${contact}</a></p>
<p>Мы можем обновлять эту политику; дата последнего обновления указана вверху страницы.</p>
`);
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
}

function sendLegalPage(res, title, body) {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'public, max-age=300' });
  res.end(legalPage(title, body));
}

function handleTermsPage(res) {
  const cfg = legalCfg(), contact = cfg.contact || LEGAL_CONTACT;
  sendLegalPage(res, 'Пользовательское соглашение', `<h2>1. Общие условия</h2><p>AniPulse — beta-сервис ${cfg.operatorName}. Создавая аккаунт, вы принимаете эти условия. Сервис предназначен для лиц старше 18 лет.</p><h2>2. Аккаунт</h2><p>Вы отвечаете за сохранность доступа и действия в аккаунте. Аккаунт можно удалить в приложении.</p><h2>3. Контент и общение</h2><p>Пользователь сохраняет права на свой контент и даёт сервису неисключительное право показывать его в AniPulse. Запрещены угрозы, травля, спам, мошенничество, ненависть, порнография и нарушение чужих прав. Детали: <a href="/community-rules">Правила сообщества</a>.</p><h2>4. Модерация</h2><p>Мы можем скрыть или удалить контент, ограничить или заблокировать аккаунт при нарушениях. Жалобы подаются из меню контента.</p><h2>5. Beta-статус</h2><p>Функции могут меняться, а доступность материалов зависит от внешних источников. Мы не гарантируем бесперебойную работу.</p><h2>6. Контакт</h2><p><a href="mailto:${contact}">${contact}</a></p>`);
}

function handleCommunityRulesPage(res) {
  sendLegalPage(res, 'Правила сообщества', `<h2>Будьте уважительны</h2><p>Нельзя угрожать, травить, преследовать, оскорблять или раскрывать чужие личные данные.</p><h2>Запрещённый контент</h2><ul><li>порнография, особенно с участием несовершеннолетних;</li><li>призывы к насилию, суициду, экстремизму или ненависти;</li><li>наркотики, оружие, мошенничество, фишинг и вредоносные ссылки;</li><li>спам, реклама без согласования и нарушение авторских прав.</li></ul><h2>Спойлеры</h2><p>Отмечайте спойлеры и не раскрывайте сюжет в нике или аватаре.</p><h2>Жалобы</h2><p>Выберите «Пожаловаться» в меню сообщения, комментария или профиля. Заведомо ложные жалобы также нарушают правила.</p>`);
}

function handleConsentPage(res) {
  const cfg = legalCfg(), contact = cfg.contact || LEGAL_CONTACT;
  sendLegalPage(res, 'Согласие на обработку персональных данных', `<p>Я свободно, своей волей и в своём интересе даю ${cfg.operatorName}, адрес: ${cfg.operatorAddress}, согласие на автоматизированную обработку моих данных: ника, email, ID аккаунта, аватара, профиля, оценок, списков, статистики, сообщений, комментариев, жалоб и технических данных.</p><p>Цели: создание и защита аккаунта, синхронизация, социальные функции, модерация, уведомления и техническая поддержка. Действия: сбор, запись, хранение, уточнение, использование, передача указанным в политике обработчикам, блокирование, удаление и уничтожение.</p><p>При отдельном выборе источника Kodik пользовательское устройство может передать IP-адрес и стандартные технические сведения сетевого запроса CDN, расположенному в Нидерландах, исключительно для доставки выбранного видеопотока. Ник, email, сообщения и токен AniPulse не передаются.</p><p>Согласие действует до удаления аккаунта или отзыва согласия. Отозвать его можно письмом на <a href="mailto:${contact}">${contact}</a> или удалением аккаунта. <a href="/privacy">Полная политика</a>.</p>`);
}

function handleRightHoldersPage(res) {
  const html = legalPage('Для правообладателей', `
<h2>О сервисе</h2>
<p>AniPulse — каталог и агрегатор ссылок на аниме. Мы не храним и не размещаем видеофайлы на своих серверах: воспроизведение происходит через сторонние источники (Kodik, AniLibria), уже находящиеся в открытом доступе в интернете. Описания и метаданные тайтлов берутся из открытого каталога Shikimori.</p>
<h2>Если вы правообладатель</h2>
<p>Если вы считаете, что какой-то тайтл в нашем каталоге нарушает ваши права, напишите нам, указав:</p>
<ul>
<li>Подтверждение ваших прав на произведение (документы или ссылки на официальные источники)</li>
<li>Точное название тайтла/серии и источник, на который жалоба</li>
<li>Ваши контактные данные для обратной связи</li>
</ul>
<p>Мы оперативно рассмотрим обращение и, при обоснованности претензии, уберём тайтл или конкретный источник озвучки из нашего каталога. Обратите внимание: мы можем убрать ссылку/привязку в нашем приложении, но не можем удалить сам видеофайл — он размещён на стороннем сервисе (Kodik/AniLibria), и для полного удаления также нужно обращаться напрямую к ним.</p>
<h2>Контакты</h2>
<p><a href="mailto:${LEGAL_CONTACT}">${LEGAL_CONTACT}</a></p>
`);
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
}

// Любой не пойманный throw в async-хендлере раньше становился unhandledRejection
// и валил весь процесс (полный даунтайм до рестарта systemd). Теперь: 500 клиенту,
// лог в journal, сервер живёт дальше.
const server = http.createServer((req, res) => {
  route(req, res).catch((e) => {
    console.error('handler error:', req.url, e);
    try {
      if (!res.headersSent) jsonRes(res, 500, { error: 'Внутренняя ошибка сервера' });
      else res.end();
    } catch (_) { /* сокет уже закрыт */ }
  });
});
async function route(req, res) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  // CORS для веб-клиента (anipulsetv.ru + локальная разработка): браузерные fetch
  // из веб-версии иначе режутся. Разрешаем только наши источники, не «*».
  const origin = req.headers['origin'] || '';
  if (/^https:\/\/(www\.)?anipulsetv\.ru$/.test(origin) || /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
    res.setHeader('Access-Control-Max-Age', '86400');
  }
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  if (req.url === '/.well-known/assetlinks.json') {
    return jsonRes(res, 200, [{
      relation: ['delegate_permission/common.handle_all_urls'],
      target: {
        namespace: 'android_app',
        package_name: 'com.anipulse.app',
        sha256_cert_fingerprints: ['4D:53:4C:C7:64:4D:F0:D7:3A:0A:4A:34:30:EA:BB:90:DB:FB:BB:30:8F:FC:4D:6F:F9:22:C1:32:B7:EE:AB:62'],
      },
    }]);
  }
  if (req.url === '/privacy') return handlePrivacyPage(res);
  if (req.url === '/terms') return handleTermsPage(res);
  if (req.url === '/community-rules') return handleCommunityRulesPage(res);
  if (req.url === '/personal-data-consent') return handleConsentPage(res);
  if (req.url === '/for-right-holders') return handleRightHoldersPage(res);
  if (req.url.startsWith('/auth/android-callback')) {
    const html = legalPage('Возврат в приложение', '<h2>Вернитесь в AniPulse</h2><p>Если приложение не открылось автоматически, установите актуальную beta-версию и повторите вход.</p>');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    return res.end(html);
  }
  const dubsM = req.url.match(/^\/alapi\/kodik-dubs\?shikimoriId=(\d+)/);
  if (dubsM) return handleKodikDubs(dubsM[1], res);
  const findM = req.url.match(/^\/alapi\/kodik-find\?shikimoriId=(\d+)/);
  if (findM) return handleKodikFind(findM[1], res);
  const kodikM = req.url.match(/^\/alapi\/kodik\?link=([^&]+)(?:&episode=(\d+))?/);
  if (kodikM) return handleKodik(decodeURIComponent(kodikM[1]), kodikM[2], res);
  if (req.url.startsWith('/alapi/bugreport')) return handleBugReport(req, res);
  if (req.url.startsWith('/alapi/admin/')) return handleAdmin(req, res);
  if (req.url.startsWith('/alapi/blocks') || req.url.startsWith('/alapi/reports')) return handleModeration(req, res);
  if (req.url.startsWith('/alapi/user')) return handleUserCard(req, res);
  if (req.url.startsWith('/alapi/profile')) return handleProfileUpdate(req, res);
  if (req.url.startsWith('/alapi/friends')) return handleFriends(req, res);
  if (req.url.startsWith('/alapi/notifications')) return handleNotifications(req, res);
  if (req.url.startsWith('/alapi/dm')) return handleDm(req, res);
  if (req.url.startsWith('/alapi/chat')) return handleChat(req, res);
  if (req.url.startsWith('/alapi/comments')) return handleComments(req, res);
  if (req.url.startsWith('/alapi/rating')) return handleRating(req, res);
  if (req.url.startsWith('/alapi/avatar-upload')) return handleAvatarUpload(req, res);
  if (req.url.startsWith('/alapi/avatar-img')) return handleAvatarImg(req, res);
  if (req.url.startsWith('/alapi/avatar')) return handleAvatar(req, res);
  if (req.url.startsWith('/alapi/auth/yandex/callback')) return handleOAuthYandex(req, res, true);
  if (req.url.startsWith('/alapi/auth/yandex')) return handleOAuthYandex(req, res, false);
  if (req.url.startsWith('/alapi/auth/vk/callback')) return handleOAuthVk(req, res, true);
  if (req.url.startsWith('/alapi/auth/vk')) return handleOAuthVk(req, res, false);
  const authM = req.url.match(/^\/alapi\/auth\/([a-z-]+)/);
  if (authM) return handleAuth(req, res, authM[1]);
  if (req.url.startsWith('/alapi/app-version')) return handleAppVersion(res);
  if (req.url.startsWith('/alapi/apk')) return handleApkDownload(res);
  if (req.url.startsWith('/alapi/anilibria-updates')) return handleAnilibriaUpdates(res);
  const poster = req.url.match(/^\/alapi\/poster\/(\d+)/);
  if (poster) return handlePoster(poster[1], res);

  const m = req.url.match(/^\/alapi\/([a-z0-9]+)\/(.*)$/);
  if (!m) { res.writeHead(404); return res.end('not found'); }
  const base = UPSTREAMS[m[1]];
  if (!base) { res.writeHead(404); return res.end('unknown source'); }
  const target = base + '/' + m[2];
  // Постеры не меняются по URL — неделя клиентского кэша (дисковый кэш Coil),
  // повторные заходы в каталог больше не тянут картинки по сети вообще.
  const imgHeaders = (ct) => String(ct || '').startsWith('image/')
    ? { 'Cache-Control': 'public, max-age=604800, immutable' } : {};
  const hit = cache.get(target);
  if (hit && hit.exp > Date.now()) {
    res.writeHead(hit.status, { 'Content-Type': hit.ctype, 'X-Cache': 'HIT', ...imgHeaders(hit.ctype) });
    return res.end(hit.body);
  }
  try {
    const r = await fetchFollow(target);
    const isImg = String(r.ctype || '').startsWith('image/');
    if (r.status === 200 && r.body.length <= 5 * 1024 * 1024) {
      if (cache.size >= 300) cache.delete(cache.keys().next().value);
      cache.set(target, { ...r, exp: Date.now() + (isImg ? IMG_TTL_MS : TTL_MS) });
    }
    res.writeHead(r.status, { 'Content-Type': r.ctype, ...imgHeaders(r.ctype) }); res.end(r.body);
  } catch (e) { res.writeHead(502); res.end('gateway error: ' + e.message); }
}
// Страховка на случай промисов вне запросов (таймеры, почта): лог вместо падения процесса.
process.on('unhandledRejection', (e) => console.error('unhandledRejection:', e));
const LISTEN_PORT = Number(process.env.ANIPULSE_PORT || 8090);
server.listen(LISTEN_PORT, '127.0.0.1', () => console.log(`AniPulse gateway on 127.0.0.1:${LISTEN_PORT}`));
server.headersTimeout = 15_000;
server.requestTimeout = 30_000;
server.keepAliveTimeout = 5_000;
server.maxRequestsPerSocket = 100;
