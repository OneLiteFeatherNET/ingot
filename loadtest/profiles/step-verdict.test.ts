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
import { plateauSteps } from './plateau.ts';
import type { MetricSample, MetricSink } from './step-metric.ts';
import {
  VERDICT_EXEC,
  VERDICT_REQUEST_NAME,
  plateauVerdictScenarios,
  queryApiUrl,
  queryRangeUrl,
  stepQueries,
  stepRange,
  stepWindows,
  verdictInput,
  withTags,
} from './step-verdict.ts';

const STEPS = plateauSteps(100, 10);

function matrix(values: readonly [number, string][]): unknown {
  return { status: 'success', data: { resultType: 'matrix', result: [{ metric: {}, values }] } };
}

function minutes(start: number, count: number, value: string): [number, string][] {
  return Array.from({ length: count }, (_, offset) => [start + 60 * (offset + 1), value] as [number, string]);
}

test('every plateau step gets one verdict scenario that starts when its hold ends', () => {
  const scenarios = plateauVerdictScenarios(STEPS, 'resolve', 150);

  assert.deepEqual(Object.keys(scenarios), ['verdict-1', 'verdict-2', 'verdict-3', 'verdict-4', 'verdict-5']);
  assert.equal(scenarios['verdict-1']?.startTime, '10m');
  assert.equal(scenarios['verdict-5']?.startTime, '50m');
});

test('a verdict scenario runs one iteration of the verdict function on one virtual user', () => {
  const scenario = plateauVerdictScenarios(STEPS, 'resolve', 150)['verdict-2'];

  assert.equal(scenario?.executor, 'shared-iterations');
  assert.equal(scenario.vus, 1);
  assert.equal(scenario.iterations, 1);
  assert.equal(scenario.exec, VERDICT_EXEC);
});

test('a verdict scenario hands its step rate, hold, p95 limit and scenario name to the verdict function', () => {
  const scenario = plateauVerdictScenarios(STEPS, 'resolve', 150)['verdict-2'];

  assert.deepEqual(scenario?.env, {
    LOADTEST_STEP_RATE: '70',
    LOADTEST_STEP_HOLD_MINUTES: '10',
    LOADTEST_STEP_P95_MS: '150',
    LOADTEST_STEP_SCENARIO: 'resolve',
  });
});

test('the verdict input puts the step start one hold before the verdict scenario started', () => {
  const input = verdictInput(
    { LOADTEST_STEP_RATE: '70', LOADTEST_STEP_HOLD_MINUTES: '10', LOADTEST_STEP_P95_MS: '150', LOADTEST_STEP_SCENARIO: 'resolve' },
    { testid: 'lt-abc123' },
    1_700_000_600_000,
  );

  assert.deepEqual(input, {
    testId: 'lt-abc123',
    scenario: 'resolve',
    rate: 70,
    holdMinutes: 10,
    p95Ms: 150,
    stepStartSeconds: 1_700_000_000,
  });
});

test('the verdict input is refused without a testid tag, and the refusal names it', () => {
  assert.throws(
    () =>
      verdictInput(
        { LOADTEST_STEP_RATE: '70', LOADTEST_STEP_HOLD_MINUTES: '10', LOADTEST_STEP_P95_MS: '150', LOADTEST_STEP_SCENARIO: 'resolve' },
        {},
        1_700_000_600_000,
      ),
    /testid/,
  );
});

test('the verdict input is refused when a step variable is missing, and the refusal names it', () => {
  assert.throws(
    () => verdictInput({ LOADTEST_STEP_HOLD_MINUTES: '10', LOADTEST_STEP_P95_MS: '150', LOADTEST_STEP_SCENARIO: 'resolve' }, { testid: 'lt-1' }, 0),
    /LOADTEST_STEP_RATE/,
  );
});

test('the query API is found on the host of a Mimir remote write endpoint', () => {
  assert.equal(queryApiUrl('http://mimir.monitoring:8080/api/v1/push'), 'http://mimir.monitoring:8080/prometheus');
});

