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
import { K6_IMAGE, LOADTEST_NODE_LABEL, LOAD_NAMESPACE } from '../lib/config.ts';
import { buildTestRun, type TestRunInput } from './testrun.ts';

const input: TestRunInput = {
  namespace: LOAD_NAMESPACE,
  testId: 'lt-abc123',
  topology: 'monolith',
  profile: 'breakpoint',
  scenario: 'resolve',
  ingotVersion: '2.0.0',
  parallelism: 2,
  configMap: 'lt-abc123-scripts',
  archiveFile: 'resolve.tar',
};

test('the test run is named after the test id in the load test namespace', () => {
  const testRun = buildTestRun(input);

  assert.equal(testRun.metadata.name, 'ingot-load-lt-abc123');
  assert.equal(testRun.metadata.namespace, 'ingot-loadtest');
});

test('the test run sets the parallelism of the load generator', () => {
  assert.equal(buildTestRun(input).spec.parallelism, 2);
});

test('the test run reads the archive from the configmap that holds it', () => {
  assert.deepEqual(buildTestRun(input).spec.script, {
    configMap: { name: 'lt-abc123-scripts', file: 'resolve.tar' },
  });
});

test('the test run uses the k6 image pinned in the config', () => {
  assert.equal(buildTestRun(input).spec.runner.image, K6_IMAGE);
});

test('the test run schedules the load generator only on labelled load test nodes', () => {
  assert.deepEqual(buildTestRun(input).spec.runner.nodeSelector, { [LOADTEST_NODE_LABEL]: 'true' });
  assert.deepEqual(buildTestRun(input).spec.runner.tolerations, [
    { key: LOADTEST_NODE_LABEL, operator: 'Exists', effect: 'NoSchedule' },
  ]);
});

test('the load generator avoids the node of every Ingot pod by hostname', () => {
  const anti = buildTestRun(input).spec.runner.affinity.podAntiAffinity.requiredDuringSchedulingIgnoredDuringExecution;

  assert.deepEqual(anti, [
    {
      labelSelector: { matchLabels: { 'app.kubernetes.io/instance': 'ingot-loadtest' } },
      topologyKey: 'kubernetes.io/hostname',
    },
  ]);
});

test('the test run passes the tags and the output of the runner environment as arguments', () => {
  const args = buildTestRun(input).spec.arguments;

  assert.match(args, /--out experimental-prometheus-rw/);
  assert.match(args, /--tag testid=lt-abc123/);
});

test('the test run refuses a namespace other than the load test namespace', () => {
  assert.throws(() => buildTestRun({ ...input, namespace: 'ingot-production' }), /ingot-production/);
});
