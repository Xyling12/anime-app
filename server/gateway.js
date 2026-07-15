// AniPulse API-шлюз: обход блокировок РФ + ddos-guard + кэш + постеры + Kodik (маппинг по shikimori_id + извлечение HD).
const http = require('http');
const https = require('https');
const { URL } = require('url');

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
const KODIK_TOKEN = '447d179e875efe44217f20d1ee2146be';
const UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';
const cache = new Map();
const posterCache = new Map();
const TTL_MS = 60 * 1000;
const POSTER_TTL_MS = 24 * 60 * 60 * 1000;

function fetchFollow(urlStr, { cookies = {}, redirects = 0, method = 'GET', body = null, headers = {} } = {}) {
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
      const ch = []; res.on('data', d => ch.push(d));
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
async function handleKodik(link, episode, res) {
  try {
    let pageUrl = link.startsWith('//') ? 'https:' + link : link;
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

async function handlePoster(id, res) {
  const hit = posterCache.get(id);
  if (hit && hit.exp > Date.now()) { res.writeHead(302, { Location: hit.path }); return res.end(); }
  try {
    const r = await fetchFollow(`${UPSTREAMS.jikan}/v4/anime/${id}`);
    const img = JSON.parse(r.body.toString())?.data?.images?.jpg?.large_image_url;
    if (!img) { res.writeHead(404); return res.end('no poster'); }
    const path = '/alapi/malcdn' + new URL(img).pathname;
    posterCache.set(id, { path, exp: Date.now() + POSTER_TTL_MS });
    res.writeHead(302, { Location: path }); res.end();
  } catch (e) { res.writeHead(502); res.end('poster error: ' + e.message); }
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
const fs = require('fs');
const USERS_FILE = '/opt/anipulse/users.json';
const SECRET_FILE = '/opt/anipulse/auth-secret';
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
  return (req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || 'unknown';
}
function loadUsers() { try { return JSON.parse(fs.readFileSync(USERS_FILE, 'utf8')); } catch (e) { return { seq: 0, users: [] }; } }
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
  if (sig !== parts[2]) return null;
  return userId;
}
const MAX_BODY = 64 * 1024; // защита от гигантских тел
function readBody(req) {
  return new Promise((resolve) => {
    const ch = []; let size = 0;
    req.on('data', d => { size += d.length; if (size > MAX_BODY) { req.destroy(); resolve(null); return; } ch.push(d); });
    req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(ch).toString())); } catch (e) { resolve(null); } });
  });
}
function jsonRes(res, code, obj) { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj)); }
function authUserEarly(req) {
  const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
  if (!userId) return null;
  return loadUsers().users.find(u => u.id === userId) || null;
}
const SMTP_FILE = '/opt/anipulse/smtp.json';
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
function newVerifyCode(u) {
  u.verifyCode = String(Math.floor(100000 + Math.random() * 900000));
  u.verifyExp = Date.now() + 15 * 60 * 1000;
  sendMail(u.email, 'Код подтверждения AniPulse', 'Ваш код: ' + u.verifyCode + '\n\nКод действует 15 минут.');
}

