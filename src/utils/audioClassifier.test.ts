import test from 'node:test';
import assert from 'node:assert/strict';
import { clientHeuristicClassify } from './audioClassifier';

test('clientHeuristicClassify detects glass breaking as high threat', () => {
  const result = clientHeuristicClassify(
    { peakDecibels: 88, avgDecibels: 68, duration: 3 },
    'Glass Breaking'
  );
  assert.equal(result.tag, 'Glass Breaking');
  assert.equal(result.threatLevel, 'high');
  assert.equal(result.category, 'threat');
  assert.ok(result.confidence >= 0.85);
});

test('clientHeuristicClassify detects footsteps as medium threat intrusion', () => {
  const result = clientHeuristicClassify(
    { peakDecibels: 48, avgDecibels: 35, duration: 4 },
    'Footsteps'
  );
  assert.equal(result.tag, 'Footsteps');
  assert.equal(result.threatLevel, 'medium');
  assert.equal(result.category, 'intrusion');
});

test('clientHeuristicClassify detects human voice as low threat speech', () => {
  const result = clientHeuristicClassify(
    { peakDecibels: 58, avgDecibels: 48, duration: 5 },
    'Human Voice / Speech'
  );
  assert.equal(result.tag, 'Human Voice / Speech');
  assert.equal(result.threatLevel, 'low');
  assert.equal(result.category, 'speech');
});

test('clientHeuristicClassify does not guess a sound class from loudness alone', () => {
  const result = clientHeuristicClassify(
    { peakDecibels: 92, avgDecibels: 70, duration: 2 }
  );
  assert.equal(result.tag, 'Ambient Noise');
  assert.equal(result.threatLevel, 'info');
  assert.ok(result.confidence < 0.6);
});
