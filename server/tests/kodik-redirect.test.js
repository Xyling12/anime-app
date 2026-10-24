'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { resolveBlockedFront, needsResolve, normalizeProxy } = require('../kodik-redirect');

const BLOCKED = 'https://cloud.solodcdn.com/useruploads/abc/def:2026102503/720.mp4:hls:manifest.m3u8';
const TARGET = 'https://magnesium.cloud.solodcdn.com/useruploads/abc/def:2026102503/720.mp4:hls:manifest.m3u8';
const PROXY = 'http://user:pass@proxy.example:8080'; // gitleaks:allow — фиктивный адрес для теста

test('only the blocked front host needs resolving', () => {
  assert.equal(needsResolve(BLOCKED), true);
  assert.equal(needsResolve('https://sky.solodcdn.com/x.m3u8'), false);
  assert.equal(needsResolve('not a url'), false);
});

test('proxy without a scheme is treated as http', () => {
  assert.equal(normalizeProxy('user:pass@proxy.example:8080'), PROXY);
  assert.equal(normalizeProxy(PROXY), PROXY);
  assert.equal(normalizeProxy('  '), '');
});

test('a blocked front link is replaced with the redirect target', async () => {
  const out = await resolveBlockedFront(BLOCKED, PROXY, async () => ({ status: 302, location: TARGET }));
  assert.equal(out, TARGET);
});

test('other hosts, a missing proxy and errors keep the original link', async () => {
  const never = async () => { throw new Error('must not be called'); };
  assert.equal(await resolveBlockedFront('https://sky.solodcdn.com/a.m3u8', PROXY, never), 'https://sky.solodcdn.com/a.m3u8');
  assert.equal(await resolveBlockedFront(BLOCKED, '', never), BLOCKED);
  assert.equal(await resolveBlockedFront(BLOCKED, PROXY, async () => { throw new Error('down'); }), BLOCKED);
  assert.equal(await resolveBlockedFront(BLOCKED, PROXY, async () => ({ status: 200, location: null })), BLOCKED);
});

test('redirects outside the Kodik CDN or back to the blocked host are ignored', async () => {
  for (const location of ['https://evil.example/x.m3u8', 'http://magnesium.cloud.solodcdn.com/x.m3u8', 'https://cloud.solodcdn.com/other']) {
    assert.equal(await resolveBlockedFront(BLOCKED, PROXY, async () => ({ status: 302, location })), BLOCKED, location);
  }
});
