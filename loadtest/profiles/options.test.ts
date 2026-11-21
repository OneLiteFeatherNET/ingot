import assert from 'node:assert/strict';
import { test } from 'node:test';
import { profileOptions } from './options.ts';

test('the breakpoint profile ramps the scenario and aborts on its p95 limit', () => {
  const options = profileOptions({ LOADTEST_PROFILE: 'breakpoint' }, 'resolve', 150);

  assert.equal(options.scenarios?.resolve?.executor, 'ramping-arrival-rate');
  assert.deepEqual(options.thresholds?.http_req_duration, [
    { threshold: 'p(95)<150', abortOnFail: true, delayAbortEval: '30s' },
  ]);
});

test('the breakpoint profile takes its ceiling and ramp minutes from the script variables', () => {
  const options = profileOptions(
    { LOADTEST_PROFILE: 'breakpoint', LOADTEST_CEILING: '300', LOADTEST_RAMP_MINUTES: '4' },
    'resolve',
    150,
  );

  const scenario = options.scenarios?.resolve;
  assert.ok(scenario?.executor === 'ramping-arrival-rate');
  assert.deepEqual(scenario.stages, [{ target: 300, duration: '4m' }]);
});

test('the plateau profile runs its five steps from the breakpoint in LOADTEST_BREAKPOINT', () => {
  const options = profileOptions({ LOADTEST_PROFILE: 'plateau', LOADTEST_BREAKPOINT: '100' }, 'resolve', 150);

  assert.deepEqual(Object.keys(options.scenarios ?? {}), [
    'plateau-1',
    'plateau-2',
    'plateau-3',
    'plateau-4',
    'plateau-5',
    'verdict-1',
    'verdict-2',
    'verdict-3',
    'verdict-4',
    'verdict-5',
  ]);
});

test('the plateau verdicts carry the p95 limit and the name of the scenario', () => {
  const options = profileOptions({ LOADTEST_PROFILE: 'plateau', LOADTEST_BREAKPOINT: '100' }, 'resolve', 150);

  const verdict = options.scenarios?.['verdict-1'];
  assert.equal(verdict?.env?.LOADTEST_STEP_P95_MS, '150');
  assert.equal(verdict?.env?.LOADTEST_STEP_SCENARIO, 'resolve');
});

test('the plateau profile is refused without a breakpoint, and the refusal names the variable', () => {
  assert.throws(() => profileOptions({ LOADTEST_PROFILE: 'plateau' }, 'resolve', 150), /LOADTEST_BREAKPOINT/);
});

test('the soak profile holds the rate in LOADTEST_RATE', () => {
  const options = profileOptions({ LOADTEST_PROFILE: 'soak', LOADTEST_RATE: '40' }, 'resolve', 150);

  const scenario = options.scenarios?.resolve;
  assert.ok(scenario?.executor === 'constant-arrival-rate');
  assert.equal(scenario.rate, 40);
});

test('the soak profile is refused without a rate, and the refusal names the variable', () => {
  assert.throws(() => profileOptions({ LOADTEST_PROFILE: 'soak' }, 'resolve', 150), /LOADTEST_RATE/);
});

test('a missing or unknown profile is refused, so the script never falls back to one iteration', () => {
  assert.throws(() => profileOptions({}, 'resolve', 150), /LOADTEST_PROFILE/);
  assert.throws(() => profileOptions({ LOADTEST_PROFILE: 'spike' }, 'resolve', 150), /spike/);
});

test('a number variable that is not a number is refused with its name', () => {
  assert.throws(
    () => profileOptions({ LOADTEST_PROFILE: 'breakpoint', LOADTEST_CEILING: 'many' }, 'resolve', 150),
    /LOADTEST_CEILING/,
  );
});

test('the breakpoint profile runs the whole ramp when LOADTEST_ABORT_ON_FAIL is false', () => {
  const options = profileOptions({ LOADTEST_PROFILE: 'breakpoint', LOADTEST_ABORT_ON_FAIL: 'false' }, 'resolve', 150);

  assert.deepEqual(options.thresholds?.http_req_duration, [
    { threshold: 'p(95)<150', abortOnFail: false, delayAbortEval: '30s' },
  ]);
});
