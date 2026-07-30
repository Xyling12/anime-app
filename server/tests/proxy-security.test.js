'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  ByteLruCache,
  assertSafeHttpsUrl,
  createSafeLookup,
  detectRasterContentType,
  isPrivateOrReservedIp,
  isSafeProxyContentType,
  publicProxyPolicy,
  safeProxyHeaders,
} = require('../proxy-security');

test('blocks private, loopback, link-local and reserved addresses', () => {
  for (const ip of [
    '0.0.0.0', '10.0.0.1', '100.64.0.1', '127.0.0.1', '169.254.169.254',
    '172.16.0.1', '192.168.1.1', '198.18.0.1', '::1', 'fc00::1', 'fe80::1',
    '0:0:0:0:0:0:0:1', '2001:db8::1', '::ffff:127.0.0.1', '::ffff:7f00:1',
  ]) assert.equal(isPrivateOrReservedIp(ip), true, ip);
  assert.equal(isPrivateOrReservedIp('1.1.1.1'), false);
  assert.equal(isPrivateOrReservedIp('2606:4700:4700::1111'), false);
});

test('only permits credential-free HTTPS URLs on port 443', () => {
  assert.equal(assertSafeHttpsUrl('https://example.com/path').hostname, 'example.com');
  for (const url of [
    'http://example.com', 'https://localhost/a', 'https://127.0.0.1/a',
    'https://example.com:8443/a', 'https://user:pass@example.com/a',
  ]) assert.throws(() => assertSafeHttpsUrl(url));
});

test('safe DNS lookup rejects a hostname when any answer is private', async () => {
  const lookup = createSafeLookup((_host, _options, callback) => callback(null, [
    { address: '1.1.1.1', family: 4 },
    { address: '127.0.0.1', family: 4 },
  ]));
  await assert.rejects(new Promise((resolve, reject) => {
    lookup('example.com', {}, (error, address) => error ? reject(error) : resolve(address));
  }), /private or reserved/);
});

test('safe DNS lookup returns a public pinned answer', async () => {
  const lookup = createSafeLookup((_host, _options, callback) => callback(null, [
    { address: '1.1.1.1', family: 4 },
  ]));
  const answer = await new Promise((resolve, reject) => {
    lookup('example.com', {}, (error, address, family) => error ? reject(error) : resolve({ address, family }));
  });
  assert.deepEqual(answer, { address: '1.1.1.1', family: 4 });
});

test('rejects active browser content and permits required API/media MIME types', () => {
  for (const type of ['text/html', 'application/javascript', 'image/svg+xml', 'application/xml']) {
    assert.equal(isSafeProxyContentType(type), false, type);
  }
  for (const type of ['application/json; charset=utf-8', 'image/jpeg', 'application/vnd.apple.mpegurl']) {
    assert.equal(isSafeProxyContentType(type), true, type);
  }
  const headers = safeProxyHeaders('application/json; charset=utf-8');
  assert.equal(headers['X-Content-Type-Options'], 'nosniff');
  assert.match(headers['Content-Security-Policy'], /default-src 'none'/);
  assert.equal(headers['Content-Disposition'], 'attachment');
});

test('detects raster images by bytes and does not trust SVG text', () => {
  assert.equal(detectRasterContentType(Buffer.from([0xff, 0xd8, 0xff, 0x00])), 'image/jpeg');
  assert.equal(detectRasterContentType(Buffer.from('\u0089PNG\r\n\u001a\n', 'latin1')), 'image/png');
  assert.equal(detectRasterContentType(Buffer.from('GIF89a')), 'image/gif');
  assert.equal(detectRasterContentType(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>')), null);
});

test('byte LRU enforces both memory and entry limits and prunes expiry', () => {
  const cache = new ByteLruCache({ maxBytes: 5, maxEntries: 2 });
  cache.set('a', { body: Buffer.alloc(2), exp: 100 });
  cache.set('b', { body: Buffer.alloc(2), exp: 100 });
  cache.set('c', { body: Buffer.alloc(2), exp: 100 });
  assert.equal(cache.get('a', 0), null);
  assert.equal(cache.bytes, 4);
  assert.equal(cache.map.size, 2);
  cache.prune(101);
  assert.equal(cache.bytes, 0);
  assert.equal(cache.map.size, 0);
});

test('public proxy permits only the routes used by the Android client', () => {
  assert.equal(publicProxyPolicy('shikimori', 'https://shikimori.io/api/animes?limit=30')?.kind, 'json');
  assert.equal(publicProxyPolicy('shikimori', 'https://shikimori.io/api/animes/1/similar')?.kind, 'json');
  assert.equal(publicProxyPolicy('shikimori', 'https://shikimori.io/system/animes/preview/1.jpg')?.kind, 'image');
  assert.equal(publicProxyPolicy('anilibria', 'https://anilibria.top/api/v1/anime/releases/1')?.kind, 'json');
  assert.equal(publicProxyPolicy('aniskip', 'https://api.aniskip.com/v2/skip-times/1/2?types=op&types=ed')?.kind, 'json');
  assert.equal(publicProxyPolicy('malcdn', 'https://cdn.myanimelist.net/images/anime/1/2.jpg')?.kind, 'image');
});

test('public proxy denies active pages, unused aliases and abusive queries', () => {
  assert.equal(publicProxyPolicy('shikimori', 'https://shikimori.io/login'), null);
  assert.equal(publicProxyPolicy('shikimori', 'https://shikimori.io/api/animes?limit=100000'), null);
  assert.equal(publicProxyPolicy('shikimori', 'https://shikimori.io/api/animes?admin=true'), null);
  assert.equal(publicProxyPolicy('animego', 'https://animego.me/anything'), null);
  assert.equal(publicProxyPolicy('anime365', 'https://smotret-anime.online/anything'), null);
  assert.equal(publicProxyPolicy('jikan', 'https://api.jikan.moe/v4/anime/1'), null);
  assert.equal(publicProxyPolicy('malcdn', 'https://cdn.myanimelist.net/evil.svg'), null);
});
