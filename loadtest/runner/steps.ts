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

/** How a run ended, as the record step sees it: the TestRun completed, or the Ingot pods never became ready. */
export type RunResult = { readonly kind: 'completed' } | { readonly kind: 'not-ready' };

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
  /** The record step, implemented by `recordStep` in record.ts. */
  record(result: RunResult): Promise<void>;
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
    if (!(await waitUntil(ports.ready, plan.readyTimeoutMs, plan.pollMs, ports, clock))) {
      // A deployment that never came up is a valid result at this size (design D11), so it is recorded before the run fails.
      await ports.record({ kind: 'not-ready' });
      throw new Error(`the Ingot pods did not become ready within ${plan.readyTimeoutMs} ms`);
    }
    await ports.archive();
    await ports.apply();
    if (!(await waitUntil(ports.finished, plan.completionTimeoutMs, plan.pollMs, ports, clock))) {
      throw new Error(`the TestRun did not finish within ${plan.completionTimeoutMs} ms`);
    }
    await ports.record({ kind: 'completed' });
  } finally {
    if (installStarted) {
      await ports.uninstall();
    }
  }
}

/** Polls until the check holds, and answers false once the deadline has passed. An error of the check propagates. */
async function waitUntil(
  check: () => Promise<boolean>,
  timeoutMs: number,
  pollMs: number,
  ports: RunnerPorts,
  clock: Clock,
): Promise<boolean> {
  const deadline = clock.now() + timeoutMs;
  for (;;) {
    if (await check()) {
      return true;
    }
    if (clock.now() >= deadline) {
      return false;
    }
    await ports.pause(pollMs);
  }
}
