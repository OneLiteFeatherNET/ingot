/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { evaluateStep, type StepWindow } from './pass-rule.ts';
import { MAX_ERROR_RATE } from './thresholds.ts';

const LIMITS = { p95Ms: 150, maxErrorRate: MAX_ERROR_RATE } as const;

/** One minute of a step that meets every threshold; a test overrides the one value it is about. */
function minute(overrides: Partial<StepWindow> = {}): StepWindow {
  return { p95Ms: 100, errorRate: 0, droppedIterations: 0, ...overrides };
}

/** A hold in which every minute is healthy. */
function healthyHold(minutes = 15): StepWindow[] {
  return Array.from({ length: minutes }, () => minute());
}

test('a step with every minute healthy passes', () => {
  assert.deepEqual(evaluateStep(healthyHold(), LIMITS), { passed: true });
});

const P95_CASES: readonly (readonly [string, number, boolean])[] = [
  ['a p95 one millisecond under the limit passes', 149, true],
  ['a p95 exactly at the limit does not pass', 150, false],
  ['a p95 above the limit does not pass', 151, false],
];

for (const [name, p95Ms, passes] of P95_CASES) {
  test(name, () => {
    const verdict = evaluateStep([minute({ p95Ms })], LIMITS);
    assert.equal(verdict.passed, passes, `p95 ${p95Ms} ms against the limit of ${LIMITS.p95Ms} ms`);
  });
}

const ERROR_RATE_CASES: readonly (readonly [string, number, boolean])[] = [
  ['an error rate of 0.9 percent passes', 0.009, true],
  ['an error rate of exactly 1 percent does not pass', 0.01, false],
  ['an error rate above 1 percent does not pass', 0.02, false],
];

for (const [name, errorRate, passes] of ERROR_RATE_CASES) {
  test(name, () => {
    const verdict = evaluateStep([minute({ errorRate })], LIMITS);
    assert.equal(verdict.passed, passes, `error rate ${errorRate} against the limit of ${LIMITS.maxErrorRate}`);
  });
}

test('a single dropped iteration in one minute fails the step', () => {
  assert.deepEqual(evaluateStep([minute({ droppedIterations: 1 })], LIMITS), {
    passed: false,
    reason: 'breach',
    window: 0,
    breach: 'dropped-iterations',
  });
});

test('a step that breaks in its last minute does not pass', () => {
  const windows = healthyHold(15);
  windows[14] = minute({ p95Ms: 900 });
  assert.deepEqual(evaluateStep(windows, LIMITS), {
    passed: false,
    reason: 'breach',
    window: 14,
    breach: 'p95',
  });
});

test('a step that breaks in its first minute does not pass', () => {
  const windows = healthyHold(15);
  windows[0] = minute({ errorRate: 0.5 });
  assert.deepEqual(evaluateStep(windows, LIMITS), {
    passed: false,
    reason: 'breach',
    window: 0,
    breach: 'error-rate',
  });
});

test('a step without any measured minute does not pass', () => {
  assert.deepEqual(evaluateStep([], LIMITS), { passed: false, reason: 'no-windows' });
});

test('a missing p95 measurement does not pass', () => {
  assert.equal(evaluateStep([minute({ p95Ms: Number.NaN })], LIMITS).passed, false);
});
