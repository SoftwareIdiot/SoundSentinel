import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateDecibelsFromRMS,
  mapDecibelsToScale,
  isSoundDetected,
  calculateStorageStats,
  identifyOldestToPrune,
  identifyExpiredRecordings,
  getRetentionDurationMs,
  formatBytes,
  formatDuration,
} from './cctvLogic';

test('calculateDecibelsFromRMS computes accurate logarithmic values', () => {
  assert.equal(calculateDecibelsFromRMS(1.0), 0);
  assert.equal(calculateDecibelsFromRMS(0.00001), -80);
  const half = calculateDecibelsFromRMS(0.5);
  assert.ok(half > -6.5 && half < -5.5);
});

test('mapDecibelsToScale converts dBFS to 0-100 range', () => {
  assert.equal(mapDecibelsToScale(-80), 0);
  assert.equal(mapDecibelsToScale(-70), 0);
  assert.equal(mapDecibelsToScale(0), 100);
  const mid = mapDecibelsToScale(-37.5);
  assert.ok(mid >= 45 && mid <= 55);
});

test('isSoundDetected validates threshold boundary', () => {
  assert.equal(isSoundDetected(50, 45), true);
  assert.equal(isSoundDetected(45, 45), true);
  assert.equal(isSoundDetected(44, 45), false);
});

test('calculateStorageStats detects near full warning and percentages', () => {
  const quota = 100 * 1024 * 1024; // 100 MB
  const recordings = [
    { sizeBytes: 40 * 1024 * 1024, isProtected: false },
    { sizeBytes: 46 * 1024 * 1024, isProtected: true },
  ]; // Total 86 MB = 86%

  const stats = calculateStorageStats(recordings, quota, 85);
  assert.equal(stats.recordingsCount, 2);
  assert.equal(stats.protectedCount, 1);
  assert.equal(stats.usedPercentage, 86);
  assert.equal(stats.isNearFull, true);
  assert.equal(stats.isFull, false);
});

test('identifyOldestToPrune deletes oldest unprotected recordings only', () => {
  const recordings = [
    { id: 'rec-1', timestamp: 1000, sizeBytes: 10 * 1024 * 1024, isProtected: true }, // Oldest but protected!
    { id: 'rec-2', timestamp: 2000, sizeBytes: 15 * 1024 * 1024, isProtected: false }, // Oldest unprotected
    { id: 'rec-3', timestamp: 3000, sizeBytes: 20 * 1024 * 1024, isProtected: false },
    { id: 'rec-4', timestamp: 4000, sizeBytes: 10 * 1024 * 1024, isProtected: false },
  ];

  // Need 25 MB freed
  const prunedIds = identifyOldestToPrune(recordings, 25 * 1024 * 1024);
  // rec-2 gives 15MB, rec-3 gives 20MB -> total 35MB >= 25MB
  assert.deepEqual(prunedIds, ['rec-2', 'rec-3']);
  // rec-1 must NOT be in prunedIds
  assert.ok(!prunedIds.includes('rec-1'));
});

test('identifyExpiredRecordings finds recordings exceeding retention window', () => {
  const now = 10000000;
  const ONE_HOUR = 3600 * 1000;
  const recordings = [
    { id: 'rec-old', timestamp: now - 3 * ONE_HOUR, isProtected: false }, // 3h old
    { id: 'rec-old-protected', timestamp: now - 3 * ONE_HOUR, isProtected: true }, // 3h old but protected
    { id: 'rec-new', timestamp: now - 0.5 * ONE_HOUR, isProtected: false }, // 30min old
  ];

  const expired = identifyExpiredRecordings(recordings, 2 * ONE_HOUR, now);
  assert.deepEqual(expired, ['rec-old']);
});

test('getRetentionDurationMs returns proper millisecond values', () => {
  assert.equal(getRetentionDurationMs('1h'), 3600000);
  assert.equal(getRetentionDurationMs('24h'), 24 * 3600000);
  assert.equal(getRetentionDurationMs('never'), Infinity);
  assert.equal(getRetentionDurationMs('custom', 12), 12 * 3600000);
});

test('formatBytes and formatDuration format cleanly', () => {
  assert.equal(formatBytes(0), '0 B');
  assert.equal(formatBytes(1024), '1.0 KB');
  assert.equal(formatBytes(50 * 1024 * 1024), '50.0 MB');

  assert.equal(formatDuration(0), '00:00');
  assert.equal(formatDuration(65), '01:05');
  assert.equal(formatDuration(3665), '1:01:05');
});
