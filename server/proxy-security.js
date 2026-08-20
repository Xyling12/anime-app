'use strict';

const dns = require('dns');
const net = require('net');

function isPrivateOrReservedIp(address) {
  let ip = String(address || '').trim().toLowerCase().replace(/^\[|\]$/g, '').split('%', 1)[0];

  if (net.isIPv4(ip)) {
    const parts = ip.split('.').map(Number);
    const [a, b, c] = parts;
    return (
      a === 0 ||
      a === 10 ||
      a === 127 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 0 && c === 0) ||
      (a === 192 && b === 0 && c === 2) ||
      (a === 192 && b === 168) ||
      (a === 198 && (b === 18 || b === 19)) ||
      (a === 198 && b === 51 && c === 100) ||
      (a === 203 && b === 0 && c === 113) ||
      a >= 224
    );
  }

  if (net.isIPv6(ip)) {
    let normalized = ip;
    const ipv4Tail = normalized.match(/(\d+\.\d+\.\d+\.\d+)$/)?.[1];
    if (ipv4Tail) {
      const octets = ipv4Tail.split('.').map(Number);
      normalized = normalized.slice(0, -ipv4Tail.length) +
        ((octets[0] << 8) | octets[1]).toString(16) + ':' +
        ((octets[2] << 8) | octets[3]).toString(16);
    }
    const halves = normalized.split('::');
    const left = halves[0] ? halves[0].split(':') : [];
    const right = halves[1] ? halves[1].split(':') : [];
    const groups = halves.length === 2
      ? [...left, ...Array(8 - left.length - right.length).fill('0'), ...right]
      : left;
    const bytes = groups.flatMap(group => {
      const value = Number.parseInt(group || '0', 16);
      return [value >> 8, value & 0xff];
    });
    const allZero = bytes.every(value => value === 0);
    const loopback = bytes.slice(0, 15).every(value => value === 0) && bytes[15] === 1;
    const mappedV4 = bytes.slice(0, 10).every(value => value === 0) &&
      bytes[10] === 0xff && bytes[11] === 0xff
      ? bytes.slice(12).join('.')
      : null;
    return (
      allZero ||
      loopback ||
      (mappedV4 && isPrivateOrReservedIp(mappedV4)) ||
      (bytes[0] & 0xfe) === 0xfc ||
      (bytes[0] === 0xfe && (bytes[1] & 0xc0) === 0x80) ||
      bytes[0] === 0xff ||
      (bytes[0] === 0x20 && bytes[1] === 0x01 && bytes[2] === 0x0d && bytes[3] === 0xb8)
    );
  }

  return true;
}

function assertSafeHttpsUrl(input) {
  const url = input instanceof URL ? input : new URL(input);
  if (url.protocol !== 'https:') throw new Error('only HTTPS upstreams are allowed');
  if (url.port && url.port !== '443') throw new Error('non-standard upstream port is not allowed');
  if (url.username || url.password) throw new Error('upstream credentials in URL are not allowed');
  if (!url.hostname || url.hostname.toLowerCase() === 'localhost') throw new Error('local upstream is not allowed');
  if (net.isIP(url.hostname) && isPrivateOrReservedIp(url.hostname)) {
    throw new Error('private or reserved upstream address is not allowed');
  }
  return url;
}

function onlyQueryParams(url, allowed) {
  for (const key of url.searchParams.keys()) if (!allowed.has(key)) return false;
  return true;
}

function publicProxyPolicy(alias, target) {
  const url = new URL(target);
  const path = url.pathname;
  if (alias === 'shikimori') {
    if (/^\/system\/animes\/(?:original|preview|x48|x96)\/[a-z0-9_./-]+\.(?:avif|gif|jpe?g|png|webp)$/i.test(path) &&
        (!url.search || /^\?\d{1,20}$/.test(url.search))) {
      return { kind: 'image', maxBytes: 4 * 1024 * 1024 };
    }
    if (path === '/api/animes') {
      const allowed = new Set(['page', 'limit', 'order', 'kind', 'status', 'season', 'genre', 'search', 'censored']);
      const page = Number(url.searchParams.get('page') || 1);
      const limit = Number(url.searchParams.get('limit') || 24);
      const search = url.searchParams.get('search') || '';
      if (!onlyQueryParams(url, allowed) || !Number.isInteger(page) || page < 1 || page > 1000 ||
          !Number.isInteger(limit) || limit < 1 || limit > 30 || search.length > 100) return null;
      return { kind: 'json', maxBytes: 2 * 1024 * 1024 };
    }
    if ((/^\/api\/animes\/\d+(?:\/similar|\/related|\/franchise)?$/.test(path) || path === '/api/genres') && !url.search) {
      return { kind: 'json', maxBytes: 2 * 1024 * 1024 };
    }
    if (path === '/api/calendar' && onlyQueryParams(url, new Set(['censored']))) {
      return { kind: 'json', maxBytes: 2 * 1024 * 1024 };
    }
    return null;
  }
  if (alias === 'anilibria') {
    if (path === '/api/v1/app/search/releases' &&
        onlyQueryParams(url, new Set(['query'])) &&
        (url.searchParams.get('query') || '').length <= 150) {
      return { kind: 'json', maxBytes: 2 * 1024 * 1024 };
    }
    if (/^\/api\/v1\/anime\/releases\/\d+$/.test(path) && !url.search) {
      return { kind: 'json', maxBytes: 4 * 1024 * 1024 };
    }
    if (/^\/storage\/releases\/posters\/[a-z0-9_./-]+\.(?:avif|gif|jpe?g|png|webp)$/i.test(path) && !url.search) {
      return { kind: 'image', maxBytes: 4 * 1024 * 1024 };
    }
    return null;
  }
  if (alias === 'aniskip' &&
      /^\/v2\/skip-times\/\d+\/\d+$/.test(path) &&
      onlyQueryParams(url, new Set(['types', 'episodeLength'])) &&
      url.searchParams.getAll('types').every(type => type === 'op' || type === 'ed')) {
    return { kind: 'json', maxBytes: 256 * 1024 };
  }
  if (alias === 'malcdn' && /^\/images\/anime\/[a-z0-9_./-]+\.(?:avif|gif|jpe?g|png|webp)$/i.test(path) && !url.search) {
    return { kind: 'image', maxBytes: 4 * 1024 * 1024 };
  }
  if (alias === 'anilistcdn' &&
      /^\/file\/anilistcdn\/media\/anime\/cover\/[a-z0-9_./-]+\.(?:avif|gif|jpe?g|png|webp)$/i.test(path) &&
      !url.search) {
    return { kind: 'image', maxBytes: 4 * 1024 * 1024 };
  }
  return null;
}

