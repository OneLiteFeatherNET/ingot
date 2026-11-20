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
import { LOAD_NAMESPACE } from '../lib/config.ts';
import { runSteps, type Clock, type RunnerPorts, type StepPlan } from './steps.ts';

/** A fake world: every port appends its name to the log, and the clock only moves when the runner pauses. */
function fakeWorld(overrides: Partial<RunnerPorts> = {}) {
  const log: string[] = [];
  let now = 0;
  const clock: Clock = { now: () => now };
  const defaults: RunnerPorts = {
    labelledNodes: async () => {
      log.push('labelledNodes');
      return 10;
    },
    install: async () => {
      log.push('install');
    },
    ready: async () => {
      log.push('ready');
      return true;
    },
    archive: async () => {
      log.push('archive');
    },
    apply: async () => {
      log.push('apply');
    },
    finished: async () => {
      log.push('finished');
      return true;
    },
    record: async (result) => {
      log.push(`record ${result.kind}`);
    },
    uninstall: async () => {
      log.push('uninstall');
    },
    pause: async (ms: number) => {
      log.push(`pause ${ms}`);
      now += ms;
    },
  };
  return { log, clock, ports: { ...defaults, ...overrides } };
}

const plan: StepPlan = {
  namespace: LOAD_NAMESPACE,
  topology: 'monolith',
  reference: false,
  readyTimeoutMs: 60_000,
  completionTimeoutMs: 600_000,
  pollMs: 5_000,
};

test('the steps run install, readiness, archive, apply, completion, record and uninstall in that order', async () => {
  const world = fakeWorld();

  await runSteps(world.ports, world.clock, plan);

  assert.deepEqual(world.log, ['install', 'ready', 'archive', 'apply', 'finished', 'record completed', 'uninstall']);
});

test('a reference run checks the labelled nodes before the install', async () => {
  const world = fakeWorld();

  await runSteps(world.ports, world.clock, { ...plan, reference: true });

  assert.deepEqual(world.log.slice(0, 2), ['labelledNodes', 'install']);
});

test('a reference run refused for missing nodes exits before the install and does not uninstall', async () => {
  const world = fakeWorld({ labelledNodes: async () => 0 });

  await assert.rejects(runSteps(world.ports, world.clock, { ...plan, reference: true }), /needs 2/);

  assert.deepEqual(world.log, [], 'no install and no uninstall happen for a refused run');
});

test('a failed apply still uninstalls the release and rethrows the failure', async () => {
  const world = fakeWorld({
    apply: async () => {
      throw new Error('apply rejected');
    },
  });

  await assert.rejects(runSteps(world.ports, world.clock, plan), /apply rejected/);

  assert.equal(world.log.at(-1), 'uninstall');
  assert.ok(!world.log.some((entry) => entry.startsWith('record')), 'no record is written for a failed run');
});

test('a pod that never becomes ready fails the run once the readiness deadline has passed', async () => {
  const world = fakeWorld({ ready: async () => false });

  await assert.rejects(runSteps(world.ports, world.clock, plan), /60000 ms/);

  assert.equal(world.log.at(-1), 'uninstall');
  assert.ok(!world.log.includes('archive'), 'the archive is not built for a deployment that never came up');
});

test('the readiness wait polls with the configured interval until the deadline', async () => {
  const world = fakeWorld({ ready: async () => false });

  await assert.rejects(runSteps(world.ports, world.clock, plan));

  const pauses = world.log.filter((entry) => entry.startsWith('pause'));
  assert.equal(pauses.length, 12, 'a 60 second deadline at 5 second polls');
  assert.ok(pauses.every((entry) => entry === 'pause 5000'));
});

test('a run in a namespace other than the load test namespace is refused before any port is called', async () => {
  const world = fakeWorld();

  await assert.rejects(runSteps(world.ports, world.clock, { ...plan, namespace: 'ingot-staging' }), /ingot-staging/);

  assert.deepEqual(world.log, []);
});

test('a pod that never becomes ready is recorded as not-ready before the uninstall', async () => {
  const world = fakeWorld({ ready: async () => false });

  await assert.rejects(runSteps(world.ports, world.clock, plan));

  assert.deepEqual(world.log.slice(-2), ['record not-ready', 'uninstall']);
});

test('a failed not-ready record still uninstalls the release', async () => {
  const world = fakeWorld({
    ready: async () => false,
    record: async () => {
      throw new Error('disk full');
    },
  });

  await assert.rejects(runSteps(world.ports, world.clock, plan), /disk full/);

  assert.equal(world.log.at(-1), 'uninstall');
});

test('a readiness check that fails with an error is not recorded as not-ready', async () => {
  const world = fakeWorld({
    ready: async () => {
      throw new Error('kubectl unreachable');
    },
  });

  await assert.rejects(runSteps(world.ports, world.clock, plan), /kubectl unreachable/);

  assert.ok(!world.log.some((entry) => entry.startsWith('record')), 'only a passed deadline means not-ready');
  assert.equal(world.log.at(-1), 'uninstall');
});
