'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { resolveBlockedFront, needsResolve, normalizeProxy } = require('../kodik-redirect');

const FRONT = 'https://cloud2.solodcdn.com/useruploads/abc/def:2026102507/720.mp4:hls:manifest.m3u8';
const TARGET = 'https://magnesium.cloud2.solodcdn.com/useruploads/abc/def:2026102507/720.mp4:hls:manifest.m3u8';
const PROXY = 'http://user:pass@proxy.example:8080'; // gitleaks:allow — фиктивный адрес для теста

test('every cloudN front host needs resolving, nodes and other hosts do not', () => {
  assert.equal(needsResolve('https://cloud.solodcdn.com/a.m3u8'), true);
  assert.equal(needsResolve(FRONT), true);
  assert.equal(needsResolve(TARGET), false);
  assert.equal(needsResolve('https://sky.solodcdn.com/x.m3u8'), false);
  assert.equal(needsResolve('not a url'), false);
});

test('proxy without a scheme is treated as http', () => {
  assert.equal(normalizeProxy('user:pass@proxy.example:8080'), PROXY);
  assert.equal(normalizeProxy(PROXY), PROXY);
  assert.equal(normalizeProxy('  '), '');
});

test('a front link is replaced with the redirect target', async () => {
  const out = await resolveBlockedFront(FRONT, PROXY, async () => ({ status: 302, location: TARGET }));
  assert.equal(out, TARGET);
});

test('direct and proxy routes race; whichever answers properly wins', async () => {
  const calls = [];
  const viaProxyOnly = async (_url, route) => {
    calls.push(route);
    if (!route) throw new Error('direct timeout');
    return { status: 302, location: TARGET };
  };
  assert.equal(await resolveBlockedFront(FRONT, PROXY, viaProxyOnly), TARGET);
  assert.deepEqual(calls.sort(), ['', PROXY]);

  const directOnly = async (_url, route) => {
    if (route) throw new Error('certificate has expired');
    return { status: 302, location: TARGET };
  };
  assert.equal(await resolveBlockedFront(FRONT, PROXY, directOnly), TARGET);
});

test('without a proxy the front is still resolved directly', async () => {
  const routes = [];
  const out = await resolveBlockedFront(FRONT, '', async (_url, route) => { routes.push(route); return { status: 302, location: TARGET }; });
  assert.equal(out, TARGET);
  assert.deepEqual(routes, ['']);
});

test('other hosts and failures on every route keep the original link', async () => {
  const never = async () => { throw new Error('must not be called'); };
  assert.equal(await resolveBlockedFront('https://sky.solodcdn.com/a.m3u8', PROXY, never), 'https://sky.solodcdn.com/a.m3u8');
  assert.equal(await resolveBlockedFront(FRONT, PROXY, async () => { throw new Error('down'); }), FRONT);
  assert.equal(await resolveBlockedFront(FRONT, PROXY, async () => ({ status: 200, location: null })), FRONT);
});

test('redirects outside the Kodik CDN or to another front are ignored', async () => {
  for (const location of ['https://evil.example/x.m3u8', 'http://magnesium.cloud2.solodcdn.com/x.m3u8', 'https://cloud.solodcdn.com/other', 'https://cloud3.solodcdn.com/x']) {
    assert.equal(await resolveBlockedFront(FRONT, PROXY, async () => ({ status: 302, location })), FRONT, location);
  }
});
