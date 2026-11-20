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
// The reference capacities page (design D13). Renders one table per core scenario from the result records. Only
// reference records count. A pure function: the caller reads the records and writes the page.

import type { Scenario, Topology } from '../lib/config.ts';
import { REFERENCE_SIZES, type ReferenceSize } from '../lib/sizes.ts';
import type { ResultRecord } from './record.ts';

/** The scenarios whose results are published. The others run on demand and are never shown on the page. */
export const PUBLISHED_SCENARIOS = ['mix', 'resolve', 'deploy'] as const satisfies readonly Scenario[];

/**
 * The six distinct overlays of the reference matrix. `scalable-read-2.yaml` is identical to `scalable.yaml`, which
 * already runs two read replicas, so the matrix counts it once.
 */
export const PUBLISHED_TOPOLOGIES = [
  'monolith',
  'ha',
  'split',
  'scalable',
  'scalable-read-1',
  'scalable-read-4',
] as const satisfies readonly Topology[];

export const NOT_SUPPORTED = 'not supported at this size';
export const NOT_MEASURED = 'not measured';

export type PublishedScenario = (typeof PUBLISHED_SCENARIOS)[number];
export type PublishedTopology = (typeof PUBLISHED_TOPOLOGIES)[number];

export interface CapacityCell {
  readonly scenario: PublishedScenario;
  readonly topology: PublishedTopology;
  readonly size: ReferenceSize['name'];
  readonly text: string;
}

/** Every cell of the matrix, in the order of the page. Among several reference records of one cell the newest wins. */
export function capacityCells(records: readonly ResultRecord[]): CapacityCell[] {
  const cells: CapacityCell[] = [];
  for (const scenario of PUBLISHED_SCENARIOS) {
    for (const topology of PUBLISHED_TOPOLOGIES) {
      for (const size of REFERENCE_SIZES) {
        cells.push({ scenario, topology, size: size.name, text: cellText(newestRecord(records, scenario, topology, size.name)) });
      }
    }
  }
  return cells;
}

/** Renders the page body: one section and one table per published scenario, a row per topology and a column per size. */
export function renderCapacities(records: readonly ResultRecord[]): string {
  const cells = capacityCells(records);
  const sections = PUBLISHED_SCENARIOS.map((scenario) => {
    const header = `| Topology | ${REFERENCE_SIZES.map((size) => size.name).join(' | ')} |`;
    const divider = `| --- | ${REFERENCE_SIZES.map(() => '---').join(' | ')} |`;
    const rows = PUBLISHED_TOPOLOGIES.map((topology) => {
      const values = REFERENCE_SIZES.map(
        (size) => cells.find((cell) => cell.scenario === scenario && cell.topology === topology && cell.size === size.name)?.text ?? NOT_MEASURED,
      );
      return `| ${topology} | ${values.join(' | ')} |`;
    });
    return [`## ${scenario}`, '', header, divider, ...rows, ''].join('\n');
  });
  return sections.join('\n');
}

function newestRecord(
  records: readonly ResultRecord[],
  scenario: PublishedScenario,
  topology: PublishedTopology,
  size: ReferenceSize['name'],
): ResultRecord | undefined {
  let newest: ResultRecord | undefined;
  for (const record of records) {
    if (!record.reference || record.scenario !== scenario || record.topology !== topology || record.pod.size !== size) {
      continue;
    }
    if (newest === undefined || record.date > newest.date || (record.date === newest.date && record.ingotVersion > newest.ingotVersion)) {
      newest = record;
    }
  }
  return newest;
}

function cellText(record: ResultRecord | undefined): string {
  if (record === undefined) {
    return NOT_MEASURED;
  }
  if (record.outcome.kind === 'rate') {
    return `${record.outcome.rate} req/s (p95 ${record.outcome.p95Ms} ms)`;
  }
  return NOT_SUPPORTED;
}
