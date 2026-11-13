'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const analytics = require('../analytics-store');

test('analytics counts downloads, first opens, active users, online and session time', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-analytics-'));
  const file = path.join(directory, 'analytics.json');
  const start = Date.UTC(2026, 7, 4, 10, 0, 0);
  analytics.recordDownload(file, start);
  analytics.recordHeartbeat(file, { installId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', platform: 'android', version: '1.0', event: 'start' }, 7, start);
  analytics.recordHeartbeat(file, { installId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', platform: 'android', version: '1.0', event: 'heartbeat' }, 7, start + 60000);
  analytics.recordHeartbeat(file, { installId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', platform: 'web', version: 'web', event: 'start' }, null, start + 120000);
  const value = analytics.summary(file, start + 120000);
  assert.equal(value.totalDownloads, 1);
  assert.equal(value.totalInstalls, 2);
  assert.equal(value.today.firstOpens, 2);
  assert.equal(value.today.active, 2);
  assert.equal(value.today.android, 1);
  assert.equal(value.today.web, 1);
  assert.equal(value.online, 2);
  assert.equal(value.onlineAndroid, 1);
  assert.equal(value.onlineWeb, 1);
  assert.equal(value.today.sessionMinutes, 1);
  fs.rmSync(directory, { recursive: true, force: true });
});

test('weekly report marker can only be claimed once', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-analytics-'));
  const file = path.join(directory, 'analytics.json');
  const now = Date.UTC(2026, 7, 4);
  assert.equal(analytics.claimWeeklyReport(file, now), true);
  assert.equal(analytics.claimWeeklyReport(file, now), false);
  fs.rmSync(directory, { recursive: true, force: true });
});

test('stale installs older than 180 days are pruned while totalInstalls is preserved', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-analytics-'));
  const file = path.join(directory, 'analytics.json');
  const day0 = Date.UTC(2026, 0, 1, 10, 0, 0);
  const day200 = day0 + 200 * 86400000;
  analytics.recordHeartbeat(file, { installId: '11111111-1111-1111-1111-111111111111', platform: 'android', version: '0.7.0', event: 'start' }, null, day0);
  analytics.recordHeartbeat(file, { installId: '22222222-2222-2222-2222-222222222222', platform: 'android', version: '1.0.0', event: 'start' }, null, day200);
  const s = analytics.summary(file, day200);
  assert.equal(s.totalInstalls, 2);
  const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  assert.equal(Object.keys(raw.installs).length, 1);
  assert.equal(raw.archivedInstalls, 1);
  assert.ok(raw.installs['22222222-2222-2222-2222-222222222222']);
  fs.rmSync(directory, { recursive: true, force: true });
});

test('steady heartbeats are buffered and persisted cleanly on flush', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'anipulse-analytics-'));
  const file = path.join(directory, 'analytics.json');
  const t0 = Date.UTC(2026, 7, 4, 10, 0, 0);
  analytics.recordHeartbeat(file, { installId: '33333333-3333-3333-3333-333333333333', platform: 'web', version: 'web', event: 'start' }, null, t0);
  analytics.recordHeartbeat(file, { installId: '33333333-3333-3333-3333-333333333333', platform: 'web', version: 'web', event: 'heartbeat' }, null, t0 + 120000);
  const inMemory = analytics.summary(file, t0 + 120000);
  assert.equal(inMemory.today.sessionMinutes, 2);
  const onDiskBeforeFlush = JSON.parse(fs.readFileSync(file, 'utf8'));
  assert.equal(onDiskBeforeFlush.installs['33333333-3333-3333-3333-333333333333'].totalMs, 0);
  assert.equal(analytics.flush(file), true);
  const onDiskAfterFlush = JSON.parse(fs.readFileSync(file, 'utf8'));
  assert.equal(onDiskAfterFlush.installs['33333333-3333-3333-3333-333333333333'].totalMs, 120000);
  fs.rmSync(directory, { recursive: true, force: true });
});
