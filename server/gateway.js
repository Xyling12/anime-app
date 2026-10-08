// AniPulse API-шлюз: обход блокировок РФ + ddos-guard + кэш + постеры + Kodik (маппинг по shikimori_id + извлечение HD).
const http = require('http');
const https = require('https');
const fs = require('fs');
const pathModule = require('path');
const { URL } = require('url');
const analyticsStore = require('./analytics-store');
const {
  ByteLruCache,
  assertSafeHttpsUrl,
  createSafeLookup,
  detectRasterContentType,
  isSafeProxyContentType,
  isShikimoriPosterPath,
  publicProxyPolicy,
  safeProxyHeaders,
} = require('./proxy-security');
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

// Контент, который нужно исключить из каталога для публикации APK в РФ.
// Список составлен по замечанию модерации; ID включают сезоны/спецвыпуски.
const BLOCKED_ANIME_IDS = new Set([
  1535, 2994, // Death Note
  226, 376,   // Elfen Lied
  34542,      // Inuyashiki
  22319, 27899, 36511, 37799, 30458, 31297 // Tokyo Ghoul и связанные сезоны/спецвыпуски
]);
function filterBlockedAnimePayload(path, body) {
  if (!String(path).startsWith('/api/')) return null;
  let data;
  try { data = JSON.parse(body.toString()); } catch { return null; }
  const isAnime = (x) => x && BLOCKED_ANIME_IDS.has(Number(x.id));
  if (Array.isArray(data)) {
    const filtered = data.filter(x => !isAnime(x));
    return Buffer.from(JSON.stringify(filtered));
  }
  if (isAnime(data)) return Buffer.from(JSON.stringify({ error: 'not found' }));
  return null;
}
const KODIK_API = 'https://kodik-api.com';
const KODIK_TOKEN = process.env.KODIK_TOKEN || (() => {
  try { return fs.readFileSync(dataPath('kodik-token'), 'utf8').trim(); } catch (_) { return ''; }
})();
const UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';
const cache = new ByteLruCache({ maxBytes: 32 * 1024 * 1024, maxEntries: 200 });
const proxyInflight = new Map();
const MAX_PROXY_INFLIGHT = 12;
const posterCache = new Map();
const TTL_MS = 60 * 1000;
const IMG_TTL_MS = 30 * 60 * 1000; // картинки в серверном кэше держим дольше текста
const POSTER_TTL_MS = 24 * 60 * 60 * 1000;

// Периодическая чистка кэшей: записи раньше только помечались просроченными,
// но не удалялись из Map — память росла к MemoryMax=150M юнита, под давлением
// часть запросов картинок начинала фейлиться (репорт «не все постеры грузятся»).
setInterval(() => {
  const now = Date.now();
  cache.prune(now);
  for (const [k, v] of posterCache) { if (v.exp <= now) posterCache.delete(k); }
}, 5 * 60 * 1000).unref();

