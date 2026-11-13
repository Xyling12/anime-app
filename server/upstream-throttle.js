'use strict';

/**
 * Ограничитель запросов к чужому API со скользящими окнами.
 *
 * Shikimori пускает не больше 5 запросов в секунду и 90 в минуту с одного IP, а
 * через шлюз к нему ходят все посетители сайта, приложение и поисковые роботы
 * разом. Сверх лимита он отвечал 429, и карточка тайтла падала (08.10.2026: 4 из
 * 20 непрогретых карточек). Ограничитель выдаёт каждому запросу время старта,
 * при котором ни одно окно не переполнено; лишние встают в очередь. Если ждать
 * пришлось бы дольше `maxWaitMs`, take() возвращает null — вызывающий отдаёт
 * сохранённую копию или ошибку, вместо того чтобы держать запрос минуту.
 *
 * @param {{ limits: { windowMs: number, max: number }[], maxWaitMs: number, now?: () => number }} options
 */
function createRateLimiter({ limits, maxWaitMs, now = () => Date.now() }) {
  const longestWindow = Math.max(...limits.map((l) => l.windowMs));
  const largestMax = Math.max(...limits.map((l) => l.max));
  // Времена старта уже выданных запросов, по возрастанию (бывают и в будущем — очередь).
  let starts = [];

  return {
    /** Сколько миллисекунд подождать перед запросом, или null — очередь слишком длинная. */
    take() {
      const t = now();
      starts = starts.filter((s) => s > t - longestWindow);
      let at = Math.max(t, starts.length ? starts[starts.length - 1] : t);
      for (const { windowMs, max } of limits) {
        // В окне до `at` уже max запросов — ждём, пока самый ранний из них из окна выйдет.
        if (starts.length >= max) at = Math.max(at, starts[starts.length - max] + windowMs);
      }
      if (at - t > maxWaitMs) return null;
      starts.push(at);
      if (starts.length > largestMax) starts = starts.slice(-largestMax);
      return at - t;
    },
  };
}

module.exports = { createRateLimiter };
