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
import { remoteWriteUrl, runnerEnvironment, scriptVariables, type RunIdentity, type RunTargets } from './environment.ts';

const run: RunIdentity = {
  testId: 'lt-abc123',
  topology: 'ha',
  profile: 'plateau',
  scenario: 'resolve',
  ingotVersion: '2.0.0',
};

const targets: RunTargets = {
  remoteWriteUrl: 'http://metrics.example.test/api/v1/push',
  baseUrl: 'http://ingot-loadtest:8080',
};

function variable(name: string): string | undefined {
  return runnerEnvironment(run, targets).variables.find((entry) => entry.name === name)?.value;
}

test('the runner environment enables native histograms for the remote write output', () => {
  const environment = runnerEnvironment(run, targets);

  assert.deepEqual(
    environment.variables.find((variable) => variable.name === 'K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM'),
    { name: 'K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM', value: 'true' },
  );
});

test('the runner environment writes the samples through the experimental prometheus remote write output', () => {
  const environment = runnerEnvironment(run, targets);

  const outIndex = environment.arguments.indexOf('--out');
  assert.notEqual(outIndex, -1, 'the arguments name an output');
  assert.equal(environment.arguments[outIndex + 1], 'experimental-prometheus-rw');
});

test('the runner environment tags every sample with testid, topology, scenario, profile and ingot_version', () => {
  const environment = runnerEnvironment(run, targets);

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
  assert.throws(() => runnerEnvironment({ ...run, ingotVersion: '2.0.0 beta' }, targets), /ingot_version/);
});

test('the runner environment refuses a test id that is not a lower case DNS label', () => {
  assert.throws(() => runnerEnvironment({ ...run, testId: 'LT_ABC' }, targets), /testid/);
});

test('the runner environment sends the samples to the configured remote write url', () => {
  assert.equal(variable('K6_PROMETHEUS_RW_SERVER_URL'), 'http://metrics.example.test/api/v1/push');
});

test('the runner environment refuses a remote write url that is not http or https', () => {
  assert.throws(
    () => runnerEnvironment(run, { ...targets, remoteWriteUrl: 'metrics.example.test:9090' }),
    /remote write url "metrics.example.test:9090"/,
  );
});

test('the runner environment points the scenario at the ingot service through BASE_URL', () => {
  assert.equal(variable('BASE_URL'), 'http://ingot-loadtest:8080');
});

test('the runner environment hands the profile to the script as LOADTEST_PROFILE', () => {
  const environment = runnerEnvironment(run, targets);

  const envIndex = environment.arguments.indexOf('--env');
  assert.notEqual(envIndex, -1, 'the arguments set a script variable');
  assert.equal(environment.arguments[envIndex + 1], 'LOADTEST_PROFILE=plateau');
});

test('the runner environment lets the script read the container variables, which carry the credentials', () => {
  assert.ok(runnerEnvironment(run, targets).arguments.includes('--include-system-env-vars'));
});

test('the remote write url is read from INGOT_LOADTEST_RW_URL', () => {
  assert.equal(
    remoteWriteUrl({ INGOT_LOADTEST_RW_URL: 'http://metrics.example.test/api/v1/push' }),
    'http://metrics.example.test/api/v1/push',
  );
});

test('the remote write url is required, because k6 would otherwise write to its own localhost', () => {
  assert.throws(() => remoteWriteUrl({}), /INGOT_LOADTEST_RW_URL/);
  assert.throws(() => remoteWriteUrl({ INGOT_LOADTEST_RW_URL: '' }), /INGOT_LOADTEST_RW_URL/);
});

test('the runner environment hands the profile inputs to the script as script variables', () => {
  const environment = runnerEnvironment(run, {
    ...targets,
    scriptVariables: { LOADTEST_CEILING: '300', LOADTEST_ABORT_ON_FAIL: 'false' },
  });

  const variables = environment.arguments.flatMap((argument, index) =>
    argument === '--env' ? [environment.arguments[index + 1]] : [],
  );
  assert.deepEqual(variables, ['LOADTEST_PROFILE=plateau', 'LOADTEST_CEILING=300', 'LOADTEST_ABORT_ON_FAIL=false']);
});

test('the profile inputs are read from the runner environment, and only those', () => {
  assert.deepEqual(
    scriptVariables({ LOADTEST_CEILING: '300', LOADTEST_RATE: '', HOME: '/home/someone', LOADTEST_ABORT_ON_FAIL: 'false' }),
    { LOADTEST_CEILING: '300', LOADTEST_ABORT_ON_FAIL: 'false' },
  );
});
