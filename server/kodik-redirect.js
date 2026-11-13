'use strict';

// Входной узел CDN Kodik cloud.solodcdn.com недоступен у части российских
// провайдеров (и с нашего сервера в Москве), а с него раздаётся почти вся
// озвучка Studio Band: у зрителей чёрный экран (жалоба, 24.10.2026). Сам узел
// только перенаправляет (302) на рабочие узлы вида magnesium.cloud.solodcdn.com,
// а они из России доступны, и ссылка после перенаправления не привязана к IP.
//
// Поэтому шлюз через прокси за рубежом делает один запрос заголовков к входному
// узлу и отдаёт клиенту конечный адрес. Видео через прокси не идёт — зритель
// качает его напрямую с рабочего узла.

const http = require('http');
const https = require('https');
const tls = require('tls');

const BLOCKED_FRONTS = new Set(['cloud.solodcdn.com']);
const ALLOWED_TARGET = /(^|\.)solodcdn\.com$/i;
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';

// Прокси хранится как «логин:пароль@хост:порт» или полным адресом со схемой.
function normalizeProxy(value) {
  const v = String(value || '').trim();
  if (!v) return '';
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(v) ? v : 'http://' + v;
}

function needsResolve(url) {
  try { return BLOCKED_FRONTS.has(new URL(url).hostname); } catch (_) { return false; }
}

/**
 * GET через HTTP-прокси (CONNECT), читаем только статус и Location — тело не
 * качаем, соединение закрываем сразу.
 */
function fetchRedirectViaProxy(targetUrl, proxyUrl, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const target = new URL(targetUrl);
    const proxy = new URL(proxyUrl);
    if (proxy.protocol !== 'http:') return reject(new Error('only http proxy is supported'));
    const headers = { Host: `${target.hostname}:443` };
    if (proxy.username) {
      const auth = `${decodeURIComponent(proxy.username)}:${decodeURIComponent(proxy.password)}`;
      headers['Proxy-Authorization'] = 'Basic ' + Buffer.from(auth).toString('base64');
    }
    let done = false;
    const finish = (fn, value) => { if (!done) { done = true; clearTimeout(timer); fn(value); } };
    const timer = setTimeout(() => { connectReq.destroy(); finish(reject, new Error('proxy timeout')); }, timeoutMs);
    const connectReq = http.request({
      host: proxy.hostname,
      port: Number(proxy.port) || 80,
      method: 'CONNECT',
      path: `${target.hostname}:443`,
      headers,
    });
    connectReq.on('connect', (res, socket) => {
      if (res.statusCode !== 200) {
        socket.destroy();
        return finish(reject, new Error(`proxy CONNECT ${res.statusCode}`));
      }
      const secure = tls.connect({ socket, servername: target.hostname });
      const req = https.request({
        host: target.hostname,
        path: target.pathname + target.search,
        method: 'GET',
        headers: { 'User-Agent': UA, Accept: '*/*' },
        createConnection: () => secure,
      }, (upstream) => {
        const result = { status: upstream.statusCode, location: upstream.headers.location || null };
        upstream.destroy();
        secure.destroy();
        finish(resolve, result);
      });
      req.on('error', (e) => finish(reject, e));
      req.end();
    });
    connectReq.on('error', (e) => finish(reject, e));
    connectReq.end();
  });
}

/**
 * Ссылку на заблокированный входной узел меняем на конечную. Любая ошибка или
 * подозрительный ответ — оставляем как было: хуже не станет, плеер покажет
 * «озвучка не загружается».
 */
async function resolveBlockedFront(url, proxyUrl, fetchRedirect = fetchRedirectViaProxy) {
  if (!proxyUrl || !needsResolve(url)) return url;
  try {
    const { status, location } = await fetchRedirect(url, proxyUrl);
    if (status >= 300 && status < 400 && location) {
      const next = new URL(location, url);
      if (next.protocol === 'https:' && ALLOWED_TARGET.test(next.hostname) && !BLOCKED_FRONTS.has(next.hostname)) {
        return next.toString();
      }
    }
  } catch (e) {
    console.error('kodik front resolve failed:', e.message);
  }
  return url;
}

module.exports = { resolveBlockedFront, fetchRedirectViaProxy, needsResolve, normalizeProxy, BLOCKED_FRONTS };
