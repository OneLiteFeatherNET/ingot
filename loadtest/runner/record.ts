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
// The result record of one run (design D13): one JSON file per topology, size and scenario, under the Ingot version.
// The record is built and validated without a file system or a clock. The caller hands in the date and the writer, so
// the unit tests need neither.

import type { Scenario, Topology } from '../lib/config.ts';
import { sizeByName, type ReferenceSize } from '../lib/sizes.ts';

/** The folder the records are checked in under, relative to the repository root. */
export const RESULTS_DIR = 'loadtest/results';

export const STORAGE_BACKENDS = ['filesystem', 's3', 'storage-cache'] as const;
export type StorageBackend = (typeof STORAGE_BACKENDS)[number];

/** The outcome of a scenario. A rate is the maximum sustainable rate with its p95; the other two name the step they stopped at. */
export type Outcome =
  | { readonly kind: 'rate'; readonly rate: number; readonly p95Ms: number }
  | { readonly kind: 'not-ready'; readonly step: number }
  | { readonly kind: 'oom-killed'; readonly step: number };

export interface NodeType {
  readonly cpuModel: string;
  readonly cores: number;
  readonly memoryGiB: number;
}

export interface RecordInput {
  readonly ingotVersion: string;
  readonly topology: Topology;
  readonly size: ReferenceSize['name'];
  readonly storage: StorageBackend;
  readonly nodeType: NodeType;
  readonly k6Version: string;
  /** The day of the run as YYYY-MM-DD. The caller passes it in, so the record never reads the clock. */
  readonly date: string;
  readonly reference: boolean;
  readonly scenario: Scenario;
  readonly outcome: Outcome;
}

export interface ResultRecord {
  readonly path: string;
  readonly ingotVersion: string;
  readonly topology: Topology;
  readonly pod: { readonly size: ReferenceSize['name']; readonly cpu: string; readonly memory: string };
  readonly storage: StorageBackend;
  readonly database: 'postgresql';
  readonly nodeType: NodeType;
  readonly k6Version: string;
  readonly date: string;
  readonly reference: boolean;
  readonly scenario: Scenario;
  readonly outcome: Outcome;
}

const VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._+-]*$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Validates the input and returns the record with its path. Refuses anything that would not be a real measurement. */
export function createRecord(input: RecordInput): ResultRecord {
  if (!VERSION_PATTERN.test(input.ingotVersion) || input.ingotVersion.includes('..')) {
    throw new Error(`refusing the ingot version "${input.ingotVersion}": it must be a single path segment`);
  }
  if (!DATE_PATTERN.test(input.date) || new Date(input.date).toISOString().slice(0, 10) !== input.date) {
    throw new Error(`refusing the date "${input.date}": it must be a real calendar day as YYYY-MM-DD`);
  }
  assertNodeType(input.nodeType);
  assertOutcome(input.outcome);
  const size = sizeByName(input.size);
  return {
    path: `${RESULTS_DIR}/${input.ingotVersion}/${input.topology}-${size.name}-${input.scenario}.json`,
    ingotVersion: input.ingotVersion,
    topology: input.topology,
    pod: { size: size.name, cpu: size.cpu, memory: size.memory },
    storage: input.storage,
    database: 'postgresql',
    nodeType: { ...input.nodeType },
    k6Version: input.k6Version,
    date: input.date,
    reference: input.reference,
    scenario: input.scenario,
    outcome: { ...input.outcome },
  };
}

export type RecordWriter = (path: string, content: string) => Promise<void>;

/** Writes the record as one JSON document with a trailing newline, and returns the path it wrote to. */
export async function writeResultRecord(record: ResultRecord, write: RecordWriter): Promise<string> {
  const { path, ...body } = record;
  await write(path, `${JSON.stringify(body, null, 2)}\n`);
  return path;
}

function assertNodeType(nodeType: NodeType): void {
  if (nodeType.cpuModel.trim() === '') {
    throw new Error('refusing a node type without a CPU model');
  }
  if (!Number.isInteger(nodeType.cores) || nodeType.cores < 1) {
    throw new Error(`refusing a node type with ${nodeType.cores} cores: expected a whole number of at least 1`);
  }
  if (!Number.isFinite(nodeType.memoryGiB) || nodeType.memoryGiB <= 0) {
    throw new Error(`refusing a node type with ${nodeType.memoryGiB} GiB of memory: expected a positive number`);
  }
}

function assertOutcome(outcome: Outcome): void {
  if (outcome.kind === 'rate') {
    if (!isNonNegative(outcome.rate)) {
      throw new Error(`refusing a rate of ${outcome.rate}: a rate is a finite non-negative number of requests per second`);
    }
    if (!isNonNegative(outcome.p95Ms)) {
      throw new Error(`refusing a p95 of ${outcome.p95Ms} ms: expected a finite non-negative number`);
    }
    return;
  }
  if (!isNonNegative(outcome.step)) {
    throw new Error(`refusing a ${outcome.kind} step of ${outcome.step}: expected a finite non-negative rate`);
  }
}

function isNonNegative(value: number): boolean {
  return Number.isFinite(value) && value >= 0;
}
