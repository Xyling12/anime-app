const { publicProxyPolicy } = require('../proxy-security.js');
const cases = [
  ['shikimori', 'https://shikimori.io/api/animes?limit=30', 'json'],
  ['shikimori', 'https://shikimori.io/api/animes/1', 'json'],
  ['shikimori', 'https://shikimori.io/api/animes/1/similar', 'json'],
  ['shikimori', 'https://shikimori.io/api/animes/1/related', 'json'],
  ['shikimori', 'https://shikimori.io/api/animes/1/franchise', 'json'],
  ['shikimori', 'https://shikimori.io/api/animes/1/anything-else', null],
  ['shikimori', 'https://shikimori.io/api/animes/1/related?x=1', null],
  ['shikimori', 'https://shikimori.io/system/animes/preview/1.jpg', 'image'],
  ['anilibria', 'https://anilibria.top/api/v1/anime/releases/1', 'json'],
];
let pass = 0, fail = 0;
for (const [a, t, exp] of cases) {
  const got = publicProxyPolicy(a, t)?.kind ?? null;
  const ok = got === exp;
  if (ok) pass++; else fail++;
  console.log(`${ok ? 'OK ' : 'FAIL'} ${a} ${t} -> ${got} (expected ${exp})`);
}
console.log(`\n${pass} pass / ${fail} fail`);
process.exit(fail ? 1 : 0);
