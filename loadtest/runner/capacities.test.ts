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
import { REFERENCE_SIZES } from '../lib/sizes.ts';
import { PUBLISHED_SCENARIOS, PUBLISHED_TOPOLOGIES, capacityCells, renderCapacities, NOT_SUPPORTED, NOT_MEASURED } from './capacities.ts';
import { createRecord, type RecordInput, type ResultRecord } from './record.ts';

const base: RecordInput = {
  ingotVersion: '2.0.0',
  topology: 'monolith',
  size: 'S',
  storage: 'filesystem',
  nodeType: { cpuModel: 'Example CPU', cores: 4, memoryGiB: 16 },
  k6Version: '1.8.2',
  date: '2026-11-19',
  reference: true,
  scenario: 'mix',
  outcome: { kind: 'rate', rate: 120, p95Ms: 340 },
};

const recordOf = (overrides: Partial<RecordInput>): ResultRecord => createRecord({ ...base, ...overrides });

function cellOf(cells: ReturnType<typeof capacityCells>, topology: string, size: string, scenario: string): string {
  const cell = cells.find((candidate) => candidate.topology === topology && candidate.size === size && candidate.scenario === scenario);
  assert.ok(cell, `no cell for ${topology} ${size} ${scenario}`);
  return cell.text;
}

test('only the core scenarios mix, resolve and deploy are published', () => {
  assert.deepEqual([...PUBLISHED_SCENARIOS], ['mix', 'resolve', 'deploy']);
});

test('every topology, size and core scenario has a cell, 90 in all', () => {
  const cells = capacityCells([]);

  assert.equal(PUBLISHED_TOPOLOGIES.length * REFERENCE_SIZES.length * PUBLISHED_SCENARIOS.length, 90);
  assert.equal(cells.length, 90);
});

test('a cell without any record reads not measured instead of staying empty', () => {
  const cells = capacityCells([]);

  assert.ok(cells.every((cell) => cell.text === NOT_MEASURED));
});

test('a non-reference record contributes no value to its cell', () => {
  const cells = capacityCells([recordOf({ reference: false })]);

  assert.equal(cellOf(cells, 'monolith', 'S', 'mix'), NOT_MEASURED);
});

test('a reference rate record shows its rate and its p95', () => {
  const cells = capacityCells([recordOf({})]);

  assert.equal(cellOf(cells, 'monolith', 'S', 'mix'), '120 req/s (p95 340 ms)');
});

test('an oom-killed record reads not supported at this size', () => {
  const cells = capacityCells([recordOf({ outcome: { kind: 'oom-killed', step: 100 } })]);

  assert.equal(cellOf(cells, 'monolith', 'S', 'mix'), NOT_SUPPORTED);
});

test('a not-ready record reads not supported at this size', () => {
  const cells = capacityCells([recordOf({ outcome: { kind: 'not-ready', step: 60 } })]);

  assert.equal(cellOf(cells, 'monolith', 'S', 'mix'), NOT_SUPPORTED);
});

test('the newest reference record of a cell wins over an older one', () => {
  const cells = capacityCells([
    recordOf({ date: '2026-11-01', outcome: { kind: 'rate', rate: 100, p95Ms: 300 } }),
    recordOf({ date: '2026-11-19', outcome: { kind: 'rate', rate: 150, p95Ms: 310 } }),
  ]);

  assert.equal(cellOf(cells, 'monolith', 'S', 'mix'), '150 req/s (p95 310 ms)');
});

test('a record of a scenario that is not published leaves every cell as not measured', () => {
  const cells = capacityCells([recordOf({ scenario: 'metadata' })]);

  assert.ok(cells.every((cell) => cell.text === NOT_MEASURED));
});

test('the rendered page holds one table per published scenario with a row per topology', () => {
  const page = renderCapacities([recordOf({})]);

  for (const scenario of PUBLISHED_SCENARIOS) {
    assert.ok(page.includes(`## ${scenario}`), `the page has no section for ${scenario}`);
  }
  assert.ok(page.includes('| monolith |'), 'the page has no row for monolith');
  assert.ok(page.includes('120 req/s (p95 340 ms)'), 'the page does not show the measured rate');
});