async function handleAuth(req, res, path) {
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
  if (path === 'register' && req.method === 'POST') {
    const b = await readBody(req);
    if (!b || !b.nick || !b.email || !b.password) return jsonRes(res, 400, { error: 'Заполните все поля' });
    const nick = String(b.nick).trim(), email = String(b.email).trim().toLowerCase();
    if (nick.length < 3 || nick.length > 24) return jsonRes(res, 400, { error: 'Ник: 3-24 символа' });
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return jsonRes(res, 400, { error: 'Некорректная почта' });
    if (String(b.password).length < 6) return jsonRes(res, 400, { error: 'Пароль: минимум 6 символов' });
    const db = loadUsers();
    if (db.users.some(u => u.nick.toLowerCase() === nick.toLowerCase())) return jsonRes(res, 409, { error: 'Ник занят' });
    if (db.users.some(u => u.email === email)) return jsonRes(res, 409, { error: 'Почта уже зарегистрирована' });
    const user = { id: ++db.seq, nick, email, pass: hashPassword(String(b.password)), linked: {}, createdAt: Date.now() };
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
    return jsonRes(res, 200, { token: makeToken(user.id), nick: user.nick, email: user.email });
  }
  if (path === 'verify' && req.method === 'POST') {
    const user = authUser(req);
    if (!user) return jsonRes(res, 401, { error: 'auth' });
    const b = await readBody(req);
    const db = loadUsers(); const u = db.users.find(x => x.id === user.id);
    if (u.emailVerified !== false) return jsonRes(res, 200, { ok: true });
    if (!b || String(b.code) !== u.verifyCode || (u.verifyExp || 0) < Date.now()) return jsonRes(res, 400, { error: 'Неверный или просроченный код' });
    u.emailVerified = true; delete u.verifyCode; delete u.verifyExp; saveUsers(db);
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
  if (path === 'me') {
    const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
    if (!userId) return jsonRes(res, 401, { error: 'Не авторизован' });
    const user = loadUsers().users.find(u => u.id === userId);
    if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
    return jsonRes(res, 200, { nick: user.nick, email: user.email, avatar: user.avatar || 0, linked: Object.keys(user.linked || {}), emailVerified: user.emailVerified !== false, admin: !!user.admin });
  }
  jsonRes(res, 404, { error: 'not found' });
}


// ===== Соцчасть: общий чат, комментарии к тайтлам, свой рейтинг 1-10 =====
const CHAT_FILE = '/opt/anipulse/chat.json';
const COMMENTS_FILE = '/opt/anipulse/comments.json';
const RATINGS_FILE = '/opt/anipulse/ratings.json';
function loadJson(f, def) { try { return JSON.parse(fs.readFileSync(f, 'utf8')); } catch (e) { return def; } }
function saveJson(f, obj) { fs.writeFileSync(f + '.tmp', JSON.stringify(obj)); fs.renameSync(f + '.tmp', f); }
function authUser(req) {
  const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
  if (!userId) return null;
  const u = loadUsers().users.find(x => x.id === userId) || null;
  if (u) touchLastSeen(u);
  return u;
}
function sanitizeText(t, max) { return String(t || '').replace(/\s+/g, ' ').trim().slice(0, max); }
// ===== Соцчасть v2: уведомления, @упоминания, ЛС =====
const NOTIF_FILE = '/opt/anipulse/notifications.json';
const DM_FILE = '/opt/anipulse/dms.json';

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
  for (const n of nicks) {
    const u = users.find(x => x.nick && x.nick.toLowerCase() === n);
    if (!u || u.id === fromUser.id) continue;
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
  const all = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
  if (req.method === 'GET' && req.url.startsWith('/alapi/dm/list')) {
    const users = loadUsers().users;
    const out = [];
    for (const [key, msgs] of Object.entries(all.threads)) {
      const ids = key.split(':').map(Number);
      if (!ids.includes(user.id) || !msgs.length) continue;
      const otherId = ids[0] === user.id ? ids[1] : ids[0];
      const other = users.find(u => u.id === otherId);
      const last = msgs[msgs.length - 1];
      const lastRead = (all.lastRead[key] || {})[user.id] || 0;
      out.push({
        withNick: other ? other.nick : '?',
        withAvatar: other ? (other.avatar || 0) : 0,
        lastText: last.text, lastAt: last.at,
        unread: msgs.filter(m => m.id > lastRead && m.from !== user.nick).length,
      });
    }
    out.sort((a, b) => b.lastAt - a.lastAt);
    return jsonRes(res, 200, out);
  }
  if (req.method === 'GET') {
    const withNick = decodeURIComponent(String((req.url.match(/[?&]with=([^&]+)/) || [])[1] || ''));
    const other = loadUsers().users.find(u => u.nick && u.nick.toLowerCase() === withNick.toLowerCase());
    if (!other) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    const after = Number((req.url.match(/[?&]after=(\d+)/) || [])[1] || 0);
    const key = dmKey(user.id, other.id);
    const msgs = (all.threads[key] || []).filter(m => m.id > after).slice(-100);
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
    const key = dmKey(user.id, other.id);
    const msg = { id: ++all.seq, from: user.nick, fromAvatar: user.avatar || 0, to: other.nick, text, at: Date.now() };
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
const FRIENDS_FILE = '/opt/anipulse/friends.json';
const ONLINE_MS = 2 * 60 * 1000; // активность за 2 минуты = онлайн

// Отметка активности: не чаще раза в 60с на пользователя, чтобы не писать файл на каждый запрос.
const lastSeenMem = new Map();
function touchLastSeen(user) {
  const now = Date.now();
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
    nick: u.nick, avatar: u.avatar || 0, bio: u.bio || '',
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
  } else if (me) out.friendState = 'self';
  return jsonRes(res, 200, out);
}

async function handleProfileUpdate(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'auth' });
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  const b = await readBody(req);
  const db = loadUsers();
  const u = db.users.find(x => x.id === user.id);
  if (!u) return jsonRes(res, 404, { error: 'user' });
  if (b && typeof b.bio === 'string') u.bio = sanitizeText(b.bio, 200);
  if (b && typeof b.favoriteGenre === 'string') u.favoriteGenre = sanitizeText(b.favoriteGenre, 40);
  if (b && b.stats && typeof b.stats === 'object') {
    u.stats = {
      watchedEpisodes: Math.max(0, Number(b.stats.watchedEpisodes) || 0),
      watchMinutes: Math.max(0, Number(b.stats.watchMinutes) || 0),
      startedTitles: Math.max(0, Number(b.stats.startedTitles) || 0),
      favoritesCount: Math.max(0, Number(b.stats.favoritesCount) || 0),
    };
  }
  saveUsers(db);
  return jsonRes(res, 200, { ok: true });
}

