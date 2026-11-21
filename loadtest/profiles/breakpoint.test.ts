import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  BREAKPOINT_DEFAULT_CEILING,
  BREAKPOINT_DEFAULT_MINUTES,
  breakpointRateAt,
  breakpointScenario,
} from './breakpoint.ts';

test('breakpointScenario uses the ramping-arrival-rate executor', () => {
  const { scenario } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.equal(scenario.executor, 'ramping-arrival-rate');
});

test('breakpointScenario starts at one request per second', () => {
  const { scenario } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.equal(scenario.startRate, 1);
});

test('breakpointScenario ramps to the default ceiling over the default minutes', () => {
  const { scenario } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.equal(BREAKPOINT_DEFAULT_CEILING, 1000);
  assert.equal(BREAKPOINT_DEFAULT_MINUTES, 10);
  assert.deepEqual(scenario.stages, [{ target: 1000, duration: '10m' }]);
});

test('breakpointScenario ramps to a configured ceiling over a configured duration', () => {
  const { scenario } = breakpointScenario({ exec: 'mix', p95Ms: 500, ceiling: 300, minutes: 4 });

  assert.deepEqual(scenario.stages, [{ target: 300, duration: '4m' }]);
});

test('breakpointScenario aborts the run when the p95 threshold breaks', () => {
  const { thresholds } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.deepEqual(thresholds['http_req_duration{phase:ramp}'], [
    { threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '150s' },
  ]);
});

test('breakpointScenario takes the p95 limit from its argument', () => {
  const { thresholds } = breakpointScenario({ exec: 'mix', p95Ms: 2000 });

  assert.deepEqual(thresholds['http_req_duration{phase:ramp}'], [
    { threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '150s' },
  ]);
});

test('breakpointScenario runs the given exec function', () => {
  const { scenario } = breakpointScenario({ exec: 'resolve', p95Ms: 150 });

  assert.equal(scenario.exec, 'resolve');
});

test('breakpointScenario refuses a ceiling below one request per second and names it', () => {
  assert.throws(() => breakpointScenario({ exec: 'mix', p95Ms: 500, ceiling: 0 }), /ceiling of 0/);
});

test('breakpointScenario refuses a ceiling that is not a finite number', () => {
  assert.throws(() => breakpointScenario({ exec: 'mix', p95Ms: 500, ceiling: Number.NaN }), /ceiling/);
});

test('breakpointScenario refuses a duration under one minute and names it', () => {
  assert.throws(() => breakpointScenario({ exec: 'mix', p95Ms: 500, minutes: 0 }), /0 minute/);
});

test('breakpointScenario keeps the threshold but does not abort when abortOnFail is off', () => {
  const { thresholds } = breakpointScenario({ exec: 'mix', p95Ms: 500, abortOnFail: false });

  assert.deepEqual(thresholds['http_req_duration{phase:ramp}'], [
    { threshold: 'p(95)<500', abortOnFail: false, delayAbortEval: '150s' },
  ]);
});

test('breakpointScenario also breaks on the error rate, because a refused request counts as a fast one', () => {
  const { thresholds } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.deepEqual(thresholds['http_req_failed{phase:ramp}'], [
    { threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '150s' },
  ]);
});

test('breakpointScenario warms the server up at a low constant rate before the ramp', () => {
  const { warmup } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.equal(warmup.executor, 'constant-arrival-rate');
  assert.equal(warmup.rate, 5);
  assert.equal(warmup.duration, '120s');
  assert.equal(warmup.exec, 'mix');
});

test('breakpointScenario starts the ramp when the warm-up ends', () => {
  const { scenario } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.equal(scenario.startTime, '120s');
});

test('breakpointScenario tags the warm-up and the ramp requests with their phase', () => {
  const { warmup, scenario } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.deepEqual(warmup.tags, { phase: 'warmup' });
  assert.deepEqual(scenario.tags, { phase: 'ramp' });
});

test('breakpointScenario counts only ramp requests in its thresholds, so a slow warm-up cannot abort the run', () => {
  const { thresholds } = breakpointScenario({ exec: 'mix', p95Ms: 500 });

  assert.deepEqual(Object.keys(thresholds).sort(), ['http_req_duration{phase:ramp}', 'http_req_failed{phase:ramp}']);
});

test('breakpointRateAt reports no rate while the warm-up runs', () => {
  assert.equal(breakpointRateAt(119), undefined);
});

test('breakpointRateAt reads the rate from the ramp start, not from the start of the run', () => {
  assert.equal(breakpointRateAt(120), 1);
  assert.equal(breakpointRateAt(120 + 300), 1 + 999 / 2);
});

test('breakpointRateAt follows a configured ceiling and ramp duration', () => {
  assert.equal(breakpointRateAt(120 + 60, { ceiling: 241, minutes: 4 }), 61);
});

test('breakpointRateAt stays at the ceiling once the ramp has reached it', () => {
  assert.equal(breakpointRateAt(120 + 3600), 1000);
});
