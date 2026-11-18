import assert from 'node:assert/strict';
import { test } from 'node:test';
import { BREAKPOINT_DEFAULT_CEILING, BREAKPOINT_DEFAULT_MINUTES, breakpointScenario } from './breakpoint.ts';

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

  assert.deepEqual(thresholds['http_req_duration'], [
    { threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '30s' },
  ]);
});

test('breakpointScenario takes the p95 limit from its argument', () => {
  const { thresholds } = breakpointScenario({ exec: 'mix', p95Ms: 2000 });

  assert.deepEqual(thresholds['http_req_duration'], [
    { threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '30s' },
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