async function handleFriends(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Войдите' });
  const db = loadUsers();
  const fdb = friendsDb();
  const mine = friendsOf(fdb, user.id);
  if (req.method === 'GET') {
    const toCard = id => { const u = db.users.find(x => x.id === id); return u ? publicUser(u) : null; };
    const friends = mine.friends.map(toCard).filter(Boolean)
      .sort((a, b) => (b.online ? 1 : 0) - (a.online ? 1 : 0));
    const incoming = mine.incoming.map(toCard).filter(Boolean);
    return jsonRes(res, 200, { friends, incoming });
  }
  if (req.method === 'POST') {
    const b = await readBody(req);
    const nick = sanitizeText(b && b.nick, 24);
    const other = db.users.find(x => x.nick && x.nick.toLowerCase() === nick.toLowerCase());
    if (!other) return jsonRes(res, 404, { error: 'Пользователь не найден' });
    if (other.id === user.id) return jsonRes(res, 400, { error: 'Это вы' });
    const theirs = friendsOf(fdb, other.id);
    if (req.url.startsWith('/alapi/friends/add')) {
      if (mine.friends.includes(other.id)) return jsonRes(res, 200, { state: 'friends' });
      if (mine.incoming.includes(other.id)) {
        // встречная заявка — сразу дружба
        mine.incoming = mine.incoming.filter(i => i !== other.id);
        mine.friends.push(other.id); theirs.friends.push(user.id);
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
      mine.friends.push(other.id); theirs.friends.push(user.id);
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
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  const b = await readBody(req);
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
    return jsonRes(res, 200, chat.messages.filter(m => m.id > after).slice(-100));
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
    const msg = { id: ++chat.seq, userId: user.id, nick: user.nick, avatar: user.avatar || 0, text, at: Date.now() };
    const rid = Number(b && b.replyTo) || 0;
    if (rid) { const orig = chat.messages.find(m => m.id === rid); if (orig) msg.replyTo = { id: orig.id, nick: orig.nick, text: String(orig.text).slice(0, 80) }; }
    chat.messages.push(msg);
    if (chat.messages.length > 500) chat.messages = chat.messages.slice(-500);
    saveJson(CHAT_FILE, chat);
    notifyMentions(text, user, "chat");
    return jsonRes(res, 200, msg);
  }
  jsonRes(res, 405, { error: 'method' });
}
async function handleComments(req, res) {
  const animeId = decodeURIComponent(String((req.url.match(/[?&]animeId=([\w:.%-]+)/) || [])[1] || ''));
  if (req.method === 'GET') {
    if (!animeId) return jsonRes(res, 400, { error: 'animeId required' });
    const all = loadJson(COMMENTS_FILE, {});
    return jsonRes(res, 200, (all[animeId] || []).slice(-100));
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
    const cm = { id: Date.now() + Math.floor(Math.random() * 1000), userId: user.id, nick: user.nick, avatar: user.avatar || 0, text, at: Date.now() };
    if ((b && b.spoiler) || looksSpoiler(text)) cm.spoiler = true;
    list.push(cm);
    all[id] = list.slice(-300);
    saveJson(COMMENTS_FILE, all);
    notifyMentions(text, user, "comment:" + id);
    return jsonRes(res, 200, cm);
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
    const b = await readBody(req);
    const id = String((b && b.animeId) || '');
    const score = Number(b && b.score);
    if (!id || !(score >= 1 && score <= 10)) return jsonRes(res, 400, { error: 'Оценка 1-10' });
    const all = loadJson(RATINGS_FILE, {});
    const votes = all[id] || {};
    votes[user.id] = Math.round(score);
    all[id] = votes;
    saveJson(RATINGS_FILE, all);
    const vals = Object.values(votes);
    return jsonRes(res, 200, { avg: Math.round(vals.reduce((a, b) => a + b, 0) / vals.length * 10) / 10, count: vals.length, my: votes[user.id] });
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
  u.avatar = avatar; saveUsers(db);
  return jsonRes(res, 200, { avatar });
}


// ===== OAuth: Яндекс (ключи в /opt/anipulse/oauth.json, chmod 600) + VK ID (PKCE, без секрета) =====
const OAUTH_FILE = '/opt/anipulse/oauth.json';
function oauthCfg() { try { return JSON.parse(fs.readFileSync(OAUTH_FILE, 'utf8')); } catch (e) { return {}; } }
const OAUTH_REDIRECT_BASE = 'https://5-42-99-195.sslip.io/alapi/auth';
const pkceStore = new Map(); // state -> {verifier, exp}
function uniqueNick(db, base) {
  let nick = String(base || 'user').replace(/[^\wа-яА-ЯёЁ .-]/g, '').trim().slice(0, 20) || 'user';
  let candidate = nick, i = 1;
  while (db.users.some(u => u.nick.toLowerCase() === candidate.toLowerCase())) candidate = nick + (++i);
  return candidate;
}
function socialLogin(provider, extId, displayName, res, state) {
  const db = loadUsers();
  if (state && state.startsWith('link.')) {
    const userId = verifyToken(state.slice(5));
    const u = userId && db.users.find(x => x.id === userId);
    if (u) {
      u.linked = u.linked || {}; u.linked[provider] = String(extId); saveUsers(db);
      res.writeHead(302, { Location: 'anipulse://auth?linked=' + provider }); return res.end();
    }
  }
  let user = db.users.find(u => u.linked && u.linked[provider] === String(extId));
  if (!user) {
    user = {
      id: ++db.seq,
      nick: uniqueNick(db, displayName),
      email: provider + '_' + extId + '@social.anipulse',
      pass: hashPassword(crypto.randomBytes(16).toString('hex')),
      linked: {}, createdAt: Date.now(),
    };
    user.linked[provider] = String(extId);
    db.users.push(user); saveUsers(db);
  }
  const token = makeToken(user.id);
  res.writeHead(302, { Location: 'anipulse://auth?token=' + encodeURIComponent(token) + '&nick=' + encodeURIComponent(user.nick) });
  res.end();
}
async function handleOAuthYandex(req, res, isCallback) {
  try {
    const cfg = (oauthCfg().yandex) || {};
    if (!cfg.client_id) { res.writeHead(503); return res.end('yandex oauth not configured'); }
    const q = new URL('http://x' + req.url).searchParams;
    if (!isCallback) {
      const url = 'https://oauth.yandex.ru/authorize?response_type=code&client_id=' + cfg.client_id +
        '&redirect_uri=' + encodeURIComponent(OAUTH_REDIRECT_BASE + '/yandex/callback') +
        '&state=' + encodeURIComponent(q.get('state') || '');
      res.writeHead(302, { Location: url }); return res.end();
    }
    const code = q.get('code');
    if (!code) { res.writeHead(400); return res.end('no code'); }
    const body = 'grant_type=authorization_code&code=' + encodeURIComponent(code) +
      '&client_id=' + cfg.client_id + '&client_secret=' + cfg.client_secret;
    const tokenResp = JSON.parse((await fetchFollow('https://oauth.yandex.ru/token', { method: 'POST', body })).body.toString());
    if (!tokenResp.access_token) { res.writeHead(502); return res.end('yandex token error'); }
    const info = JSON.parse((await fetchFollow('https://login.yandex.ru/info?format=json', {
      headers: { 'Authorization': 'OAuth ' + tokenResp.access_token },
    })).body.toString());
    if (!info.id) { res.writeHead(502); return res.end('yandex info error'); }
    socialLogin('yandex', info.id, info.display_name || info.real_name || info.login, res, q.get('state') || '');
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
      const state = crypto.randomBytes(8).toString('hex') + '_' + (q.get('state') || '');
      pkceStore.set(state, { verifier, exp: Date.now() + 10 * 60 * 1000 });
      for (const [k, v] of pkceStore) if (v.exp < Date.now()) pkceStore.delete(k);
      const url = 'https://id.vk.com/authorize?response_type=code&client_id=' + cfg.client_id +
        '&redirect_uri=' + encodeURIComponent(OAUTH_REDIRECT_BASE + '/vk/callback') +
        '&state=' + encodeURIComponent(state) +
        '&code_challenge=' + challenge + '&code_challenge_method=s256&scope=vkid.personal_info';
      res.writeHead(302, { Location: url }); return res.end();
    }
    const code = q.get('code'), state = q.get('state') || '', deviceId = q.get('device_id') || '';
    const saved = pkceStore.get(state);
    if (!code || !saved) { res.writeHead(400); return res.end('no code/state'); }
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
    const linkState = state.includes('_') ? state.slice(state.indexOf('_') + 1) : '';
    socialLogin('vk', u.user_id, [u.first_name, u.last_name].filter(Boolean).join(' '), res, linkState);
  } catch (e) { res.writeHead(502); res.end('oauth error: ' + e.message); }
}

const server = http.createServer(async (req, res) => {
  const dubsM = req.url.match(/^\/alapi\/kodik-dubs\?shikimoriId=(\d+)/);
  if (dubsM) return handleKodikDubs(dubsM[1], res);
  const findM = req.url.match(/^\/alapi\/kodik-find\?shikimoriId=(\d+)/);
  if (findM) return handleKodikFind(findM[1], res);
  const kodikM = req.url.match(/^\/alapi\/kodik\?link=([^&]+)(?:&episode=(\d+))?/);
  if (kodikM) return handleKodik(decodeURIComponent(kodikM[1]), kodikM[2], res);
  if (req.url.startsWith('/alapi/admin/')) return handleAdmin(req, res);
  if (req.url.startsWith('/alapi/user')) return handleUserCard(req, res);
  if (req.url.startsWith('/alapi/profile')) return handleProfileUpdate(req, res);
  if (req.url.startsWith('/alapi/friends')) return handleFriends(req, res);
  if (req.url.startsWith('/alapi/notifications')) return handleNotifications(req, res);
  if (req.url.startsWith('/alapi/dm')) return handleDm(req, res);
  if (req.url.startsWith('/alapi/chat')) return handleChat(req, res);
  if (req.url.startsWith('/alapi/comments')) return handleComments(req, res);
  if (req.url.startsWith('/alapi/rating')) return handleRating(req, res);
  if (req.url.startsWith('/alapi/avatar')) return handleAvatar(req, res);
  if (req.url.startsWith('/alapi/auth/yandex/callback')) return handleOAuthYandex(req, res, true);
  if (req.url.startsWith('/alapi/auth/yandex')) return handleOAuthYandex(req, res, false);
  if (req.url.startsWith('/alapi/auth/vk/callback')) return handleOAuthVk(req, res, true);
  if (req.url.startsWith('/alapi/auth/vk')) return handleOAuthVk(req, res, false);
  const authM = req.url.match(/^\/alapi\/auth\/([a-z]+)/);
  if (authM) return handleAuth(req, res, authM[1]);
  if (req.url.startsWith('/alapi/anilibria-updates')) return handleAnilibriaUpdates(res);
  const poster = req.url.match(/^\/alapi\/poster\/(\d+)/);
  if (poster) return handlePoster(poster[1], res);

  const m = req.url.match(/^\/alapi\/([a-z0-9]+)\/(.*)$/);
  if (!m) { res.writeHead(404); return res.end('not found'); }
  const base = UPSTREAMS[m[1]];
  if (!base) { res.writeHead(404); return res.end('unknown source'); }
  const target = base + '/' + m[2];
  const hit = cache.get(target);
  if (hit && hit.exp > Date.now()) { res.writeHead(hit.status, { 'Content-Type': hit.ctype, 'X-Cache': 'HIT' }); return res.end(hit.body); }
  try {
    const r = await fetchFollow(target);
    if (r.status === 200) cache.set(target, { ...r, exp: Date.now() + TTL_MS });
    res.writeHead(r.status, { 'Content-Type': r.ctype }); res.end(r.body);
  } catch (e) { res.writeHead(502); res.end('gateway error: ' + e.message); }
});
server.listen(8090, '127.0.0.1', () => console.log('AniPulse gateway on 127.0.0.1:8090'));