function createSafeLookup(lookup = dns.lookup) {
  return (hostname, options, callback) => {
    const requested = typeof options === 'object' ? options : {};
    lookup(hostname, { ...requested, all: true, verbatim: true }, (error, addresses) => {
      if (error) return callback(error);
      const list = Array.isArray(addresses) ? addresses : [addresses];
      if (!list.length || list.some(item => !item?.address || isPrivateOrReservedIp(item.address))) {
        return callback(new Error('upstream resolved to a private or reserved address'));
      }
      if (requested.all) return callback(null, list);
      const selected = list[0];
      return callback(null, selected.address, selected.family);
    });
  };
}

const SAFE_CONTENT_TYPES = new Set([
  'application/json',
  'application/vnd.apple.mpegurl',
  'application/x-mpegurl',
  'audio/mpeg',
  'image/avif',
  'image/gif',
  'image/jpeg',
  'image/png',
  'image/webp',
  'text/plain',
  'video/mp2t',
  'video/mp4',
]);

function isSafeProxyContentType(contentType) {
  const mime = String(contentType || '').split(';', 1)[0].trim().toLowerCase();
  return SAFE_CONTENT_TYPES.has(mime);
}

function detectRasterContentType(body) {
  if (!Buffer.isBuffer(body)) return null;
  if (body.length >= 3 && body[0] === 0xff && body[1] === 0xd8 && body[2] === 0xff) return 'image/jpeg';
  if (body.length >= 8 && body.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (body.length >= 12 && body.toString('ascii', 0, 4) === 'RIFF' && body.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  if (body.length >= 6 && ['GIF87a', 'GIF89a'].includes(body.toString('ascii', 0, 6))) return 'image/gif';
  if (body.length >= 12 && body.toString('ascii', 4, 8) === 'ftyp' && /^(avif|avis)$/.test(body.toString('ascii', 8, 12))) return 'image/avif';
  return null;
}

function safeProxyHeaders(contentType, { cacheHit = false } = {}) {
  const mime = String(contentType || '').split(';', 1)[0].trim().toLowerCase();
  const headers = {
    'Content-Type': contentType,
    'Content-Security-Policy': "default-src 'none'; sandbox",
    'X-Content-Type-Options': 'nosniff',
  };
  if (cacheHit) headers['X-Cache'] = 'HIT';
  if (mime.startsWith('image/')) headers['Cache-Control'] = 'public, max-age=604800, immutable';
  else headers['Content-Disposition'] = 'attachment';
  return headers;
}

class ByteLruCache {
  constructor({ maxBytes, maxEntries }) {
    this.maxBytes = maxBytes;
    this.maxEntries = maxEntries;
    this.bytes = 0;
    this.map = new Map();
  }

  delete(key) {
    const old = this.map.get(key);
    if (!old) return false;
    this.bytes -= old.size;
    return this.map.delete(key);
  }

  get(key, now = Date.now()) {
    const value = this.map.get(key);
    if (!value) return null;
    if (value.exp <= now) {
      this.delete(key);
      return null;
    }
    this.map.delete(key);
    this.map.set(key, value);
    return value;
  }

  set(key, value) {
    const size = Number(value?.body?.length || 0);
    if (!Number.isSafeInteger(size) || size < 0 || size > this.maxBytes) return false;
    this.delete(key);
    this.map.set(key, { ...value, size });
    this.bytes += size;
    while (this.bytes > this.maxBytes || this.map.size > this.maxEntries) {
      this.delete(this.map.keys().next().value);
    }
    return this.map.has(key);
  }

  prune(now = Date.now()) {
    for (const [key, value] of this.map) {
      if (value.exp <= now) this.delete(key);
    }
  }
}

module.exports = {
  ByteLruCache,
  assertSafeHttpsUrl,
  createSafeLookup,
  detectRasterContentType,
  isPrivateOrReservedIp,
  isSafeProxyContentType,
  publicProxyPolicy,
  safeProxyHeaders,
};