const safeLookup = createSafeLookup();
function fetchFollow(urlStr, {
  cookieJar = new Map(),
  redirects = 0,
  method = 'GET',
  body = null,
  headers = {},
  maxBytes = 8 * 1024 * 1024,
  redirectAllowed = (from, to) => from.hostname === to.hostname,
} = {}) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('too many redirects'));
    let u;
    try { u = assertSafeHttpsUrl(urlStr); } catch (e) { return reject(e); }
    const originCookies = cookieJar.get(u.origin) || {};
    const cookieHeader = Object.entries(originCookies).map(([k, v]) => `${k}=${v}`).join('; ');
    const h = { 'User-Agent': UA, 'Accept': 'application/json, text/html, image/*, */*', ...headers };
    if (cookieHeader) h['Cookie'] = cookieHeader;
    if (body) {
      if (!Object.keys(h).some(key => key.toLowerCase() === 'content-type')) {
        h['Content-Type'] = 'application/x-www-form-urlencoded';
      }
      h['Content-Length'] = Buffer.byteLength(body);
    }
    const req = https.request(u, { method, headers: h, lookup: safeLookup }, (res) => {
      const updatedCookies = { ...originCookies };
      (res.headers['set-cookie'] || []).forEach((c) => {
        const [pair] = c.split(';'); const idx = pair.indexOf('=');
        if (idx > 0) updatedCookies[pair.slice(0, idx).trim()] = pair.slice(idx + 1).trim();
      });
      cookieJar.set(u.origin, updatedCookies);
      if ([301,302,303,307,308].includes(res.statusCode) && res.headers.location) {
        let next;
        try { next = assertSafeHttpsUrl(new URL(res.headers.location, u)); } catch (e) {
          res.resume();
          return reject(e);
        }
        if (!redirectAllowed(u, next)) {
          res.resume();
          return reject(new Error('upstream redirect host is not allowed'));
        }
        const keepMethod = res.statusCode === 307 || res.statusCode === 308;
        const sameOrigin = next.origin === u.origin;
        const nextHeaders = sameOrigin
          ? headers
          : Object.fromEntries(Object.entries(headers).filter(([key]) =>
            !['authorization', 'cookie', 'proxy-authorization'].includes(key.toLowerCase())));
        res.resume();
        return resolve(fetchFollow(next.toString(), {
          cookieJar,
          redirects: redirects + 1,
          method: keepMethod ? method : 'GET',
          body: keepMethod ? body : null,
          headers: nextHeaders,
          maxBytes,
          redirectAllowed,
        }));
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

// Повтор для JSON-апстримов, отвечающих 5xx. Появился из-за api.aniskip.com:
// с сервера он отдаёт 500 примерно через раз (500/200/500/200 подряд) — похоже,
// за балансировщиком лежит один из бэкендов. Клиент делал один запрос, ловил
// ошибку, и пропуск опенинга молча не работал у половины зрителей.
//
// Только для kind === 'json': ответы маленькие, запросы идемпотентные. Картинки
// сюда не попадают намеренно — повторы на них дали бы шторм трафика.
// 4xx не повторяем: это ответ по существу, а не сбой.
async function fetchJsonUpstream(target, options, retries = 2) {
  let last;
  for (let attempt = 0; attempt <= retries; attempt++) {
    if (attempt > 0) await new Promise(resolve => setTimeout(resolve, 300 * attempt));
    try {
      last = await fetchFollow(target, options);
    } catch (error) {
      if (attempt === retries) throw error;
      continue;
    }
    if (last.status < 500 || last.status > 599) return last;
  }
  return last;
}

// Расшифровка src Kodik — в kodik-decode.js (там же объяснение и тесты).
const { kodikDecode } = require('./kodik-decode');

// /alapi/kodik-find?shikimoriId=X -> {link, translation, quality} (маппинг по shikimori_id)
async function handleKodikFind(id, res) {
  try {
    const r = await fetchFollow(`${KODIK_API}/get-player?shikimoriID=${id}&token=${KODIK_TOKEN}&title=x`, { maxBytes: 512 * 1024 });
    const parsed = JSON.parse(r.body.toString());
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(parsed));
  } catch (e) { res.writeHead(502); res.end('kodik-find error: ' + e.message); }
}


// Kodik отдаёт названия студий как в HTML: «AEROChannelEkat &amp; Risha».
function decodeHtmlEntities(text) {
  return String(text || '')
    .replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'")
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}

// /alapi/kodik-dubs?shikimoriId=X -> [{title,type,link}] — ВСЕ озвучки Kodik (бесплатно).
async function handleKodikDubs(id, res){
  try{
    const r=await fetchFollow(`${KODIK_API}/get-player?shikimoriID=${id}&token=${KODIK_TOKEN}&title=x`, { maxBytes: 512 * 1024 });
    const j=JSON.parse(r.body.toString());
    if(!j.found||!j.link){ res.writeHead(200,{'Content-Type':'application/json'}); return res.end('[]'); }
    const pageUrl=(j.link.startsWith('//')?'https:'+j.link:j.link);
    const pageHost = assertSafeHttpsUrl(pageUrl).hostname;
    if (!isAllowedKodikHost(pageHost)) throw new Error('unexpected Kodik player host');
    const page=(await fetchFollow(pageUrl, {
      maxBytes: 2 * 1024 * 1024,
      redirectAllowed: (_from, to) => isAllowedKodikHost(to.hostname),
    })).body.toString();
    const opts=[...page.matchAll(/<option\b[^>]*?data-media-id="(\d+)"[^>]*?data-media-hash="([0-9a-f]+)"[^>]*?data-media-type="serial"[^>]*?data-title="([^"]*)"[^>]*?>/g)];
    const typeMatch=(v)=>{ const m=page.match(new RegExp('data-id="'+v+'"[^>]*data-translation-type="([a-z]+)"')); return m?m[1]:'voice'; };
    const seen=new Set(); const out=[];
    for(const o of opts){
      const [_,mid,mhash,title]=o;
      const key=mid+':'+mhash; if(seen.has(key))continue; seen.add(key);
      const vm=o[0].match(/value="(\d+)"/);
      out.push({title:decodeHtmlEntities(title)||'Kodik', type: vm?typeMatch(vm[1]):'voice', link:`//kodikplayer.com/serial/${mid}/${mhash}/720p`});
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
// Какая серия реально выбрана на странице сериала Kodik. На несуществующую
// серию (29-ю из 28 или ещё не вышедшую у этой озвучки) Kodik не отвечает
// ошибкой, а молча выбирает последнюю доступную — и шлюз отдавал её поток.
// Клиенты после последней серии уходили на N+1 и крутили финал по кругу.
function kodikSelectedEpisode(page) {
  // Ищем сам элемент, а не первое упоминание: класс встречается и в CSS страницы.
  const start = page.indexOf('class="serial-series-box"');
  if (start < 0) return null;
  const end = page.indexOf('</select>', start);
  const block = page.slice(start, end < 0 ? undefined : end);
  for (const m of block.matchAll(/<option\b([^>]*)>/g)) {
    if (!/\bselected\b/.test(m[1])) continue;
    const v = m[1].match(/\bvalue="(\d+)"/);
    return v ? Number(v[1]) : null;
  }
  return null;
}
async function handleKodik(link, episode, res) {
  try {
    let pageUrl = link.startsWith('//') ? 'https:' + link : link;
    const parsedPage = assertSafeHttpsUrl(pageUrl);
    if (!isAllowedKodikHost(parsedPage.hostname)) { res.writeHead(400); return res.end('kodik error: недопустимый хост'); }
    if (episode) {
      const sep = pageUrl.includes('?') ? '&' : '?';
      pageUrl += `${sep}season=1&episode=${episode}`;
    }
    const host = parsedPage.hostname;
    const page = (await fetchFollow(pageUrl, {
      maxBytes: 2 * 1024 * 1024,
      redirectAllowed: (_from, to) => isAllowedKodikHost(to.hostname),
    })).body.toString();
    if (episode) {
      const selected = kodikSelectedEpisode(page);
      if (selected != null && selected !== Number(episode)) {
        return jsonRes(res, 404, { error: 'episode not found', lastEpisode: selected });
      }
    }
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
      maxBytes: 2 * 1024 * 1024,
      redirectAllowed: (_from, to) => isAllowedKodikHost(to.hostname),
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
async function anilistCover(id) {
  try {
    const body = JSON.stringify({
      query: 'query($m:Int){Media(idMal:$m,type:ANIME){coverImage{large}}}',
      variables: { m: Number(id) },
    });
    const response = await fetchFollow('https://graphql.anilist.co', {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      maxBytes: 256 * 1024,
    });
    return JSON.parse(response.body.toString())?.data?.Media?.coverImage?.large || null;
  } catch (_) {
    return null;
  }
}
// Shikimori перенёс постеры в новое хранилище (/uploads/poster/animes/…). REST
// /api/animes для таких тайтлов отдаёт заглушку missing_*, а настоящий постер есть
// только в GraphQL (poster.mainUrl). В расписании на 08.10.2026 так было у 87 тайтлов
// из 101: все они шли в медленную очередь Jikan/AniList, часть получала 503 «очередь
// полна» или 404, и на сайте и в приложении оставались пустые карточки.
// Экран просит десятки постеров разом, поэтому id копятся 30 мс и уходят одним
// запросом GraphQL (до 50 id), а запросы идут друг за другом: по запросу на постер
// упёрлись бы в лимит Shikimori (5 в секунду).
const SHIKI_POSTER_BATCH = 50;
const SHIKI_POSTER_WAIT_MS = 30;
let shikiPosterWaiters = new Map(); // id -> [resolve, ...]
let shikiPosterTimer = null;
let shikiPosterChain = Promise.resolve();
function shikimoriPoster(id) {
  return new Promise((resolve) => {
    const waiters = shikiPosterWaiters.get(id);
    if (waiters) waiters.push(resolve);
    else shikiPosterWaiters.set(id, [resolve]);
    if (shikiPosterWaiters.size >= SHIKI_POSTER_BATCH) flushShikimoriPosters();
    else if (!shikiPosterTimer) shikiPosterTimer = setTimeout(flushShikimoriPosters, SHIKI_POSTER_WAIT_MS);
  });
}
function flushShikimoriPosters() {
  clearTimeout(shikiPosterTimer);
  shikiPosterTimer = null;
  const batch = shikiPosterWaiters;
  shikiPosterWaiters = new Map();
  if (!batch.size) return;
  shikiPosterChain = shikiPosterChain
    .then(() => fetchShikimoriPosters([...batch.keys()]))
    .catch(() => new Map())
    .then((found) => {
      for (const [id, waiters] of batch) for (const resolve of waiters) resolve(found.get(id) || null);
    });
}
async function fetchShikimoriPosters(ids) {
  const found = new Map();
  // id приходят из маршрута /alapi/poster/(\d+), но в текст запроса подставляем
  // только цифры — на случай, если функцию когда-нибудь вызовут иначе.
  const safeIds = ids.filter((id) => /^\d{1,10}$/.test(id));
  if (!safeIds.length) return found;
  const body = JSON.stringify({
    query: `{ animes(ids: "${safeIds.join(',')}", limit: ${safeIds.length}) { id poster { mainUrl } } }`,
  });
  const response = await fetchFollow(`${UPSTREAMS.shikimori}/api/graphql`, {
    method: 'POST',
    body,
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
    maxBytes: 512 * 1024,
  });
  const shikiHost = new URL(UPSTREAMS.shikimori).hostname;
  for (const anime of JSON.parse(response.body.toString())?.data?.animes || []) {
    let url;
    try { url = new URL(anime?.poster?.mainUrl); } catch (_) { continue; }
    if (url.hostname === shikiHost && isShikimoriPosterPath(url.pathname)) {
      found.set(String(anime.id), '/alapi/shikimori' + url.pathname);
    }
  }
  return found;
}

let jikanChain = Promise.resolve();
const posterPending = new Map();
const MAX_POSTER_QUEUE = 64;
let fallbackQueued = 0;
function putPosterCache(id, value) {
  if (!posterCache.has(id) && posterCache.size >= 1000) {
    posterCache.delete(posterCache.keys().next().value);
  }
  posterCache.set(id, value);
}
function resolvePoster(id) {
  const hit = posterCache.get(id);
  if (hit && hit.exp > Date.now()) return Promise.resolve(hit.path);
  const pending = posterPending.get(id);
  if (pending) return pending;

  const job = shikimoriPoster(id).then((path) => {
    if (!path) return resolveFallbackPoster(id);
    putPosterCache(id, { path, exp: Date.now() + POSTER_TTL_MS });
    return path;
  });
  posterPending.set(id, job);
  job.then(() => posterPending.delete(id), () => posterPending.delete(id));
  return job;
}
// Запасной путь для тайтлов, которых нет и в GraphQL Shikimori: Jikan, затем AniList.
function resolveFallbackPoster(id) {
  if (fallbackQueued >= MAX_POSTER_QUEUE) return Promise.reject(new Error('poster queue is full'));
  fallbackQueued++;

  const job = jikanChain.then(async () => {
    const h2 = posterCache.get(id);
    if (h2 && h2.exp > Date.now()) return h2.path;
    let posterPath = null;
    try {
      const r = await fetchFollow(`${UPSTREAMS.jikan}/v4/anime/${id}`, { maxBytes: 512 * 1024 });
      const img = JSON.parse(r.body.toString())?.data?.images?.jpg?.large_image_url || null;
      if (img) posterPath = '/alapi/malcdn' + new URL(img).pathname;
    } catch (e) {}
    if (!posterPath) {
      const al = await anilistCover(id);
      if (al) posterPath = '/alapi/anilistcdn' + new URL(al).pathname;
    }
    putPosterCache(id, {
      path: posterPath,
      exp: Date.now() + (posterPath ? POSTER_TTL_MS : 10 * 60 * 1000),
    });
    await new Promise(r => setTimeout(r, 400));
    return posterPath;
  });
  jikanChain = job.catch(() => {});
  job.then(() => fallbackQueued--, () => fallbackQueued--);
  return job;
}
async function handlePoster(id, res) {
  try {
    const posterPath = await resolvePoster(id);
    if (!posterPath) { res.writeHead(404); return res.end('no poster'); }
    res.writeHead(302, { Location: posterPath });
    return res.end();
  } catch (e) {
    const overloaded = e.message === 'poster queue is full';
    res.writeHead(overloaded ? 503 : 502, overloaded ? { 'Retry-After': '10' } : {});
    return res.end('poster error: ' + e.message);
  }
}
function handleAppVersion(res) {
  try { return jsonRes(res, 200, JSON.parse(fs.readFileSync(dataPath('app-version.json'), 'utf8'))); }
  catch (e) { return jsonRes(res, 200, { versionCode: 0 }); }
}
function handleApkDownload(req, res) {
  let versionName = '';
  try {
    const versionData = JSON.parse(fs.readFileSync(dataPath('app-version.json'), 'utf8'));
    if (versionData && versionData.versionName) {
      versionName = versionData.versionName.startsWith('v') ? versionData.versionName : `v${versionData.versionName}`;
    }
  } catch (_) {}
  const downloadFilename = versionName ? `AniPulse-${versionName}.apk` : 'AniPulse-latest.apk';
  const versionedApkPath = dataPath(downloadFilename);
  const latestApkPath = dataPath('AniPulse-latest.apk');
  const apkPath = fs.existsSync(versionedApkPath) ? versionedApkPath : latestApkPath;

  fs.stat(apkPath, (error, stat) => {
    if (error || !stat.isFile()) { res.writeHead(404); return res.end('no apk'); }
    if (req.headers['x-anipulse-monitor'] !== '1') {
      try { analyticsStore.recordDownload(dataPath('analytics.json')); } catch (analyticsError) {
        console.error('analytics download write failed:', analyticsError.code || analyticsError.message);
      }
    }
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': `attachment; filename="${downloadFilename}"`,
      'Content-Length': stat.size,
    });
    const stream = fs.createReadStream(apkPath);
    stream.on('error', () => {
      if (!res.headersSent) res.writeHead(500);
      res.end();
    });
    stream.pipe(res);
  });
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
const RATE_LIMIT_MAX_KEYS = 5000;
const RATE_LIMIT_MAX_WINDOW_MS = 24 * 60 * 60 * 1000; // самое длинное окно из используемых (жалобы: 10/сутки)
// Раньше при переполнении карта очищалась целиком (map.clear()). Это обнуляло счётчики
// попыток входа сразу для всех: ботнету достаточно было засорить карту 5000 адресов,
// чтобы открыть себе окно на перебор паролей. Теперь вытесняем только неактуальное.
function evictRateLimit(map, now) {
  for (const [key, hits] of map) {
    if (!hits.length || now - hits[hits.length - 1] >= RATE_LIMIT_MAX_WINDOW_MS) map.delete(key);
  }
  if (map.size <= RATE_LIMIT_MAX_KEYS) return;
  // Вытесняем в первую очередь записи с наименьшим числом попыток. Вытеснение
  // «самых старых» здесь не годится: запись атакующего создаётся раньше флуда,
  // и он вытолкнул бы собственную блокировку. Запись, которая кого-то реально
  // тормозит, всегда имеет много отметок — и переживает чистку.
  const byRisk = [...map.entries()].sort((a, b) =>
    a[1].length - b[1].length || a[1][a[1].length - 1] - b[1][b[1].length - 1]);
  for (const [key] of byRisk.slice(0, map.size - RATE_LIMIT_MAX_KEYS)) map.delete(key);
}
function tooMany(map, key, limit, windowMs) {
  const now = Date.now();
  const arr = (map.get(key) || []).filter(t => now - t < windowMs);
  if (arr.length >= limit) { map.set(key, arr); return true; }
  arr.push(now); map.set(key, arr);
  if (map.size > RATE_LIMIT_MAX_KEYS) evictRateLimit(map, now);
  return false;
}
const LOGIN_FAIL_LIMIT = 10;
const LOGIN_FAIL_WINDOW_MS = 15 * 60 * 1000;
/** Сколько отметок по ключу за окно — без добавления новой. */
function recentHits(map, key, windowMs) {
  const now = Date.now();
  return (map.get(key) || []).filter(t => now - t < windowMs).length;
}
function clientIp(req) {
  // Caddy ДОПИСЫВАЕТ реальный IP в конец X-Forwarded-For, не удаляя то, что прислал клиент —
  // поэтому доверяем ПОСЛЕДНЕМУ элементу, а не первому (иначе клиент подделывает [0] и обходит rate-limit).
  const xff = (req.headers['x-forwarded-for'] || '').split(',').map(s => s.trim()).filter(Boolean);
  return xff[xff.length - 1] || req.socket.remoteAddress || 'unknown';
}
// Как и loadJson ниже: отсутствие файла — норма (дефолт), битый файл — throw,
// чтобы следующий saveUsers не затёр всю базу аккаунтов пустым дефолтом.
// verifyToken дёргает loadUsers на КАЖДЫЙ запрос к API, включая неаутентифицированные:
// без кеша любой запрос с мусорным Bearer заставлял сервер прочитать и распарсить
// весь users.json. Кешируем по mtime+размеру и отдаём копию — вызывающий код
// мутирует результат перед saveUsers, поэтому общий объект наружу отдавать нельзя.
let _usersCache = null; // { key, db }
function invalidateUsersCache() { _usersCache = null; }
function loadUsers() {
  let stat;
  try { stat = fs.statSync(USERS_FILE); } catch (e) { return { seq: 0, users: [] }; }
  const key = `${stat.mtimeMs}:${stat.size}`;
  if (_usersCache && _usersCache.key === key) return structuredClone(_usersCache.db);
  let raw;
  try { raw = fs.readFileSync(USERS_FILE, 'utf8'); } catch (e) { return { seq: 0, users: [] }; }
  let db;
  try { db = JSON.parse(raw); } catch (e) { throw new Error(`corrupt users store: ${e.message}`); }
  _usersCache = { key, db };
  return structuredClone(db);
}
function saveUsers(db) {
  fs.writeFileSync(USERS_FILE + '.tmp', JSON.stringify(db, null, 1));
  fs.renameSync(USERS_FILE + '.tmp', USERS_FILE);
  invalidateUsersCache(); // не полагаемся на разрешение mtime: сбрасываем явно
}
function hashPassword(pw, salt) {
  salt = salt || crypto.randomBytes(16).toString('hex');
  return salt + ':' + crypto.scryptSync(pw, salt, 32).toString('hex');
}
function checkPassword(pw, stored) {
  // Битая запись в базе раньше роняла запрос в 500: timingSafeEqual бросает
  // исключение на буферах разной длины.
  if (typeof stored !== 'string' || !stored.includes(':')) return false;
  const [salt] = stored.split(':');
  const a = Buffer.from(hashPassword(pw, salt)), b = Buffer.from(stored);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function sameCode(given, expected) {
  if (typeof expected !== 'string' || !expected) return false;
  const a = Buffer.from(String(given || '')), b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
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
function sendMail(to, subject, text, html) {
  try {
    if (!_mailer) {
      const cfg = JSON.parse(fs.readFileSync(SMTP_FILE, 'utf8'));
      _mailer = require('nodemailer').createTransport({ host: 'smtp.yandex.ru', port: 465, secure: true, auth: { user: cfg.user, pass: cfg.pass } });
      _mailer._from = cfg.user;
    }
    _mailer.sendMail({
      from: 'AniPulse <' + _mailer._from + '>',
      to,
      subject,
      text,
      html,
      headers: { 'X-Entity-Ref-ID': crypto.randomUUID() },
    }, (error) => {
      if (error) console.error('SMTP delivery failed:', error.responseCode || error.code || 'unknown');
    });
  } catch (e) {}
}
const BUGREPORT_FILE = dataPath('bugreport.json');
function bugReportTo() {
  try { return JSON.parse(fs.readFileSync(BUGREPORT_FILE, 'utf8')).to; } catch (e) {}
  try { return JSON.parse(fs.readFileSync(SMTP_FILE, 'utf8')).user; } catch (e) { return null; }
}

const ANALYTICS_FILE = dataPath('analytics.json');

async function handleAnalyticsHeartbeat(req, res) {
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  if (tooMany(ipHits, 'analytics:' + clientIp(req), 180, 60 * 60 * 1000)) return jsonRes(res, 429, { error: 'rate limit' });
  const body = await readBody(req, 4096);
  const user = authUserEarly(req);
  const result = analyticsStore.recordHeartbeat(ANALYTICS_FILE, body, user && user.id);
  if (!result) return jsonRes(res, 400, { error: 'invalid install id' });
  return jsonRes(res, 200, result);
}

function analyticsEmailText(value) {
  const line = (title, period) =>
    `${title}: ${period.active} активных (${period.android} приложение, ${period.web} сайт), ` +
    `${period.firstOpens} первых запусков, ${period.downloads} скачиваний, ` +
    `${period.averageSessionMinutes} мин. на пользователя`;
  return [
    'Еженедельная статистика AniPulse', '',
    `Онлайн сейчас: ${value.online} (приложение ${value.onlineAndroid}, сайт ${value.onlineWeb})`,
    line('Сегодня', value.today),
    line('За 7 дней', value.last7Days),
    line('За 30 дней', value.last30Days), '',
    `Всего зафиксировано установок: ${value.totalInstalls}`,
    `Всего скачиваний APK с сайта: ${value.totalDownloads}`, '',
    'Статистика RuStore учитывается отдельно в кабинете RuStore.',
  ].join('\n');
}

function maybeSendWeeklyAnalytics() {
  const now = new Date();
  if (now.getUTCDay() !== 1 || now.getUTCHours() < 6) return;
  let recipient;
  try {
    const smtp = JSON.parse(fs.readFileSync(SMTP_FILE, 'utf8'));
    recipient = smtp.analyticsTo || smtp.user;
  } catch (_) { return; }
  if (!recipient) return;
  try {
    if (!analyticsStore.claimWeeklyReport(ANALYTICS_FILE, now.getTime())) return;
    sendMail(recipient, 'Еженедельная статистика AniPulse', analyticsEmailText(analyticsStore.summary(ANALYTICS_FILE, now.getTime())));
  } catch (error) {
    console.error('weekly analytics failed:', error.code || error.message);
  }
}

setTimeout(maybeSendWeeklyAnalytics, 30_000).unref();
setInterval(maybeSendWeeklyAnalytics, 60 * 60 * 1000).unref();

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
    user ? 'Аккаунт ID: ' + user.id : 'Аккаунт: гость',
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
  const code = u.verifyCode;
  sendMail(
    u.email,
    'Код подтверждения AniPulse',
    'Ваш код подтверждения AniPulse: ' + code + '\n\nКод действует 15 минут.',
    '<!doctype html><html><body style="margin:0;background:#f5f5f7;font-family:Arial,sans-serif;color:#18181b">' +
      '<div style="max-width:520px;margin:32px auto;padding:28px;background:#fff;border-radius:16px">' +
      '<h1 style="margin:0 0 16px;font-size:24px">Подтверждение почты</h1>' +
      '<p style="margin:0 0 20px;line-height:1.5">Введите этот код в приложении AniPulse:</p>' +
      '<div style="font-size:34px;font-weight:700;letter-spacing:8px;text-align:center;padding:18px;' +
        'background:#f1f1f5;border-radius:12px">' + code + '</div>' +
      '<p style="margin:20px 0 0;color:#71717a;font-size:14px;line-height:1.5">' +
        'Код действует 15 минут. Если вы не запрашивали его, просто проигнорируйте письмо.</p>' +
      '</div></body></html>',
  );
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
    // Ответ «Почта уже зарегистрирована» по своей природе подтверждает наличие
    // аккаунта. Убрать его нельзя, не сломав контракт с Android-клиентом, поэтому
    // перебор почт душим отдельным часовым лимитом поверх минутного.
    if (path === 'register' && tooMany(ipHits, 'reg:' + clientIp(req), 10, 60 * 60 * 1000)) {
      return jsonRes(res, 429, { error: 'Слишком много регистраций с этого адреса, попробуйте позже' });
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
    // Лимит по IP не спасает от перебора одного аккаунта с множества адресов.
    // Считаем неудачи и по самому логину — существует он или нет, чтобы ответ
    // не выдавал наличие аккаунта.
    const failKey = 'login-fail:' + login;
    if (recentHits(userHits, failKey, LOGIN_FAIL_WINDOW_MS) >= LOGIN_FAIL_LIMIT) {
      return jsonRes(res, 429, { error: 'Слишком много неудачных попыток входа, попробуйте через 15 минут' });
    }
    const db = loadUsers();
    const user = db.users.find(u => u.email === login || u.nick.toLowerCase() === login);
    if (!user || !checkPassword(String(b.password), user.pass)) {
      tooMany(userHits, failKey, Infinity, LOGIN_FAIL_WINDOW_MS);
      return jsonRes(res, 401, { error: 'Неверный логин или пароль' });
    }
    userHits.delete(failKey);
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
    if (!b || !sameCode(b.code, u.verifyCode) || (u.verifyExp || 0) < Date.now()) {
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
    // Не больше трёх кодов на почту в час: иначе жертву можно засыпать письмами,
    // а каждый новый код раньше обнулял счётчик попыток и открывал перебор заново.
    if (tooMany(userHits, 'fg-mail:' + email, 3, 60 * 60 * 1000)) return jsonRes(res, 200, { ok: true });
    const db = loadUsers();
    const u = db.users.find(x => x.email === email);
    if (u) {
      // Попытки считаются в часовом окне и переживают перевыпуск кода.
      if (!u.resetWindowStart || Date.now() - u.resetWindowStart > 60 * 60 * 1000) {
        u.resetWindowStart = Date.now();
        u.resetAttempts = 0;
      }
      u.resetCode = String(crypto.randomInt(100000, 1000000));
      u.resetExp = Date.now() + 15 * 60 * 1000;
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
    if (u && (u.resetAttempts || 0) >= 5) { delete u.resetCode; delete u.resetExp; saveUsers(db); return jsonRes(res, 429, { error: 'Слишком много попыток — попробуйте через час' }); }
    if (!u || !sameCode(code, u.resetCode) || (u.resetExp || 0) < Date.now()) {
      if (u) { u.resetAttempts = (u.resetAttempts || 0) + 1; saveUsers(db); }
      return jsonRes(res, 400, { error: 'Неверный или просроченный код' });
    }
    u.pass = hashPassword(password);
    delete u.resetCode; delete u.resetExp; delete u.resetAttempts; delete u.resetWindowStart;
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
    return jsonRes(res, 200, {
      userId: user.id,
      nick: user.nick,
      email: user.email,
      avatar: avatarOf(user),
      avatarRev: user.avatarRev || 0,
      linked: Object.keys(user.linked || {}),
      emailVerified: user.emailVerified !== false,
      admin: !!user.admin,
    });
  }
  jsonRes(res, 404, { error: 'not found' });
}


// ===== Соцчасть: общий чат, комментарии к тайтлам, свой рейтинг 1-10 =====
const CHAT_FILE = dataPath('chat.json');
const COMMENTS_FILE = dataPath('comments.json');
const RATINGS_FILE = dataPath('ratings.json');
const BLOCKS_FILE = dataPath('blocks.json');
const REPORTS_FILE = dataPath('reports.json');
const SYNC_FILE = dataPath('sync.json');
// «Файла нет» → дефолт (норма при первом запуске). «Файл есть, но не парсится» → throw:
// иначе следующий saveJson молча затёр бы всё хранилище дефолтом (полная потеря чата/ЛС и т.п.).
// throw ловится общим обработчиком route() → клиент получит 500, данные останутся нетронуты.
function loadJson(f, def) {
  let raw;
  try { raw = fs.readFileSync(f, 'utf8'); } catch (e) { return def; }
  try { return JSON.parse(raw); } catch (e) { throw new Error(`corrupt json store ${f}: ${e.message}`); }
}
function saveJson(f, obj) { fs.writeFileSync(f + '.tmp', JSON.stringify(obj)); fs.renameSync(f + '.tmp', f); }
const REPORT_RETENTION_MS = 3 * 365 * 24 * 60 * 60 * 1000;
function loadReports() {
  const all = loadJson(REPORTS_FILE, { seq: 0, items: [] });
  const cutoff = Date.now() - REPORT_RETENTION_MS;
  const retained = (all.items || []).filter(report => !Number(report.createdAt) || Number(report.createdAt) >= cutoff);
  if (retained.length !== (all.items || []).length) {
    all.items = retained;
    saveJson(REPORTS_FILE, all);
  }
  return all;
}
try { loadReports(); } catch (e) { console.error('report retention cleanup failed:', e.message); }
setInterval(() => {
  try { loadReports(); } catch (e) { console.error('report retention cleanup failed:', e.message); }
}, 24 * 60 * 60 * 1000).unref();
function authUser(req) {
  const userId = verifyToken((req.headers['authorization'] || '').replace('Bearer ', ''));
  if (!userId) return null;
  const u = loadUsers().users.find(x => x.id === userId) || null;
  if (u) touchLastSeen(u);
  return u;
}
function sanitizeText(t, max) { return String(t || '').replace(/\s+/g, ' ').trim().slice(0, max); }

function sanitizeSyncProgress(value) {
  const animeId = Math.trunc(Number(value && value.animeId));
  const episode = Math.trunc(Number(value && value.episode));
  if (!Number.isSafeInteger(animeId) || animeId < 1 || !Number.isSafeInteger(episode) || episode < 1 || episode > 100000) return null;
  const durationMs = Math.min(24 * 60 * 60 * 1000, Math.max(0, Math.trunc(Number(value.durationMs) || 0)));
  const positionMs = Math.min(durationMs || 24 * 60 * 60 * 1000, Math.max(0, Math.trunc(Number(value.positionMs) || 0)));
  const updatedAt = Math.min(Date.now() + 5 * 60 * 1000, Math.max(1, Math.trunc(Number(value.updatedAt) || Date.now())));
  return {
    animeId, episode, positionMs, durationMs, watched: value.watched === true,
    dubId: sanitizeText(value.dubId, 120) || null, title: sanitizeText(value.title, 200),
    posterId: Math.max(0, Math.trunc(Number(value.posterId) || animeId)),
    totalEpisodes: Math.min(100000, Math.max(0, Math.trunc(Number(value.totalEpisodes) || 0))), updatedAt,
    // Отметка удаления, как у избранного. Без неё удаление из «Продолжить
    // просмотр» не переживало синхронизацию: клиент стирал запись у себя, сервер
    // про это не знал и возвращал тайтл обратно при следующем заходе.
    deleted: value.deleted === true,
  };
}
function sanitizeSyncFavorite(value) {
  const animeId = Math.trunc(Number(value && value.animeId));
  if (!Number.isSafeInteger(animeId) || animeId < 1) return null;
  const updatedAt = Math.min(Date.now() + 5 * 60 * 1000, Math.max(1, Math.trunc(Number(value.updatedAt) || Date.now())));
  const status = ['none', 'watching', 'planned', 'completed'].includes(value.status) ? value.status : 'none';
  return {
    animeId, title: sanitizeText(value.title, 200), score: sanitizeText(value.score, 20) || null,
    status, updatedAt, deleted: value.deleted === true,
  };
}
function syncResponse(entry) {
  return {
    progress: Object.values(entry.progress || {}).slice(-5000),
    favorites: Object.values(entry.favorites || {}).slice(-2000),
  };
}
async function handleSync(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'auth' });
  const readEntry = (store) => {
    store.users = store.users || {};
    const e = store.users[user.id] || { progress: {}, favorites: {} };
    e.progress = e.progress || {}; e.favorites = e.favorites || {};
    return e;
  };
  if (req.method === 'GET') return jsonRes(res, 200, syncResponse(readEntry(loadJson(SYNC_FILE, { users: {} }))));
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  if (tooMany(userHits, 'sync:' + user.id, 120, 60 * 60 * 1000)) return jsonRes(res, 429, { error: 'rate limit' });
  const body = await readBody(req);
  // Снапшот берём ПОСЛЕ await: взятый до него затирал бы записи параллельных
  // синхронизаций других аккаунтов целиком (lost update по всему файлу).
  const all = loadJson(SYNC_FILE, { users: {} });
  const entry = readEntry(all);
  for (const raw of Array.isArray(body && body.progress) ? body.progress.slice(0, 5000) : []) {
    const item = sanitizeSyncProgress(raw); if (!item) continue;
    const key = item.animeId + ':' + item.episode, old = entry.progress[key];
    if (!old || Number(item.updatedAt) >= Number(old.updatedAt || 0)) entry.progress[key] = item;
  }
  for (const raw of Array.isArray(body && body.favorites) ? body.favorites.slice(0, 2000) : []) {
    const item = sanitizeSyncFavorite(raw); if (!item) continue;
    const key = String(item.animeId), old = entry.favorites[key];
    if (!old || Number(item.updatedAt) >= Number(old.updatedAt || 0)) entry.favorites[key] = item;
  }
  all.users[user.id] = entry;
  saveJson(SYNC_FILE, all);
  leaderboardCache = { exp: 0, data: null };
  return jsonRes(res, 200, syncResponse(entry));
}

// ===== Статистика просмотров, ранги и Таблица лидеров =====
function getLevelInfo(episodesCount) {
  if (episodesCount >= 500) return { level: 6, title: 'Легенда', badge: '⚡💎', nextTarget: null };
  if (episodesCount >= 300) return { level: 5, title: 'Сенсей', badge: '👑', nextTarget: 500 };
  if (episodesCount >= 150) return { level: 4, title: 'Отаку', badge: '⭐', nextTarget: 300 };
  if (episodesCount >= 50)  return { level: 3, title: 'Анимешник', badge: '🔥', nextTarget: 150 };
  if (episodesCount >= 10)  return { level: 2, title: 'Любитель', badge: '🍿', nextTarget: 50 };
  return { level: 1, title: 'Новичок', badge: '🌱', nextTarget: 10 };
}

function calculateUserStats(userId, syncEntry) {
  const progressItems = Object.values(syncEntry && syncEntry.progress || {});
  const favoriteItems = Object.values(syncEntry && syncEntry.favorites || {});

  let watchedEpisodesCount = 0;
  let totalWatchMs = 0;
  for (const p of progressItems) {
    if (p && !p.deleted) {
      if (p.watched === true || (p.durationMs > 0 && p.positionMs >= 0.8 * p.durationMs)) {
        watchedEpisodesCount++;
      }
      if (p.positionMs > 0) {
        totalWatchMs += Math.min(p.positionMs, p.durationMs || p.positionMs);
      }
    }
  }

  let completedTitlesCount = 0;
  for (const f of favoriteItems) {
    if (f && !f.deleted && f.status === 'completed') {
      completedTitlesCount++;
    }
  }

  const watchMinutes = Math.round(totalWatchMs / 60000);
  const watchHours = +(watchMinutes / 60).toFixed(1);
  const levelInfo = getLevelInfo(watchedEpisodesCount);

  return {
    episodesWatched: watchedEpisodesCount,
    watchMinutes,
    watchHours,
    completedTitles: completedTitlesCount,
    level: levelInfo.level,
    levelTitle: levelInfo.title,
    badge: levelInfo.badge,
    nextTarget: levelInfo.nextTarget,
  };
}

let leaderboardCache = { exp: 0, data: null };

function generateLeaderboard() {
  const syncDb = loadJson(SYNC_FILE, { users: {} });
  const usersDb = loadUsers().users || [];
  const userMap = new Map();
  for (const u of usersDb) {
    if (u && u.id && !u.banned) {
      userMap.set(String(u.id), u);
    }
  }

  const list = [];
  for (const [userIdStr, entry] of Object.entries(syncDb.users || {})) {
    const user = userMap.get(userIdStr);
    if (!user) continue;
    const stats = calculateUserStats(user.id, entry);
    if (stats.episodesWatched > 0 || stats.watchMinutes > 0) {
      list.push({
        userId: user.id,
        nick: user.nick,
        avatar: user.avatar !== undefined ? user.avatar : 0,
        avatarCustom: avatarOf(user) === -1,
        episodesWatched: stats.episodesWatched,
        watchHours: stats.watchHours,
        watchMinutes: stats.watchMinutes,
        completedTitles: stats.completedTitles,
        level: stats.level,
        levelTitle: stats.levelTitle,
        badge: stats.badge,
      });
    }
  }

  list.sort((a, b) => b.episodesWatched - a.episodesWatched || b.watchMinutes - a.watchMinutes);

  for (let i = 0; i < list.length; i++) {
    list[i].rank = i + 1;
  }

  return list;
}

function getLeaderboardData() {
  const now = Date.now();
  if (leaderboardCache.data && leaderboardCache.exp > now) {
    return leaderboardCache.data;
  }
  const allRanked = generateLeaderboard();
  leaderboardCache = {
    exp: now + 60 * 1000,
    data: allRanked,
  };
  return allRanked;
}

async function handleLeaderboard(req, res) {
  const currentUser = authUser(req);
  const allRanked = getLeaderboardData();
  const top50 = allRanked.slice(0, 50);

  let myRank = null;
  if (currentUser) {
    const found = allRanked.find(x => x.userId === currentUser.id);
    if (found) {
      myRank = found;
    } else {
      const syncDb = loadJson(SYNC_FILE, { users: {} });
      const stats = calculateUserStats(currentUser.id, syncDb.users && syncDb.users[currentUser.id]);
      myRank = {
        rank: allRanked.length + 1,
        userId: currentUser.id,
        nick: currentUser.nick,
        avatar: currentUser.avatar !== undefined ? currentUser.avatar : 0,
        avatarCustom: avatarOf(currentUser) === -1,
        episodesWatched: stats.episodesWatched,
        watchHours: stats.watchHours,
        watchMinutes: stats.watchMinutes,
        completedTitles: stats.completedTitles,
        level: stats.level,
        levelTitle: stats.levelTitle,
        badge: stats.badge,
      };
    }
  }

  return jsonRes(res, 200, {
    leaderboard: top50,
    totalParticipants: allRanked.length,
    myRank,
  });
}

async function handleMyStats(req, res) {
  const currentUser = authUser(req);
  if (!currentUser) return jsonRes(res, 401, { error: 'auth' });
  const syncDb = loadJson(SYNC_FILE, { users: {} });
  const entry = syncDb.users && syncDb.users[currentUser.id];
  const stats = calculateUserStats(currentUser.id, entry);
  const allRanked = getLeaderboardData();
  const rankEntry = allRanked.find(x => x.userId === currentUser.id);
  const rank = rankEntry ? rankEntry.rank : (allRanked.length + 1);

  return jsonRes(res, 200, {
    userId: currentUser.id,
    nick: currentUser.nick,
    avatar: currentUser.avatar !== undefined ? currentUser.avatar : 0,
    avatarCustom: avatarOf(currentUser) === -1,
    rank,
    totalParticipants: allRanked.length,
    ...stats,
  });
}

function blocksDb() { return loadJson(BLOCKS_FILE, {}); }
function blockedIds(db, userId) { return (db[String(userId)] || []).map(Number); }
function hasBlocked(db, userId, targetId) { return blockedIds(db, userId).includes(Number(targetId)); }
function blockedEither(db, a, b) { return hasBlocked(db, a, b) || hasBlocked(db, b, a); }
function bilateralHiddenIds(db, viewerId) {
  const hidden = new Set(blockedIds(db, viewerId));
  for (const [otherId, ids] of Object.entries(db)) {
    if ((ids || []).map(Number).includes(Number(viewerId))) hidden.add(Number(otherId));
  }
  return hidden;
}
function userIdByNick(nick) {
  const u = loadUsers().users.find(x => x.nick && x.nick.toLowerCase() === String(nick || '').toLowerCase());
  return u ? u.id : null;
}

function deleteAccountData(user) {
  const db = loadUsers();
  db.users = db.users.filter(u => u.id !== user.id);

  const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
  chat.messages = chat.messages.filter(m => m.userId !== user.id);
  for (const message of chat.messages) {
    if (message.replyTo && (
      Number(message.replyTo.userId) === user.id ||
      String(message.replyTo.nick || '').toLowerCase() === String(user.nick).toLowerCase()
    )) {
      message.replyTo.userId = null;
      message.replyTo.nick = 'Удалённый аккаунт';
      message.replyTo.text = 'Сообщение удалено';
    }
  }
  saveJson(CHAT_FILE, chat);

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
    value.friends = (value.friends || []).filter(id => Number(id) !== user.id);
    value.incoming = (value.incoming || []).filter(id => Number(id) !== user.id);
  }
  saveJson(FRIENDS_FILE, friends);

  const notifications = loadJson(NOTIF_FILE, { seq: 0, byUser: {} });
  delete notifications.byUser[user.id];
  for (const ownerId of Object.keys(notifications.byUser)) {
    notifications.byUser[ownerId] = (notifications.byUser[ownerId] || []).filter(n =>
      Number(n.fromUserId) !== user.id &&
      String(n.from || '').toLowerCase() !== String(user.nick).toLowerCase()
    );
  }
  saveJson(NOTIF_FILE, notifications);

  const blocks = blocksDb(); delete blocks[user.id];
  for (const key of Object.keys(blocks)) blocks[key] = blockedIds(blocks, key).filter(id => id !== user.id);
  saveJson(BLOCKS_FILE, blocks);

  const sync = loadJson(SYNC_FILE, { users: {} });
  if (sync.users) delete sync.users[user.id];
  saveJson(SYNC_FILE, sync);
  analyticsStore.anonymizeUser(ANALYTICS_FILE, user.id);

  const reports = loadReports();
  for (const r of reports.items) {
    if (r.reporterId === user.id) { r.reporterId = null; r.reporterNick = 'Удалённый аккаунт'; }
    const targetsUser = reportedUserId(r) === user.id ||
      (r.targetNick && r.targetNick.toLowerCase() === String(user.nick).toLowerCase());
    if (targetsUser) {
      r.targetUserId = null;
      r.targetNick = 'Удалённый аккаунт';
      if (String(r.targetId || '').toLowerCase() === String(user.nick).toLowerCase()) {
        r.targetId = 'Удалённый аккаунт';
      }
      if (r.snapshot) {
        r.snapshot.userId = null;
        if (String(r.snapshot.nick || '').toLowerCase() === String(user.nick).toLowerCase()) {
          r.snapshot.nick = 'Удалённый аккаунт';
        }
        if (String(r.snapshot.from || '').toLowerCase() === String(user.nick).toLowerCase()) {
          r.snapshot.from = 'Удалённый аккаунт';
        }
        if (String(r.snapshot.to || '').toLowerCase() === String(user.nick).toLowerCase()) {
          r.snapshot.to = 'Удалённый аккаунт';
        }
      }
    }
    if (String(r.resolvedBy || '').toLowerCase() === String(user.nick).toLowerCase()) {
      r.resolvedBy = 'Удалённый аккаунт';
    }
  }
  saveJson(REPORTS_FILE, reports);

  const oauth = oauthStateAll();
  for (const key of Object.keys(oauth.codes)) {
    if (Number(oauth.codes[key]?.userId) === user.id) delete oauth.codes[key];
  }
  oauthStateSweepSave(oauth);
  lastSeenMem.delete(user.id);
  for (const key of [...userHits.keys()]) if (key.endsWith(':' + user.id)) userHits.delete(key);

  // Save the account removal last. If cleanup of any auxiliary store fails, the
  // token remains usable so the user can retry instead of being left half-deleted.
  saveUsers(db);
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

function removeInteractionNotifications(a, b) {
  const all = loadJson(NOTIF_FILE, { seq: 0, byUser: {} });
  const users = loadUsers().users;
  const senderId = n => Number(n.fromUserId)
    || users.find(u => u.nick && u.nick.toLowerCase() === String(n.from || '').toLowerCase())?.id
    || 0;
  for (const [ownerId, otherId] of [[a, b], [b, a]]) {
    const list = all.byUser[ownerId] || [];
    all.byUser[ownerId] = list.filter(n =>
      n.type === 'system' || senderId(n) !== Number(otherId)
    );
  }
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
    if (!u || u.id === fromUser.id || isBanned(u) || blockedEither(blocks, u.id, fromUser.id)) continue;
    addNotification(u.id, {
      type: 'mention', from: fromUser.nick, fromUserId: fromUser.id,
      text: String(text).slice(0, 200), source,
    });
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
    const users = loadUsers().users;
    const blocks = blocksDb();
    const visible = list.filter(n => {
      if (n.type === 'system') return true;
      const fromUserId = Number(n.fromUserId)
        || users.find(u => u.nick && u.nick.toLowerCase() === String(n.from || '').toLowerCase())?.id
        || 0;
      return !fromUserId || !blockedEither(blocks, user.id, fromUserId);
    });
    return jsonRes(res, 200, visible.filter(n => n.id > after).slice(-50));
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
    const friendIds = new Set(friendsOf(friendsDb(), user.id).friends.map(Number));
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
        withUserId: other ? other.id : 0,
        withNick: other ? other.nick : '?',
        withAvatar: other ? avatarOf(other) : 0,
        withAvatarRev: other ? (other.avatarRev || 0) : 0,
        withOnline: other && friendIds.has(otherId) ? isOnline(other) : false,
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
    if (isBanned(other)) return jsonRes(res, 403, { error: 'Аккаунт получателя временно недоступен' });
    if (blockedEither(blocksDb(), user.id, other.id)) return jsonRes(res, 403, { error: 'Переписка недоступна: один из вас заблокировал другого' });
    const all = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
    const key = dmKey(user.id, other.id);
    const msg = { id: ++all.seq, from: user.nick, fromAvatar: avatarOf(user), to: other.nick, text, at: Date.now() };
    const list = all.threads[key] || [];
    list.push(msg);
    all.threads[key] = list.slice(-500);
    saveJson(DM_FILE, all);
    addNotification(other.id, {
      type: 'dm', from: user.nick, fromUserId: user.id, text: String(text).slice(0, 200),
    });
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

function publicUser(u, { showPresence = false } = {}) {
  const banned = isBanned(u);
  return {
    userId: u.id, nick: u.nick, avatar: banned ? 0 : avatarOf(u),
    avatarRev: banned ? 0 : (u.avatarRev || 0), bio: banned ? '' : (u.bio || ''),
    createdAt: u.createdAt || null, lastSeen: null, online: showPresence && !banned && isOnline(u),
    favoriteGenre: banned ? null : (u.favoriteGenre || null), stats: u.stats || null,
    restricted: banned,
  };
}

async function handleUserCard(req, res) {
  const nick = decodeURIComponent(String((req.url.match(/[?&]nick=([^&]+)/) || [])[1] || ''));
  const db = loadUsers();
  const u = db.users.find(x => x.nick && x.nick.toLowerCase() === nick.toLowerCase());
  if (!u) return jsonRes(res, 404, { error: 'Пользователь не найден' });
  const me = authUser(req);
  const blocks = blocksDb();
  if (me && me.id !== u.id && blockedEither(blocks, me.id, u.id)) {
    return jsonRes(res, 200, {
      userId: u.id,
      nick: u.nick,
      avatar: 0,
      avatarRev: 0,
      bio: '',
      lastSeen: null,
      online: false,
      friendState: 'none',
      blocked: hasBlocked(blocks, me.id, u.id),
      blockedByTarget: hasBlocked(blocks, u.id, me.id),
      restricted: true,
    });
  }
  let areFriends = false;
  if (me && me.id !== u.id) {
    const fdb = friendsDb();
    areFriends = friendsOf(fdb, me.id).friends.includes(u.id);
  }
  const out = publicUser(u, { showPresence: !!me && (me.id === u.id || areFriends) });
  // активность в соцчасти
  const comments = loadJson(COMMENTS_FILE, {});
  out.commentsCount = Object.values(comments).reduce((a, list) => a + list.filter(c => c.userId === u.id).length, 0);
  const ratings = loadJson(RATINGS_FILE, {});
  out.ratingsCount = Object.values(ratings).filter(v => v[u.id] != null).length;
  // отношения с запрашивающим
  if (me && me.id !== u.id) {
    const fdb = friendsDb();
    const mine = friendsOf(fdb, me.id), theirs = friendsOf(fdb, u.id);
    out.friendState = mine.friends.includes(u.id) ? 'friends'
      : mine.incoming.includes(u.id) ? 'incoming'
      : theirs.incoming.includes(me.id) ? 'outgoing' : 'none';
    out.blocked = false;
    out.blockedByTarget = false;
  } else if (me) out.friendState = 'self';
  return jsonRes(res, 200, out);
}

async function handleProfileUpdate(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'auth' });
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  if (isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
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
    const hidden = bilateralHiddenIds(blocksDb(), user.id);
    const toCard = id => {
      const u = db.users.find(x => x.id === id);
      return u && !hidden.has(Number(id)) ? publicUser(u, { showPresence: true }) : null;
    };
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
    const isContactAction = req.url.startsWith('/alapi/friends/add')
      || req.url.startsWith('/alapi/friends/accept');
    if (isContactAction && isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
    if (isContactAction && isBanned(other)) {
      return jsonRes(res, 403, { error: 'Аккаунт пользователя временно недоступен' });
    }
    if (isContactAction && blockedEither(blocksDb(), user.id, other.id)) {
      return jsonRes(res, 403, { error: 'Взаимодействие недоступно из-за блокировки' });
    }
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
        addNotification(other.id, {
          type: 'friend_accept', from: user.nick, fromUserId: user.id, text: 'Теперь вы друзья!',
        });
        return jsonRes(res, 200, { state: 'friends' });
      }
      if (!theirs.incoming.includes(user.id)) {
        if (tooMany(userHits, 'fr:' + user.id, 10, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро' });
        theirs.incoming.push(user.id);
        saveJson(FRIENDS_FILE, fdb);
        addNotification(other.id, {
          type: 'friend_request', from: user.nick, fromUserId: user.id,
          text: 'Хочет добавить вас в друзья',
        });
      }
      return jsonRes(res, 200, { state: 'outgoing' });
    }
    if (req.url.startsWith('/alapi/friends/accept')) {
      if (!mine.incoming.includes(other.id)) return jsonRes(res, 400, { error: 'Нет заявки' });
      mine.incoming = mine.incoming.filter(i => i !== other.id);
      addOnce(mine.friends, other.id); addOnce(theirs.friends, user.id);
      theirs.incoming = theirs.incoming.filter(i => i !== user.id);
      saveJson(FRIENDS_FILE, fdb);
      addNotification(other.id, {
        type: 'friend_accept', from: user.nick, fromUserId: user.id,
        text: 'Принял(а) вашу заявку — теперь вы друзья!',
      });
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

function removeReportedContent(report) {
  const targetId = Number(report.targetId) || 0;
  if (report.type === 'chat') {
    const chat = loadJson(CHAT_FILE, { seq: 0, messages: [] });
    const before = chat.messages.length;
    chat.messages = chat.messages.filter(m => m.id !== targetId);
    saveJson(CHAT_FILE, chat);
    return before - chat.messages.length;
  }
  if (report.type === 'comment') {
    const animeId = String(report.animeId || '');
    const all = loadJson(COMMENTS_FILE, {});
    const list = all[animeId] || [];
    all[animeId] = list.filter(c => c.id !== targetId);
    saveJson(COMMENTS_FILE, all);
    return list.length - all[animeId].length;
  }
  if (report.type === 'dm') {
    const all = loadJson(DM_FILE, { seq: 0, threads: {}, lastRead: {} });
    let removed = 0;
    for (const [key, messages] of Object.entries(all.threads || {})) {
      const before = messages.length;
      all.threads[key] = messages.filter(m => m.id !== targetId);
      removed += before - all.threads[key].length;
    }
    saveJson(DM_FILE, all);
    return removed;
  }
  if (report.type === 'profile') {
    const targetUserId = Number(report.targetUserId || report.snapshot?.userId) || 0;
    if (!targetUserId) return 0;
    const db = loadUsers();
    const target = db.users.find(x => x.id === targetUserId);
    if (!target) return 0;
    target.bio = '';
    target.favoriteGenre = '';
    target.customAvatar = false;
    target.avatar = 0;
    target.avatarRev = (target.avatarRev || 0) + 1;
    try { fs.unlinkSync(pathModule.join(AVATARS_DIR, `${target.id}.img`)); } catch (_) {}
    saveUsers(db);
    return 1;
  }
  return 0;
}

function reportedUserId(report) {
  return Number(report.targetUserId || report.snapshot?.userId) || 0;
}

function banReportedUser(report, bannedUntil) {
  const targetUserId = reportedUserId(report);
  if (!targetUserId) return { error: 'В старой жалобе нет надёжного ID пользователя — автоматический бан запрещён' };
  const db = loadUsers();
  const target = db.users.find(x => x.id === targetUserId);
  if (!target) return { error: 'Исходный аккаунт уже удалён' };
  if (target.admin) return { error: 'Нельзя забанить администратора' };
  target.bannedUntil = Math.max(Number(target.bannedUntil) || 0, bannedUntil);
  saveUsers(db);
  const notifications = loadJson(NOTIF_FILE, { seq: 0, byUser: {} });
  const existing = notifications.byUser[target.id] || [];
  if (!existing.some(n => n.moderationReportId === report.id)) {
    addNotification(target.id, {
      type: 'system',
      from: 'AniPulse',
      text: 'Ваш аккаунт временно ограничен по результатам жалобы',
      moderationReportId: report.id,
    });
  }
  return { bannedUntil: target.bannedUntil };
}

async function handleAdmin(req, res) {
  const user = authUser(req);
  if (!user || !user.admin) return jsonRes(res, 403, { error: 'Только для администратора' });
  if (req.method === 'GET' && req.url.startsWith('/alapi/admin/analytics')) {
    const result = analyticsStore.summary(ANALYTICS_FILE);
    result.registeredUsers = loadUsers().users.length;
    return jsonRes(res, 200, result);
  }
  if (req.method === 'GET' && req.url.startsWith('/alapi/admin/reports')) {
    const all = loadReports();
    const status = new URL('http://x' + req.url).searchParams.get('status') || 'open';
    return jsonRes(res, 200, all.items.filter(x => status === 'all' || x.status === status).slice(-200).reverse());
  }
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
  const b = await readBody(req);
  if (req.url.startsWith('/alapi/admin/reports/action')) {
    const decisions = {
      reject: { status: 'rejected', remove: false, banHours: 0 },
      resolve: { status: 'resolved', remove: false, banHours: 0 },
      remove: { status: 'resolved', remove: true, banHours: 0 },
      ban_24h: { status: 'resolved', remove: false, banHours: 24 },
      remove_ban_24h: { status: 'resolved', remove: true, banHours: 24 },
      remove_ban_168h: { status: 'resolved', remove: true, banHours: 168 },
    };
    const decisionName = String((b && b.action) || '');
    const decision = decisions[decisionName];
    if (!decision) return jsonRes(res, 400, { error: 'Неизвестное решение по жалобе' });
    const all = loadReports();
    const report = all.items.find(x => x.id === Number(b && b.id));
    if (!report) return jsonRes(res, 404, { error: 'Жалоба не найдена' });
    if (report.status !== 'open') {
      if (report.action?.name === decisionName && report.action?.state === 'done') {
        return jsonRes(res, 200, {
          ok: true,
          removed: report.action.removed || 0,
          bannedUntil: report.action.bannedUntil || 0,
          repeated: true,
        });
      }
      return jsonRes(res, 409, { error: 'Жалоба уже обработана' });
    }
    if (report.action?.state === 'processing' && report.action.name !== decisionName) {
      return jsonRes(res, 409, { error: 'По жалобе уже выполняется другое решение' });
    }

    const plannedBannedUntil = decision.banHours > 0
      ? (report.action?.plannedBannedUntil || Date.now() + decision.banHours * 3600 * 1000)
      : 0;
    if (decision.banHours > 0) {
      const targetUserId = reportedUserId(report);
      const target = targetUserId && loadUsers().users.find(x => x.id === targetUserId);
      if (!targetUserId) return jsonRes(res, 400, { error: 'В старой жалобе нет надёжного ID пользователя — автоматический бан запрещён' });
      if (!target) return jsonRes(res, 400, { error: 'Исходный аккаунт уже удалён' });
      if (target.admin) return jsonRes(res, 400, { error: 'Нельзя забанить администратора' });
    }

    report.action = {
      ...(report.action || {}),
      name: decisionName,
      state: 'processing',
      plannedBannedUntil,
      startedAt: report.action?.startedAt || Date.now(),
    };
    saveJson(REPORTS_FILE, all);

    let removed = 0;
    let bannedUntil = 0;
    if (decision.banHours > 0) {
      const ban = banReportedUser(report, plannedBannedUntil);
      if (ban.error) return jsonRes(res, 400, { error: ban.error });
      bannedUntil = ban.bannedUntil;
    }
    if (decision.remove) removed = removeReportedContent(report);

    report.status = decision.status;
    report.resolvedAt = Date.now();
    report.resolvedBy = user.nick;
    report.resolution = sanitizeText(b && b.resolution, 300) || decisionName;
    report.action = {
      ...report.action,
      state: 'done',
      removed,
      bannedUntil,
      completedAt: report.resolvedAt,
    };
    saveJson(REPORTS_FILE, all);
    return jsonRes(res, 200, { ok: true, removed, bannedUntil });
  }
  if (req.url.startsWith('/alapi/admin/reports/resolve')) {
    const all = loadReports();
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
    for (const u of loadUsers().users) {
      if (u.nick) avByNick[u.nick.toLowerCase()] = { avatar: avatarOf(u), avatarRev: u.avatarRev || 0, userId: u.id };
    }
    const viewer = authUser(req);
    const hidden = viewer ? bilateralHiddenIds(blocksDb(), viewer.id) : new Set();
    const usersByNick = new Map(loadUsers().users.filter(u => u.nick).map(u => [u.nick.toLowerCase(), u.id]));
    const out = chat.messages.filter(m => m.id > after && !hidden.has(Number(m.userId))).slice(-100).map(({ userId, ...rest }) => {
      const replyUserId = Number(rest.replyTo?.userId)
        || usersByNick.get(String(rest.replyTo?.nick || '').toLowerCase())
        || 0;
      const replyTo = rest.replyTo && hidden.has(Number(replyUserId))
        ? { ...rest.replyTo, nick: 'Скрыто', text: 'Сообщение скрыто' }
        : rest.replyTo;
      return {
        ...rest,
        userId,
        ...(replyTo ? { replyTo } : {}),
        avatar: avByNick[String(rest.nick || '').toLowerCase()] !== undefined
          ? avByNick[String(rest.nick || '').toLowerCase()].avatar
          : (rest.avatar || 0),
        avatarRev: avByNick[String(rest.nick || '').toLowerCase()]?.avatarRev || 0,
      };
    });
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
    if (rid) {
      const orig = chat.messages.find(m => m.id === rid);
      if (orig) msg.replyTo = { id: orig.id, userId: orig.userId, nick: orig.nick, text: String(orig.text).slice(0, 80) };
    }
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
function isSafeCommentKey(id) {
  return /^[\w:.-]{1,40}$/.test(id) && !['__proto__', 'constructor', 'prototype'].includes(id);
}
function commentList(all, id) {
  return Object.prototype.hasOwnProperty.call(all, id) && Array.isArray(all[id]) ? all[id] : [];
}
async function handleComments(req, res) {
  const animeId = decodeURIComponent(String((req.url.match(/[?&]animeId=([\w:.%-]+)/) || [])[1] || ''));
  if (req.method === 'GET') {
    if (!animeId) return jsonRes(res, 400, { error: 'animeId required' });
    const all = loadJson(COMMENTS_FILE, {});
    const avByNick = {};
    for (const u of loadUsers().users) {
      if (u.nick) avByNick[u.nick.toLowerCase()] = { avatar: avatarOf(u), avatarRev: u.avatarRev || 0, userId: u.id };
    }
    const viewer = authUser(req);
    const hidden = viewer ? bilateralHiddenIds(blocksDb(), viewer.id) : new Set();
    const list = commentList(all, animeId);
    // userId нужен Android-клиенту как ключ кеша аватарки; он и так публичен в
    // карточке профиля, поэтому отдаём его как есть.
    const out = list.filter(c => !hidden.has(Number(c.userId))).slice(-100).map(({ userId, ...rest }) => ({
      ...rest,
      userId,
      avatar: avByNick[String(rest.nick || '').toLowerCase()] !== undefined
        ? avByNick[String(rest.nick || '').toLowerCase()].avatar
        : (rest.avatar || 0),
      avatarRev: avByNick[String(rest.nick || '').toLowerCase()]?.avatarRev || 0,
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
    // Ключ хранилища — только безопасный идентификатор: `__proto__` и подобные
    // ломали запись в общий объект комментариев.
    if (!isSafeCommentKey(id)) return jsonRes(res, 400, { error: 'Некорректный тайтл' });
    let text = sanitizeText(b && b.text, 1000);
    if (!id || !text) return jsonRes(res, 400, { error: 'Пустой комментарий' });
    if (hasViolence(text)) return jsonRes(res, 400, { error: 'Комментарий нарушает правила и не отправлен' });
    text = filterProfanity(text);
    if (tooMany(userHits, 'cm:' + user.id, 3, 60 * 1000)) return jsonRes(res, 429, { error: 'Не так быстро — до 3 комментариев в минуту' });
    const all = loadJson(COMMENTS_FILE, {});
    const list = commentList(all, id);
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
    if (!isSafeCommentKey(animeId)) return jsonRes(res, 400, { error: 'animeId required' });
    const all = loadJson(COMMENTS_FILE, {});
    const list = commentList(all, animeId);
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
    if (isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
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
  if (req.method !== 'POST') return jsonRes(res, 405, { error: 'method' });
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
  if (type === 'profile') {
    const nick = sanitizeText(targetId, 24);
    const target = loadUsers().users.find(u => u.nick && u.nick.toLowerCase() === nick.toLowerCase());
    if (!target) return null;
    return {
      userId: target.id,
      nick: target.nick,
      text: [target.bio, target.favoriteGenre].filter(Boolean).join(' · '),
      avatar: avatarOf(target),
    };
  }
  return null;
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
    if (block) {
      removeRelationship(user.id, targetId);
      removeInteractionNotifications(user.id, targetId);
    }
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
    const all = loadReports();
    const snapshotNick = type === 'profile'
      ? snapshot.nick
      : (snapshot.nick || snapshot.from);
    const targetUserId = Number(snapshot.userId) || userIdByNick(snapshotNick) || 0;
    const evidence = { ...snapshot, ...(targetUserId ? { userId: targetUserId } : {}) };
    const report = {
      id: ++all.seq, reporterId: user.id, reporterNick: user.nick, type,
      targetId, targetNick: sanitizeText(snapshotNick, 24), targetUserId,
      animeId: sanitizeText(b && b.animeId, 40), reason,
      details: sanitizeText(b && b.details, 500), snapshot: evidence, status: 'open', createdAt: Date.now(),
    };
    all.items.push(report); all.items = all.items.slice(-2000); saveJson(REPORTS_FILE, all);
    return jsonRes(res, 200, { ok: true, reportId: report.id });
  }
  return jsonRes(res, 404, { error: 'not found' });
}

/** Аватар пользователя для публичных ответов: -1 = кастомный (клиент грузит /alapi/avatar-img). */
function avatarOf(u) { return isBanned(u) ? 0 : (u.customAvatar ? -1 : (u.avatar || 0)); }

async function handleAvatarUpload(req, res) {
  const user = authUser(req);
  if (!user) return jsonRes(res, 401, { error: 'Не авторизован' });
  if (user.emailVerified === false) return jsonRes(res, 403, { error: 'Подтвердите почту: Профиль → код из письма' });
  if (isBanned(user)) return jsonRes(res, 403, { error: banMessage(user) });
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
  if (!u || !u.customAvatar || isBanned(u)) { res.writeHead(404); return res.end('no avatar'); }
  let buf;
  try { buf = fs.readFileSync(`${AVATARS_DIR}/${Number(u.id)}.img`); } catch (e) { res.writeHead(404); return res.end('no avatar'); }
  res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Cache-Control': 'private, no-store', 'X-Content-Type-Options': 'nosniff' });
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
  } catch (e) { console.error('oauth error:', e.message); res.writeHead(502); res.end('oauth error'); }
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
    if (!tokenResp.access_token) { console.error('vk token error:', tokenResp && tokenResp.error); res.writeHead(502); return res.end('vk token error'); }
    const infoResp = JSON.parse((await fetchFollow('https://id.vk.com/oauth2/user_info', {
      method: 'POST',
      body: 'client_id=' + cfg.client_id + '&access_token=' + encodeURIComponent(tokenResp.access_token),
    })).body.toString());
    const u = infoResp.user || {};
    if (!u.user_id) { res.writeHead(502); return res.end('vk info error'); }
    const linkState = saved.link || ''; // из своего хранилища, не из эха VK
    socialLogin('vk', u.user_id, [u.first_name, u.last_name].filter(Boolean).join(' '), res, linkState);
  } catch (e) { console.error('oauth error:', e.message); res.writeHead(502); res.end('oauth error'); }
}

// ===== Публичные юридические страницы =====
const LEGAL_CONTACT = 'marc.1010@yandex.ru';
const LEGAL_UPDATED = '7 августа 2026';
const LEGAL_FILE = dataPath('legal.json');
function legalCfg() {
  return loadJson(LEGAL_FILE, {
    operatorName: 'Ившин Максим Сергеевич', operatorAddress: 'НЕ ЗАПОЛНЕНО', operatorInn: '',
    serverCountry: 'Российская Федерация', contact: LEGAL_CONTACT,
  });
}

function legalAddressText(cfg) {
  const address = String(cfg.operatorAddress || '').trim();
  if (!address || address === 'НЕ ЗАПОЛНЕНО') return '';
  return ` Адрес: ${address}.${cfg.operatorInn ? ` ИНН: ${cfg.operatorInn}.` : ''}`;
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
<p>Оператор персональных данных: <b>${cfg.operatorName}</b>.${legalAddressText(cfg)} Контакт: <a href="mailto:${contact}">${contact}</a>.</p>
<h2>Какие данные мы собираем</h2>
<p>При регистрации: ник, почта, пароль (хранится только в виде необратимого хеша scrypt — мы никогда не видим и не храним пароль в открытом виде). При входе через VK/Яндекс — идентификатор вашего аккаунта в этом сервисе, без пароля.</p>
<p>По желанию: аватар (готовый пресет или загруженное и обрезанное пользователем изображение), короткое «о себе», любимый жанр.</p>
<p>Для вошедших пользователей список «Моё», статусы тайтлов и прогресс серий (номер серии, позиция, длительность и время обновления) синхронизируются с сервером, чтобы продолжать просмотр на сайте и других устройствах. Эти данные доступны только владельцу аккаунта, хранятся до их удаления или удаления аккаунта и не передаются поставщикам каталога или видео. Токен входа и настройки приложения хранятся отдельно в зашифрованном хранилище Android.</p>
<p>Контент, который вы создаёте сами: комментарии, оценки тайтлов и жалобы — хранится на нашем сервере для публикации, учёта оценки и модерации. Встроенные чат, личные сообщения и друзья отключены.</p>
<p>Автоматически: IP-адрес (только для защиты от злоупотреблений — ограничение частоты запросов, не хранится долговременно), при добровольной отправке баг-репорта — модель устройства и версия Android.</p>
<p>Для псевдонимизированной технической статистики использования создаётся случайный идентификатор установки. После входа он может быть связан с ID аккаунта. Мы учитываем первый запуск, версию приложения, активность приложения или сайта, примерную длительность активной сессии и факт скачивания APK с нашего сайта. Идентификатор не является рекламным идентификатором или отпечатком устройства; постоянный сбор в фоне не выполняется, IP-адрес в статистике не сохраняется. При удалении аккаунта связь идентификатора с ID аккаунта удаляется.</p>
<h2>Как мы используем данные</h2>
<ul>
<li>Вход и работа аккаунта, восстановление пароля, подтверждение почты</li>
<li>Публикация комментариев, учёт рейтингов и обработка жалоб</li>
<li>Защита от спама и злоупотреблений (ограничения частоты, бан за нарушение правил)</li>
<li>Уведомления о новых сериях и упоминаниях в комментариях</li>
<li>Псевдонимизированная оценка числа активных пользователей, качества обновлений и стабильности сервиса</li>
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
<ul><li>аккаунт и профиль — до удаления аккаунта;</li><li>комментарии и оценки — до удаления автором, лимита хранилища или аккаунта;</li><li>псевдонимизированные события статистики — до удаления аккаунта или достижения цели обработки;</li><li>жалобы и решения модерации — до 3 лет после удаления связи с аккаунтом;</li><li>технические журналы защиты — не более 30 дней.</li></ul>
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
  sendLegalPage(res, 'Пользовательское соглашение', `<h2>1. Общие условия</h2><p>AniPulse — beta-сервис ${cfg.operatorName}. Создавая аккаунт, вы принимаете эти условия. Сервис предназначен для лиц старше 18 лет.</p><h2>2. Аккаунт</h2><p>Вы отвечаете за сохранность доступа и действия в аккаунте. Аккаунт можно удалить в приложении.</p><h2>3. Пользовательский контент</h2><p>Пользователь сохраняет права на свои комментарии и даёт сервису неисключительное право показывать их в AniPulse. Встроенные чат, личные сообщения и друзья отключены. Запрещены угрозы, травля, спам, мошенничество, ненависть, порнография и нарушение чужих прав. Детали: <a href="/community-rules">Правила сообщества</a>.</p><h2>4. Модерация</h2><p>Мы можем скрыть или удалить комментарий, ограничить или заблокировать аккаунт при нарушениях. Жалобы подаются из меню комментария или профиля.</p><h2>5. Рекомендации</h2><p>Правила формирования блока «Для вас» опубликованы отдельно: <a href="/recommendations-rules">Правила рекомендательных технологий</a>.</p><h2>6. Beta-статус</h2><p>Функции могут меняться, а доступность материалов зависит от внешних источников. Мы не гарантируем бесперебойную работу.</p><h2>7. Контакт</h2><p><a href="mailto:${contact}">${contact}</a></p>`);
}

function handleCommunityRulesPage(res) {
  sendLegalPage(res, 'Правила сообщества', `<h2>Будьте уважительны</h2><p>Нельзя угрожать, травить, преследовать, оскорблять или раскрывать чужие личные данные в комментариях, профиле, нике или аватаре.</p><h2>Запрещённый контент</h2><ul><li>порнография, особенно с участием несовершеннолетних;</li><li>призывы к насилию, суициду, экстремизму или ненависти;</li><li>наркотики, оружие, мошенничество, фишинг и вредоносные ссылки;</li><li>спам, реклама без согласования и нарушение авторских прав.</li></ul><h2>Спойлеры</h2><p>Отмечайте спойлеры и не раскрывайте сюжет в нике или аватаре.</p><h2>Жалобы</h2><p>Выберите «Пожаловаться» в меню комментария или профиля. Заведомо ложные жалобы также нарушают правила.</p>`);
}

function handleConsentPage(res) {
  const cfg = legalCfg(), contact = cfg.contact || LEGAL_CONTACT;
  sendLegalPage(res, 'Согласие на обработку персональных данных', `<p>Я свободно, своей волей и в своём интересе даю ${cfg.operatorName}${legalAddressText(cfg)}, согласие на автоматизированную обработку моих данных: ника, email, ID аккаунта, аватара, профиля, оценок, списков, прогресса просмотра, псевдонимизированной технической статистики, комментариев, жалоб и технических данных.</p><p>Цели: создание и защита аккаунта, синхронизация, публикация комментариев, учёт оценок, модерация, уведомления и техническая поддержка. Действия: сбор, запись, хранение, уточнение, использование, передача указанным в политике обработчикам, блокирование, удаление и уничтожение.</p><p>При отдельном выборе источника Kodik пользовательское устройство может передать IP-адрес и стандартные технические сведения сетевого запроса CDN, расположенному в Нидерландах, исключительно для доставки выбранного видеопотока. Ник, email, комментарии и токен AniPulse не передаются.</p><p>Согласие действует до удаления аккаунта или отзыва согласия. Отозвать его можно письмом на <a href="mailto:${contact}">${contact}</a> или удалением аккаунта. <a href="/privacy">Полная политика</a>.</p>`);
}

function handleRecommendationsRulesPage(res) {
  const contact = legalCfg().contact || LEGAL_CONTACT;
  sendLegalPage(res, 'Правила рекомендательных технологий', `<h2>Как формируется блок «Для вас»</h2><p>AniPulse подбирает тайтлы автоматически на основании выбранных пользователем жанров, списка «Моё», статусов, оценок и истории просмотра. Также учитываются общая популярность и рейтинг тайтлов. Рекомендации не используются для рекламы и не основаны на чувствительных персональных данных.</p><h2>Как изменить рекомендации</h2><p>Пользователь может изменить рекомендации, обновив любимый жанр, оценки и статусы в списке «Моё», удалив отдельные записи прогресса либо аккаунт целиком. При отсутствии достаточной истории показываются общие популярные тайтлы.</p><h2>Обращения</h2><p>Сообщить о неподходящей рекомендации или запросить сведения о правилах их формирования можно по адресу <a href="mailto:${contact}">${contact}</a>.</p>`);
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
  if (req.url === '/recommendations-rules') return handleRecommendationsRulesPage(res);
  if (req.url === '/for-right-holders') return handleRightHoldersPage(res);
  if (req.url.startsWith('/auth/android-callback')) {
    const html = legalPage('Возврат в приложение', '<h2>Вернитесь в AniPulse</h2><p>Если приложение не открылось автоматически, установите актуальную beta-версию и повторите вход.</p>');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    return res.end(html);
  }
  const dubsM = req.url.match(/^\/alapi\/kodik-dubs\?shikimoriId=(\d+)/);
  if (dubsM) {
    if (BLOCKED_ANIME_IDS.has(Number(dubsM[1]))) {
      res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
      return res.end(JSON.stringify({ error: 'not found' }));
    }
    return handleKodikDubs(dubsM[1], res);
  }
  const findM = req.url.match(/^\/alapi\/kodik-find\?shikimoriId=(\d+)/);
  if (findM) return handleKodikFind(findM[1], res);
  const kodikM = req.url.match(/^\/alapi\/kodik\?link=([^&]+)(?:&episode=(\d+))?/);
  if (kodikM) return handleKodik(decodeURIComponent(kodikM[1]), kodikM[2], res);
  if (req.url.startsWith('/alapi/bugreport')) return handleBugReport(req, res);
  if (req.url.startsWith('/alapi/analytics/heartbeat')) return handleAnalyticsHeartbeat(req, res);
  if (req.url.startsWith('/alapi/admin/')) return handleAdmin(req, res);
  if (req.url.startsWith('/alapi/blocks') || req.url.startsWith('/alapi/reports')) return handleModeration(req, res);
  if (req.url.startsWith('/alapi/user')) return handleUserCard(req, res);
  if (req.url.startsWith('/alapi/profile')) return handleProfileUpdate(req, res);
  if (req.url.startsWith('/alapi/friends') || req.url.startsWith('/alapi/dm') || req.url.startsWith('/alapi/chat')) {
    return jsonRes(res, 410, {
      error: 'Встроенное общение отключено',
      code: 'SOCIAL_FEATURE_REMOVED',
      message: 'Чаты, личные сообщения и друзья больше недоступны. Используйте официальное сообщество AniPulse.',
    });
  }
  if (req.url.startsWith('/alapi/notifications')) return handleNotifications(req, res);
  if (req.url.startsWith('/alapi/comments')) return handleComments(req, res);
  if (req.url.startsWith('/alapi/sync')) return handleSync(req, res);
  if (req.url.startsWith('/alapi/leaderboard')) return handleLeaderboard(req, res);
  if (req.url.startsWith('/alapi/stats/me')) return handleMyStats(req, res);
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
  if (req.url.startsWith('/alapi/apk')) return handleApkDownload(req, res);
  if (req.url.startsWith('/alapi/anilibria-updates')) return handleAnilibriaUpdates(res);
  const poster = req.url.match(/^\/alapi\/poster\/(\d+)/);
  if (poster) return handlePoster(poster[1], res);

  const m = req.url.match(/^\/alapi\/([a-z0-9]+)\/(.*)$/);
  if (!m) { res.writeHead(404); return res.end('not found'); }
  const alias = m[1];
  const base = UPSTREAMS[alias];
  if (!base) { res.writeHead(404); return res.end('unknown source'); }
  const target = base + '/' + m[2];
  const policy = publicProxyPolicy(alias, target);
  if (!policy) {
    res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    return res.end(JSON.stringify({ error: 'proxy route is not allowed' }));
  }
  if (tooMany(ipHits, `proxy:${clientIp(req)}`, 300, 60_000)) {
    res.writeHead(429, { 'Content-Type': 'application/json; charset=utf-8', 'Retry-After': '60' });
    return res.end(JSON.stringify({ error: 'too many proxy requests' }));
  }
  if (alias === 'shikimori') {
    const detail = String(m[2]).match(/^api\/animes\/(\d+)(?:\/.*)?$/);
    if (detail && BLOCKED_ANIME_IDS.has(Number(detail[1]))) {
      res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
      return res.end(JSON.stringify({ error: 'not found' }));
    }
  }
  // Постеры не меняются по URL — неделя клиентского кэша (дисковый кэш Coil),
  // повторные заходы в каталог больше не тянут картинки по сети вообще.
  const hit = cache.get(target);
  if (hit) {
    res.writeHead(hit.status, safeProxyHeaders(hit.ctype, { cacheHit: true }));
    return res.end(hit.body);
  }
  try {
    let pending = proxyInflight.get(target);
    if (!pending) {
      if (proxyInflight.size >= MAX_PROXY_INFLIGHT) {
        res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8', 'Retry-After': '2' });
        return res.end(JSON.stringify({ error: 'proxy is busy' }));
      }
      pending = policy.kind === 'json'
        ? fetchJsonUpstream(target, { maxBytes: policy.maxBytes })
        : fetchFollow(target, { maxBytes: policy.maxBytes });
      proxyInflight.set(target, pending);
      pending.then(() => proxyInflight.delete(target), () => proxyInflight.delete(target));
    }
    const r = await pending;
    if (r.status < 200 || r.status >= 300) {
      const status = r.status >= 400 && r.status <= 599 ? r.status : 502;
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
      return res.end(JSON.stringify({ error: 'upstream request failed' }));
    }
    let responseBody;
    let responseType;
    if (policy.kind === 'json') {
      let parsed;
      try { parsed = JSON.parse(r.body.toString('utf8')); } catch (_) {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
        return res.end(JSON.stringify({ error: 'invalid upstream JSON' }));
      }
      responseBody = Buffer.from(JSON.stringify(parsed));
      responseType = 'application/json; charset=utf-8';
    } else {
      responseType = detectRasterContentType(r.body);
      if (!responseType || !isSafeProxyContentType(responseType)) {
        res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
        return res.end(JSON.stringify({ error: 'invalid upstream image' }));
      }
      responseBody = r.body;
    }
    if (alias === 'shikimori' && policy.kind === 'json') {
      const filtered = filterBlockedAnimePayload('/' + m[2], responseBody);
      if (filtered) {
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
        return res.end(filtered);
      }
    }
    cache.set(target, {
      status: 200,
      body: responseBody,
      ctype: responseType,
      exp: Date.now() + (policy.kind === 'image' ? IMG_TTL_MS : TTL_MS),
    });
    res.writeHead(200, safeProxyHeaders(responseType));
    return res.end(responseBody);
  } catch (e) { console.error('proxy error:', alias, e.message); res.writeHead(502); res.end('gateway error'); }
}
// Страховка на случай промисов вне запросов (таймеры, почта): лог вместо падения процесса.
process.on('unhandledRejection', (e) => console.error('unhandledRejection:', e));
const LISTEN_PORT = Number(process.env.ANIPULSE_PORT || 8090);
server.listen(LISTEN_PORT, '127.0.0.1', () => console.log(`AniPulse gateway on 127.0.0.1:${LISTEN_PORT}`));
server.headersTimeout = 15_000;
server.requestTimeout = 30_000;
server.keepAliveTimeout = 5_000;
server.maxRequestsPerSocket = 100;
