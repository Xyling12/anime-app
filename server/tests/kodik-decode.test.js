'use strict';

const assert = require('node:assert/strict');
const { test } = require('node:test');
const { kodikDecode, shift } = require('../kodik-decode');

const URL = '//cloud.solodcdn.com/useruploads/5bb9bc73-c81/abc:2026100420/720.mp4:hls:manifest.m3u8';

function encode(url, s) {
  // Обратный сдвиг: kodikDecode сдвигает на s вперёд, значит кодируем на 26 - s.
  return shift(Buffer.from(url).toString('base64'), (26 - s) % 26);
}

test('decodes a stream link for every Caesar shift, including zero', () => {
  for (let s = 0; s < 26; s++) assert.equal(kodikDecode(encode(URL, s)), URL, `shift ${s}`);
});

test('never returns non-URL garbage, even if it contains //', () => {
  const garbage = Buffer.from('b_p\u0001?u0//ÿf i"101').toString('base64');
  assert.equal(kodikDecode(garbage), null);
  assert.equal(kodikDecode(''), null);
  assert.equal(kodikDecode(null), null);
});
