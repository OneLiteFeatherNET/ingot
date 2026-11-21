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
import {
  QUERY_URL_VARIABLE,
  measureOutcome,
  metricsQueryUrl,
  p95Query,
  passedStepsQuery,
  queryRequestUrl,
  type MeasureWindow,
  type MetricsClient,
} from './measure.ts';

const window: MeasureWindow = { testId: 'lt-abc123', startMs: 1_000_000, endMs: 4_600_000, holdMinutes: 15 };

/** A fake metrics store that answers each API path from a table and records the requests it got. */
function fakeStore(answers: Readonly<Record<string, unknown>>): MetricsClient & { requests: { path: string; params: Record<string, string> }[] } {
  const requests: { path: string; params: Record<string, string> }[] = [];
  return {
    requests,
    async get(path, params) {
      requests.push({ path, params });
      if (!(path in answers)) {
        throw new Error(`unexpected request to ${path}`);
      }
      return answers[path];
    },
  };
}

const matrix = (values: [number, string][]) => ({
  status: 'success',
  data: { resultType: 'matrix', result: values.length === 0 ? [] : [{ metric: {}, values }] },
});
const vector = (value: string | undefined) => ({
  status: 'success',
  data: { resultType: 'vector', result: value === undefined ? [] : [{ metric: {}, value: [4600, value] }] },
});

test('the query url defaults to the Prometheus API of the Mimir remote write host', () => {
  assert.equal(
    metricsQueryUrl({ INGOT_LOADTEST_RW_URL: 'http://metrics.example.test/api/v1/push' }),
    'http://metrics.example.test/prometheus',
  );
});

test('the query url of a Prometheus remote write receiver is the same host', () => {
  assert.equal(
    metricsQueryUrl({ INGOT_LOADTEST_RW_URL: 'https://metrics.example.test:9090/api/v1/write' }),
    'https://metrics.example.test:9090',
  );
});

test('the query url variable replaces the derived url, without a trailing slash', () => {
  assert.equal(
    metricsQueryUrl({
      INGOT_LOADTEST_RW_URL: 'http://metrics.example.test/api/v1/push',
      [QUERY_URL_VARIABLE]: 'http://localhost:9009/prometheus/',
    }),
    'http://localhost:9009/prometheus',
  );
});

test('a remote write url of unknown shape needs the query url variable, and the error names it', () => {
  assert.throws(
    () => metricsQueryUrl({ INGOT_LOADTEST_RW_URL: 'http://metrics.example.test/receive' }),
    new RegExp(QUERY_URL_VARIABLE),
  );
});

test('a query url that is not http or https is refused', () => {
  assert.throws(() => metricsQueryUrl({ [QUERY_URL_VARIABLE]: 'metrics.example.test' }), /http/);
});

test('the request url puts the API path under the query url and encodes the parameters', () => {
  assert.equal(
    queryRequestUrl('http://metrics.example.test/prometheus', '/api/v1/query', { query: 'up{a="b"}', time: '10' }),
    'http://metrics.example.test/prometheus/api/v1/query?query=up%7Ba%3D%22b%22%7D&time=10',
  );
});

test('the passed steps are read from the k6 series of the step metric of this test run', () => {
  assert.equal(passedStepsQuery('lt-abc123'), 'k6_ingot_load_step_passed{testid="lt-abc123",result="pass"}');
});

test('the p95 is read from the native histogram of this test run over one step hold', () => {
  assert.equal(
    p95Query('lt-abc123', 15),
    'histogram_quantile(0.95, sum(rate(k6_http_req_duration_seconds{testid="lt-abc123"}[15m])))',
  );
});

test('a query of a test id that is not a tag value is refused, so no PromQL is injected', () => {
  assert.throws(() => passedStepsQuery('lt"} or up{a="'), /testid/);
});

test('the outcome is the highest passed step rate with the p95 of its hold, in milliseconds', async () => {
  const store = fakeStore({
    '/api/v1/query_range': matrix([[1900, '60'], [2800, '70'], [3700, '80'], [3705, '80']]),
    '/api/v1/query': vector('0.3404'),
  });

  const outcome = await measureOutcome(store, window);

  assert.deepEqual(outcome, { kind: 'rate', rate: 80, p95Ms: 340 });
});

test('the p95 is read at the first sample of the highest step, the end of its hold', async () => {
  const store = fakeStore({
    '/api/v1/query_range': matrix([[1900, '60'], [3700, '80'], [3705, '80']]),
    '/api/v1/query': vector('0.2'),
  });

  await measureOutcome(store, window);

  const [range, instant] = store.requests;
  assert.deepEqual(range, {
    path: '/api/v1/query_range',
    params: { query: passedStepsQuery('lt-abc123'), start: '1000', end: '4600', step: '5' },
  });
  assert.deepEqual(instant, { path: '/api/v1/query', params: { query: p95Query('lt-abc123', 15), time: '3700' } });
});

test('a run without a passed step has no outcome and reads no p95', async () => {
  const store = fakeStore({ '/api/v1/query_range': matrix([]) });

  assert.equal(await measureOutcome(store, window), undefined);
  assert.equal(store.requests.length, 1, 'no p95 is read without a passed step');
});

test('a run without latency samples at its highest step has no outcome', async () => {
  const store = fakeStore({
    '/api/v1/query_range': matrix([[3700, '80']]),
    '/api/v1/query': vector('NaN'),
  });

  assert.equal(await measureOutcome(store, window), undefined);
});

test('an error answer of the metrics store fails the measurement with its message', async () => {
  const store = fakeStore({ '/api/v1/query_range': { status: 'error', error: 'bad_data: parse error' } });

  await assert.rejects(measureOutcome(store, window), /bad_data: parse error/);
});

test('an answer of the wrong shape fails the measurement instead of recording a guess', async () => {
  const store = fakeStore({ '/api/v1/query_range': { status: 'success', data: { resultType: 'vector', result: [] } } });

  await assert.rejects(measureOutcome(store, window), /matrix/);
});
