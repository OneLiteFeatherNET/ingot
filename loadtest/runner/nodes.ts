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
// The reference node check (design D12). A reference run is refused before the install when the labelled node pool is
// too small; a non-reference run skips the check and its record is marked reference false.

import { LOADTEST_NODE_LABEL, type Topology } from '../lib/config.ts';

/** The Ingot pods each overlay starts (see loadtest/topologies). Kept here so the runner can count the nodes it needs. */
const INGOT_PODS: Readonly<Record<Topology, number>> = {
  monolith: 1,
  ha: 3,
  split: 2,
  scalable: 5,
  'scalable-read-1': 4,
  'scalable-read-2': 5,
  'scalable-read-4': 7,
};

/** One node per Ingot pod, and one more for the load generator, which must not share a node with a server. */
export function nodesNeeded(topology: Topology): number {
  return INGOT_PODS[topology] + 1;
}

export interface NodeCheckInput {
  readonly reference: boolean;
  readonly topology: Topology;
  /** The number of nodes that carry the load test label. */
  readonly found: number;
}

export interface NodeCheckResult {
  readonly reference: boolean;
}

export function checkReferenceNodes(input: NodeCheckInput): NodeCheckResult {
  if (!input.reference) {
    return { reference: false };
  }
  const needed = nodesNeeded(input.topology);
  if (input.found < needed) {
    throw new Error(
      `refusing a reference run of ${input.topology}: found ${input.found} node(s) labelled ${LOADTEST_NODE_LABEL}=true, needs ${needed}`,
    );
  }
  return { reference: true };
}

/** The arguments of the kubectl call that lists the labelled nodes. */
export function labelledNodesCommand(): string[] {
  return ['get', 'nodes', '-l', `${LOADTEST_NODE_LABEL}=true`, '-o', 'name'];
}
