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
