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
// The node type of a result record (design D13): cores and memory from the capacity of the node an Ingot pod ran on,
// and the CPU model from the operator, because a Kubernetes node does not report it.

import type { NodeType } from './record.ts';

/** The variable that names the CPU model of the load test nodes, as the record keeps it. */
export const CPU_MODEL_VARIABLE = 'INGOT_LOADTEST_CPU_MODEL';

const MEMORY_UNITS: Readonly<Record<string, number>> = { '': 1, Ki: 2 ** 10, Mi: 2 ** 20, Gi: 2 ** 30, Ti: 2 ** 40 };

/** The CPU model of the nodes. Refused when unset, because a record names the machine it ran on. */
export function cpuModel(env: Readonly<Record<string, string | undefined>>): string {
  const model = env[CPU_MODEL_VARIABLE]?.trim();
  if (!model) {
    throw new Error(`set ${CPU_MODEL_VARIABLE} to the CPU model of the load test nodes, which the result record keeps`);
  }
  return model;
}

/** The node type from the JSON of one node, as `kubectl get node <name> -o json` prints it. */
export function nodeTypeFrom(nodeJson: string, model: string): NodeType {
  const capacity = (JSON.parse(nodeJson) as { status?: { capacity?: { cpu?: string; memory?: string } } }).status
    ?.capacity;
  if (capacity?.cpu === undefined || capacity.memory === undefined) {
    throw new Error('refusing a node without a cpu and memory capacity');
  }
  const cpu = /^(\d+)(m?)$/.exec(capacity.cpu);
  if (cpu === null) {
    throw new Error(`refusing the node cpu capacity "${capacity.cpu}"`);
  }
  const memory = /^(\d+)(Ki|Mi|Gi|Ti)?$/.exec(capacity.memory);
  if (memory === null) {
    throw new Error(`refusing the node memory capacity "${capacity.memory}"`);
  }
  const cores = cpu[2] === 'm' ? Math.floor(Number(cpu[1]) / 1000) : Number(cpu[1]);
  const bytes = Number(memory[1]) * MEMORY_UNITS[memory[2] ?? '']!;
  return { cpuModel: model, cores, memoryGiB: Math.round((bytes / 2 ** 30) * 10) / 10 };
}
