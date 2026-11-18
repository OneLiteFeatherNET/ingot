import assert from 'node:assert/strict';
import { test } from 'node:test';
import { STEP_PASSED_METRIC, type MetricSample, type MetricSink, emitStepPassed } from './step-metric.ts';

interface FakeSink extends MetricSink {
  readonly samples: MetricSample[];
}

function fakeSink(): FakeSink {
  const samples: MetricSample[] = [];
  return {
    samples,
    add(name, value, tags) {
      samples.push({ name, value, tags });
    },
  };
}

test('emitStepPassed reports the metric ingot_load_step_passed', () => {
  const sink = fakeSink();

  emitStepPassed(sink, { rate: 120, passed: true });

  assert.equal(STEP_PASSED_METRIC, 'ingot_load_step_passed');
  assert.equal(sink.samples[0]?.name, 'ingot_load_step_passed');
});

test('emitStepPassed carries the step rate as the value', () => {
  const sink = fakeSink();

  emitStepPassed(sink, { rate: 120, passed: true });

  assert.equal(sink.samples[0]?.value, 120);
});

test('emitStepPassed tags a passing step with result pass', () => {
  const sink = fakeSink();

  emitStepPassed(sink, { rate: 120, passed: true });

  assert.deepEqual(sink.samples[0]?.tags, { result: 'pass' });
});

test('emitStepPassed tags a failing step with result fail and still reports its rate', () => {
  const sink = fakeSink();

  emitStepPassed(sink, { rate: 160, passed: false });

  assert.deepEqual(sink.samples, [{ name: 'ingot_load_step_passed', value: 160, tags: { result: 'fail' } }]);
});

test('emitStepPassed emits exactly one sample per step', () => {
  const sink = fakeSink();

  emitStepPassed(sink, { rate: 1, passed: true });

  assert.equal(sink.samples.length, 1);
});

test('emitStepPassed refuses a negative rate and names it', () => {
  const sink = fakeSink();

  assert.throws(() => emitStepPassed(sink, { rate: -1, passed: true }), /rate of -1/);
  assert.equal(sink.samples.length, 0);
});

test('emitStepPassed refuses a rate that is not a finite number', () => {
  assert.throws(() => emitStepPassed(fakeSink(), { rate: Number.NaN, passed: true }), /rate/);
});
