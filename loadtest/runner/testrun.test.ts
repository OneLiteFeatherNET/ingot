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
  reference: false,
  credentialsSecret: 'lt-abc123-credentials',
  remoteWriteUrl: 'http://metrics.example.test/api/v1/push',
  baseUrl: 'http://ingot-loadtest:8080',
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

test('a reference run schedules the load generator only on labelled load test nodes', () => {
  const reference = buildTestRun({ ...input, reference: true });

  assert.deepEqual(reference.spec.runner.nodeSelector, { [LOADTEST_NODE_LABEL]: 'true' });
  assert.deepEqual(reference.spec.runner.tolerations, [
    { key: LOADTEST_NODE_LABEL, operator: 'Exists', effect: 'NoSchedule' },
  ]);
});

test('a run without --reference may share nodes, so it sets no node selector and no toleration', () => {
  const runner = buildTestRun(input).spec.runner;

  assert.equal('nodeSelector' in runner, false, 'no node selector');
  assert.equal('tolerations' in runner, false, 'no toleration');
});

test('the load generator reads the credentials of the load test user from the run secret', () => {
  const env = buildTestRun(input).spec.runner.env;

  assert.deepEqual(
    env.filter((variable) => variable.name.startsWith('LOADTEST_')),
    [
      {
        name: 'LOADTEST_USER',
        valueFrom: { secretKeyRef: { name: 'lt-abc123-credentials', key: 'LOADTEST_USER' } },
      },
      {
        name: 'LOADTEST_PASSWORD',
        valueFrom: { secretKeyRef: { name: 'lt-abc123-credentials', key: 'LOADTEST_PASSWORD' } },
      },
    ],
  );
});

test('the load generator writes to the remote write url and requests the ingot service', () => {
  const env = buildTestRun(input).spec.runner.env;

  assert.deepEqual(env.find((variable) => variable.name === 'K6_PROMETHEUS_RW_SERVER_URL'), {
    name: 'K6_PROMETHEUS_RW_SERVER_URL',
    value: 'http://metrics.example.test/api/v1/push',
  });
  assert.deepEqual(env.find((variable) => variable.name === 'BASE_URL'), {
    name: 'BASE_URL',
    value: 'http://ingot-loadtest:8080',
  });
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

test('the test run is kept after it ends, so the runner reads its stage and the uninstall removes it', () => {
  assert.equal('cleanup' in buildTestRun(input).spec, false);
});
