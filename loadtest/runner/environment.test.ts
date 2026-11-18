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
import { runnerEnvironment, type RunIdentity } from './environment.ts';

const run: RunIdentity = {
  testId: 'lt-abc123',
  topology: 'ha',
  profile: 'plateau',
  scenario: 'resolve',
  ingotVersion: '2.0.0',
};

test('the runner environment enables native histograms for the remote write output', () => {
  const environment = runnerEnvironment(run);

  assert.deepEqual(
    environment.variables.find((variable) => variable.name === 'K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM'),
    { name: 'K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM', value: 'true' },
  );
});

test('the runner environment writes the samples through the experimental prometheus remote write output', () => {
  const environment = runnerEnvironment(run);

  const outIndex = environment.arguments.indexOf('--out');
  assert.notEqual(outIndex, -1, 'the arguments name an output');
  assert.equal(environment.arguments[outIndex + 1], 'experimental-prometheus-rw');
});

test('the runner environment tags every sample with testid, topology, scenario, profile and ingot_version', () => {
  const environment = runnerEnvironment(run);

  const tags = environment.arguments
    .flatMap((argument, index) => (argument === '--tag' ? [environment.arguments[index + 1]] : []));
  assert.deepEqual(tags, [
    'testid=lt-abc123',
    'topology=ha',
    'scenario=resolve',
    'profile=plateau',
    'ingot_version=2.0.0',
  ]);
});

test('the runner environment refuses a tag value with a space and names the tag', () => {
  assert.throws(() => runnerEnvironment({ ...run, ingotVersion: '2.0.0 beta' }), /ingot_version/);
});

test('the runner environment refuses a test id that is not a lower case DNS label', () => {
  assert.throws(() => runnerEnvironment({ ...run, testId: 'LT_ABC' }), /testid/);
});
