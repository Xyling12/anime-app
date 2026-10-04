'use strict';

// Ссылки на поток Kodik приходят как base64 после сдвига Цезаря с неизвестным
// шагом. Раньше шлюз перебирал шаги 1..25 и брал первый результат, в котором
// встречается «//». Мусор после неверного шага тоже иногда содержит «//», и
// клиент получал битую ссылку: у «Фрирен» так ломалась 5-я серия из 28 —
// человек жал на серию и видел ошибку воспроизведения. Теперь результат
// принимается, только если это действительно URL, и проверяется шаг 0.

const URL_RE = /^(?:https?:)?\/\/[a-z0-9.-]+\.[a-z]{2,}(?::\d+)?\/[\x21-\x7e]*$/i;

function shift(src, s) {
  let out = '';
  for (const c of src) {
    const code = c.charCodeAt(0);
    if (code >= 97 && code <= 122) out += String.fromCharCode((code - 97 + s) % 26 + 97);
    else if (code >= 65 && code <= 90) out += String.fromCharCode((code - 65 + s) % 26 + 65);
    else out += c;
  }
  return out;
}

function kodikDecode(src) {
  if (typeof src !== 'string' || !src) return null;
  for (let s = 0; s < 26; s++) {
    let decoded;
    try { decoded = Buffer.from(shift(src, s), 'base64').toString('utf8'); } catch (_) { continue; }
    if (URL_RE.test(decoded)) return decoded;
  }
  return null;
}

module.exports = { kodikDecode, shift };
