'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { createRateLimiter } = require('../upstream-throttle');

const shikimoriLimits = [
  { windowMs: 1000, max: 5 },
  { windowMs: 60_000, max: 85 },
];

/** Сколько стартов попадает в любое окно windowMs — проверка, что лимит не превышен. */
function maxInWindow(starts, windowMs) {
  let best = 0;
  for (let i = 0; i < starts.length; i++) {
    const inWindow = starts.filter((s) => s >= starts[i] && s < starts[i] + windowMs).length;
    best = Math.max(best, inWindow);
  }
  return best;
}

test('rate limiter lets 5 through at once and queues the rest by the second', () => {
  const clock = 0;
  const limiter = createRateLimiter({ limits: shikimoriLimits, maxWaitMs: 6000, now: () => clock });
  const waits = Array.from({ length: 12 }, () => limiter.take());
  assert.deepEqual(waits, [0, 0, 0, 0, 0, 1000, 1000, 1000, 1000, 1000, 2000, 2000]);
});

test('rate limiter refuses instead of queueing longer than maxWaitMs', () => {
  const clock = 0;
  const limiter = createRateLimiter({ limits: shikimoriLimits, maxWaitMs: 6000, now: () => clock });
  const waits = Array.from({ length: 40 }, () => limiter.take());
  // 5 в секунду на 0…6 с = 35 запросов, остальным — отказ, чтобы отдать копию.
  assert.equal(waits.filter((w) => w !== null).length, 35);
  assert.ok(waits.slice(35).every((w) => w === null));
});

test('rate limiter never exceeds 5 per second or 85 per minute over a long crawl', () => {
  let clock = 0;
  const limiter = createRateLimiter({ limits: shikimoriLimits, maxWaitMs: 6000, now: () => clock });
  const starts = [];
  // Робот стучится 10 раз в секунду три минуты подряд.
  for (let i = 0; i < 1800; i++) {
    clock = i * 100;
    const wait = limiter.take();
    if (wait !== null) starts.push(clock + wait);
  }
  starts.sort((a, b) => a - b);
  assert.ok(maxInWindow(starts, 1000) <= 5, 'per-second limit');
  assert.ok(maxInWindow(starts, 60_000) <= 85, 'per-minute limit');
  assert.ok(starts.length >= 85 * 3 - 5, `throughput ${starts.length}`);
});