test('the query API is found at the root of a Prometheus remote write receiver', () => {
  assert.equal(queryApiUrl('http://prometheus:9090/api/v1/write'), 'http://prometheus:9090');
});

test('a remote write endpoint of another shape is refused, because the query API cannot be derived', () => {
  assert.throws(() => queryApiUrl('http://example/ingest'), /query API/);
});

test('the step queries read the k6 metric names of the run and leave out the verdict requests', () => {
  const queries = stepQueries('lt-abc123');

  assert.match(queries.p95, /k6_http_req_duration_seconds\{testid="lt-abc123",name!="ingot-load-step-verdict"\}\[1m\]/);
  assert.match(queries.errorRate, /k6_http_reqs_total\{testid="lt-abc123",name!="ingot-load-step-verdict",expected_response="false"\}/);
  assert.match(queries.dropped, /k6_dropped_iterations_total\{testid="lt-abc123"\}/);
  assert.equal(VERDICT_REQUEST_NAME, 'ingot-load-step-verdict');
});

test('the counters are read as the change over one minute, so a counter that first appears in that minute counts', () => {
  const queries = stepQueries('lt-abc123');

  assert.match(queries.dropped, /offset 1m/);
  assert.match(queries.dropped, /or vector\(0\)/);
  assert.match(queries.errorRate, /offset 1m/);
});

test('a testid that is not a DNS label is refused before it reaches a query', () => {
  assert.throws(() => stepQueries('lt"} or vector(1) #'), /testid/);
});

test('the range of a step evaluates once at the end of every minute of its hold', () => {
  assert.deepEqual(stepRange(1_700_000_000, 10), { start: '1700000060', end: '1700000600', step: '60' });
});

test('a range query url encodes the query and carries the range', () => {
  const url = queryRangeUrl('http://mimir/prometheus', 'sum(x{testid="a"})', { start: '1', end: '2', step: '60' });

  assert.equal(
    url,
    'http://mimir/prometheus/api/v1/query_range?query=sum(x%7Btestid%3D%22a%22%7D)&start=1&end=2&step=60',
  );
});

test('the answers of a clean hold become one window per minute, with the p95 in milliseconds', () => {
  const start = 1_700_000_000;
  const windows = stepWindows(
    {
      p95: matrix(minutes(start, 2, '0.12')),
      errorRate: matrix(minutes(start, 2, '0.001')),
      dropped: matrix(minutes(start, 2, '0')),
    },
    start,
    2,
  );

  assert.deepEqual(windows, [
    { p95Ms: 120, errorRate: 0.001, droppedIterations: 0 },
    { p95Ms: 120, errorRate: 0.001, droppedIterations: 0 },
  ]);
});

test('a minute missing from an answer becomes NaN, so the pass rule counts it as a breach', () => {
  const start = 1_700_000_000;
  const windows = stepWindows(
    {
      p95: matrix(minutes(start, 1, '0.12')),
      errorRate: matrix(minutes(start, 2, '0')),
      dropped: matrix(minutes(start, 2, '0')),
    },
    start,
    2,
  );

  assert.ok(Number.isNaN(windows[1]?.p95Ms));
});

test('an error answer of the metrics store leaves every minute NaN instead of throwing', () => {
  const start = 1_700_000_000;
  const windows = stepWindows(
    { p95: { status: 'error', error: 'bad' }, errorRate: undefined, dropped: matrix(minutes(start, 1, '0')) },
    start,
    1,
  );

  assert.equal(windows.length, 1);
  assert.ok(Number.isNaN(windows[0]?.p95Ms));
  assert.ok(Number.isNaN(windows[0]?.errorRate));
});

test('withTags adds the given tags to every sample, next to the tags of the emitter', () => {
  const samples: MetricSample[] = [];
  const sink: MetricSink = { add: (name, value, tags) => samples.push({ name, value, tags }) };

  withTags(sink, { scenario: 'resolve' }).add('ingot_load_step_passed', 70, { result: 'pass' });

  assert.deepEqual(samples, [{ name: 'ingot_load_step_passed', value: 70, tags: { scenario: 'resolve', result: 'pass' } }]);
});
