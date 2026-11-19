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
// Checks the checked-in Grafana dashboard as data: its variables, its panel titles and its annotation. The dashboard is
// reviewed as a diff, so this test pins what the design requires and nothing about the layout.

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { SCENARIOS, TOPOLOGIES } from '../lib/config.ts';
import { REFERENCE_SIZES } from '../lib/sizes.ts';

interface DashboardVariable {
  readonly name: string;
  readonly type: string;
  readonly query?: unknown;
  readonly options?: readonly { readonly value: string }[];
}

interface DashboardPanel {
  readonly title?: string;
  readonly type?: string;
  readonly datasource?: { readonly uid?: string };
}

interface Dashboard {
  readonly uid?: string;
  readonly title?: string;
  readonly templating: { readonly list: readonly DashboardVariable[] };
  readonly panels: readonly DashboardPanel[];
  readonly annotations: { readonly list: readonly { readonly expr?: string; readonly enable?: boolean }[] };
}

const here = dirname(fileURLToPath(import.meta.url));
const dashboardText = readFileSync(join(here, 'ingot-load-tests.json'), 'utf8');
const dashboard = JSON.parse(dashboardText) as Dashboard;

const variable = (name: string): DashboardVariable => {
  const found = dashboard.templating.list.find((candidate) => candidate.name === name);
  assert.ok(found, `the dashboard has no variable named ${name}`);
  return found;
};

// The "All" entry of a multi-value variable is not a value of the config, so it is left out of the comparison.
const optionValues = (name: string): string[] =>
  (variable(name).options ?? []).map((option) => option.value).filter((value) => value !== '$__all');

const panelTitles = (): string[] =>
  dashboard.panels.flatMap((panel) => (panel.title === undefined ? [] : [panel.title]));

test('the dashboard has a stable uid and a title for the load test runs', () => {
  assert.equal(dashboard.uid, 'ingot-load-tests', 'the uid is the link target of the runner output');
  assert.equal(dashboard.title, 'Ingot load tests', 'the title names the dashboard in the Grafana folder');
});

test('the testid variable is a label query on the k6 samples', () => {
  const testid = variable('testid');
  assert.equal(testid.type, 'query', 'the run id comes from the samples, not from a fixed list');
  assert.match(JSON.stringify(testid.query), /label_values\(k6_vus, testid\)/, 'the query lists the run ids of the samples');
});

test('the topology variable offers exactly the topologies of the config', () => {
  assert.deepEqual(optionValues('topology'), [...TOPOLOGIES], 'the options must follow TOPOLOGIES in config.ts');
});

test('the scenario variable offers exactly the scenarios of the config', () => {
  assert.deepEqual(optionValues('scenario'), [...SCENARIOS], 'the options must follow SCENARIOS in config.ts');
});

test('the size variable offers exactly the five reference sizes', () => {
  assert.deepEqual(
    optionValues('size'),
    REFERENCE_SIZES.map((size) => size.name),
    'the options must follow REFERENCE_SIZES in sizes.ts, in the same order',
  );
});

test('the datasource variable selects the Mimir datasource and no endpoint is written into the dashboard', () => {
  assert.equal(variable('datasource').type, 'datasource', 'panels read Mimir through a datasource variable');
  assert.doesNotMatch(dashboardText, /https?:\/\//, 'no URL or cluster host may be written into the dashboard');
});

test('every panel reads its datasource through the datasource variable', () => {
  for (const panel of dashboard.panels) {
    assert.equal(panel.datasource?.uid, '${datasource}', `panel "${panel.title}" must use the datasource variable`);
  }
});

test('the dashboard shows the target and achieved request rate', () => {
  assert.ok(panelTitles().includes('Target and achieved request rate'));
});

test('the dashboard shows p50, p95 and p99 latency per scenario', () => {
  assert.ok(panelTitles().includes('Latency p50, p95 and p99 per scenario'));
});

test('the dashboard shows the error rate, the dropped iterations, the active virtual users and the bytes per second', () => {
  for (const title of ['Error rate', 'Dropped iterations', 'Active virtual users', 'Bytes per second']) {
    assert.ok(panelTitles().includes(title), `the dashboard has no panel titled "${title}"`);
  }
});

test('the dashboard shows server CPU, heap and garbage collection, database pool and storage latency per pod', () => {
  for (const title of [
    'Server CPU per pod',
    'Heap and garbage collection per pod',
    'Database pool active and pending per pod',
    'Storage latency per pod',
  ]) {
    assert.ok(panelTitles().includes(title), `the dashboard has no panel titled "${title}"`);
  }
});

test('the dashboard shows the maximum sustainable rate as a table and as a topology bar chart', () => {
  assert.ok(panelTitles().includes('Maximum sustainable rate per topology and size'), 'the maximum table is missing');
  assert.ok(panelTitles().includes('Topology comparison'), 'the topology bar chart is missing');
  const barChart = dashboard.panels.find((panel) => panel.title === 'Topology comparison');
  assert.equal(barChart?.type, 'barchart', 'the topology comparison is drawn as a bar chart');
});

test('the dashboard marks step changes as annotations read from the step metric', () => {
  const stepAnnotation = dashboard.annotations.list.find((annotation) =>
    annotation.expr?.includes('ingot_load_step_passed'),
  );
  assert.ok(stepAnnotation, 'an annotation must read the step metric so that step changes appear on the curves');
  assert.equal(stepAnnotation.enable, true, 'the step annotation is enabled by default');
});

test('every panel title is unique so that the panels can be referred to by name', () => {
  const titles = panelTitles();
  assert.equal(new Set(titles).size, titles.length, `duplicate panel titles: ${titles.join(', ')}`);
});
