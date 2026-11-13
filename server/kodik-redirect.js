'use strict';

// Входные узлы CDN Kodik (cloud.solodcdn.com, с вечера 24.10.2026 и
// cloud2.solodcdn.com) раздают озвучку Studio Band и ещё нескольких студий,
// но сами видео не хранят — только перенаправляют (302) на рабочие узлы вида
// magnesium.cloud2.solodcdn.com. Ссылка после перенаправления не привязана к IP.
//
// cloud.solodcdn.com недоступен у части российских провайдеров и с нашего
// сервера, cloud2 отвечает через раз по 2–7 секунд, а иногда не отвечает
// вовсе. Зритель в обоих случаях видел чёрный экран (жалобы, 24.10.2026).
//
// Поэтому шлюз сам проходит входной узел — одновременно напрямую и через
// прокси за рубежом, берёт первый годный ответ — и отдаёт клиенту конечный
// адрес. Видео через прокси не идёт — зритель качает его напрямую с рабочего узла.

const http = require('http');
const https = require('https');
const tls = require('tls');

const FRONT_HOST = /^cloud\d*\.solodcdn\.com$/i;
const ALLOWED_TARGET = /(^|\.)solodcdn\.com$/i;
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';

// Прокси хранится как «логин:пароль@хост:порт» или полным адресом со схемой.
function normalizeProxy(value) {
  const v = String(value || '').trim();
  if (!v) return '';
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(v) ? v : 'http://' + v;
}

function needsResolve(url) {
  try { return FRONT_HOST.test(new URL(url).hostname); } catch (_) { return false; }
}

/**
 * GET к входному узлу — напрямую или через HTTP-прокси (CONNECT). Читаем только
 * статус и Location: тело не качаем, соединение закрываем сразу.
 */
function fetchRedirect(targetUrl, proxyUrl, timeoutMs = 8000) {
  return new Promise((resolve, reject) => {
    const target = new URL(targetUrl);
    let done = false;
    let cleanup = () => {};
    const finish = (fn, value) => { if (!done) { done = true; clearTimeout(timer); cleanup(); fn(value); } };
    const timer = setTimeout(() => finish(reject, new Error((proxyUrl ? 'proxy' : 'direct') + ' timeout')), timeoutMs);

    const request = (createConnection) => {
      const req = https.request({
        host: target.hostname,
        path: target.pathname + target.search,
        method: 'GET',
        headers: { 'User-Agent': UA, Accept: '*/*' },
        ...(createConnection ? { createConnection } : {}),
      }, (upstream) => {
        finish(resolve, { status: upstream.statusCode, location: upstream.headers.location || null });
      });
      cleanup = () => req.destroy();
      req.on('error', (e) => finish(reject, e));
      req.end();
    };

    if (!proxyUrl) return request(null);

    const proxy = new URL(proxyUrl);
    if (proxy.protocol !== 'http:') return finish(reject, new Error('only http proxy is supported'));
    const headers = { Host: `${target.hostname}:443` };
    if (proxy.username) {
      const auth = `${decodeURIComponent(proxy.username)}:${decodeURIComponent(proxy.password)}`;
      headers['Proxy-Authorization'] = 'Basic ' + Buffer.from(auth).toString('base64');
    }
    const connectReq = http.request({
      host: proxy.hostname,
      port: Number(proxy.port) || 80,
      method: 'CONNECT',
      path: `${target.hostname}:443`,
      headers,
    });
    cleanup = () => connectReq.destroy();
    connectReq.on('connect', (res, socket) => {
      if (res.statusCode !== 200) {
        socket.destroy();
        return finish(reject, new Error(`proxy CONNECT ${res.statusCode}`));
      }
      const secure = tls.connect({ socket, servername: target.hostname });
      request(() => secure);
      const destroyRequest = cleanup;
      cleanup = () => { destroyRequest(); secure.destroy(); };
    });
    connectReq.on('error', (e) => finish(reject, e));
    connectReq.end();
  });
}

function targetFrom(url, { status, location }) {
  if (!(status >= 300 && status < 400 && location)) throw new Error(`front answered ${status}`);
  const next = new URL(location, url);
  if (next.protocol !== 'https:' || !ALLOWED_TARGET.test(next.hostname) || FRONT_HOST.test(next.hostname)) {
    throw new Error('unexpected redirect target');
  }
  return next.toString();
}

/**
 * Ссылку на входной узел меняем на конечную. Если не ответил ни один путь —
 * оставляем как было: хуже не станет, плеер покажет «озвучка не загружается».
 */
async function resolveBlockedFront(url, proxyUrl, fetch = fetchRedirect) {
  if (!needsResolve(url)) return url;
  const routes = [''];
  if (proxyUrl) routes.push(proxyUrl);
  try {
    return await Promise.any(routes.map(async (route) => targetFrom(url, await fetch(url, route))));
  } catch (e) {
    const reasons = (e.errors || [e]).map((x) => x.message).join('; ');
    console.error('kodik front resolve failed:', reasons);
    return url;
  }
}

module.exports = { resolveBlockedFront, fetchRedirect, needsResolve, normalizeProxy, FRONT_HOST };
