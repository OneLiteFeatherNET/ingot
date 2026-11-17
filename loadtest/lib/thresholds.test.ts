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
import { SCENARIOS } from './config.ts';
import { MAX_ERROR_RATE, THRESHOLDS, thresholdFor } from './thresholds.ts';

const DESIGN_SCENARIOS = [
  'resolve',
  'resolve-miss',
  'metadata',
  'deploy',
  'mirror',
  'auth',
  'search-browse',
  'dashboard',
  'mix',
] as const;

test('every scenario named in the design has a threshold entry', () => {
  for (const scenario of DESIGN_SCENARIOS) {
    assert.ok(
      THRESHOLDS.some((entry) => entry.scenario === scenario),
      `no threshold for scenario ${scenario}`,
    );
  }
});

test('the scenario list of the config matches the scenarios of the design', () => {
  assert.deepEqual([...SCENARIOS].sort(), [...DESIGN_SCENARIOS].sort());
});

test('the resolve p95 threshold is 150 ms as the design states', () => {
  assert.equal(thresholdFor('resolve').p95Ms, 150);
});

test('the deploy p95 threshold for the 1 MB upload is 2 seconds as the design states', () => {
  assert.equal(thresholdFor('deploy', '1MB').p95Ms, 2_000);
});

test('the error rate limit of a passing step is one percent', () => {
  assert.equal(MAX_ERROR_RATE, 0.01);
});

test('thresholdFor refuses a deploy without a size', () => {
  assert.throws(() => thresholdFor('deploy'), /deploy/);
});
