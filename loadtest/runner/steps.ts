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
// The order of one run (design D6): the node check of a reference run, install, readiness, archive, apply, completion,
// record and uninstall. Every effect goes through an injected port, and every wait takes its deadline from an injected
// clock, so the order and the failure paths are unit tested without a cluster and without a sleep.

import { assertLoadNamespace, type Topology } from '../lib/config.ts';
import { checkReferenceNodes } from './nodes.ts';

export interface Clock {
  now(): number;
}

export interface RunnerPorts {
  /** The number of nodes that carry the load test label. */
  labelledNodes(): Promise<number>;
  install(): Promise<void>;
  /** True when every Ingot pod reports ready. */
  ready(): Promise<boolean>;
  /** Packs the scenario with `k6 archive` and stores it in the ConfigMap. */
  archive(): Promise<void>;
  apply(): Promise<void>;
  /** True when the TestRun has finished. */
  finished(): Promise<boolean>;
  /** The record step, implemented by the result records of group 8. */
  record(): Promise<void>;
  uninstall(): Promise<void>;
  pause(ms: number): Promise<void>;
}

export interface StepPlan {
  readonly namespace: string;
  readonly topology: Topology;
  readonly reference: boolean;
  readonly readyTimeoutMs: number;
  readonly completionTimeoutMs: number;
  readonly pollMs: number;
}

export async function runSteps(ports: RunnerPorts, clock: Clock, plan: StepPlan): Promise<void> {
  assertLoadNamespace(plan.namespace);
  if (plan.reference) {
    checkReferenceNodes({ reference: true, topology: plan.topology, found: await ports.labelledNodes() });
  }
  let installStarted = false;
  try {
    installStarted = true;
    await ports.install();
    await waitUntil(ports.ready, 'the Ingot pods to become ready', plan.readyTimeoutMs, plan.pollMs, ports, clock);
    await ports.archive();
    await ports.apply();
    await waitUntil(ports.finished, 'the TestRun to finish', plan.completionTimeoutMs, plan.pollMs, ports, clock);
    await ports.record();
  } finally {
    if (installStarted) {
      await ports.uninstall();
    }
  }
}

async function waitUntil(
  check: () => Promise<boolean>,
  what: string,
  timeoutMs: number,
  pollMs: number,
  ports: RunnerPorts,
  clock: Clock,
): Promise<void> {
  const deadline = clock.now() + timeoutMs;
  for (;;) {
    if (await check()) {
      return;
    }
    if (clock.now() >= deadline) {
      throw new Error(`${what} did not happen within ${timeoutMs} ms`);
    }
    await ports.pause(pollMs);
  }
}
