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
// The measured outcome of a completed run (design D9 and D13), read back from the metrics store through the Prometheus
// HTTP API. The maximum is the highest step rate the step metric reports as passed for the testid of the run, and its
// p95 is the latency quantile over the hold of that step. The HTTP client is injected, so the unit tests use a fake
// store and need no network.

import { STEP_PASSED_METRIC } from '../profiles/step-metric.ts';
import { REMOTE_WRITE_URL_VARIABLE } from './environment.ts';
import type { Outcome } from './record.ts';

/** The variable that names the Prometheus HTTP API of the metrics store, for example `http://<host>/prometheus`. */
export const QUERY_URL_VARIABLE = 'INGOT_LOADTEST_QUERY_URL';

/** k6 writes every metric through remote write with this prefix. */
const K6_PREFIX = 'k6_';
/** The resolution of the step search. Fine enough that the p95 window ends within seconds of the end of the step. */
const RANGE_STEP_SECONDS = 5;

/**
 * The Prometheus remote write paths and the path of the query API on the same host: Mimir pushes to /api/v1/push and
 * serves the API under /prometheus, a Prometheus receiver takes /api/v1/write and serves the API at its root.
 */
const QUERY_PREFIXES: readonly [string, string][] = [
  ['/api/v1/push', '/prometheus'],
  ['/api/v1/write', ''],
];

const HTTP_URL = /^https?:\/\/\S+$/;
const TEST_ID = /^[a-z0-9][a-z0-9-]{0,39}$/;

/**
 * The base url of the Prometheus HTTP API: the query url variable when it is set, otherwise the query API on the host of
 * the remote write endpoint. The runner reads the store from outside the cluster, so the variable is the way to reach a
 * store whose remote write host only resolves inside it.
 */
export function metricsQueryUrl(env: Readonly<Record<string, string | undefined>>): string {
  const configured = env[QUERY_URL_VARIABLE]?.trim();
  if (configured) {
    if (!HTTP_URL.test(configured)) {
      throw new Error(`refusing ${QUERY_URL_VARIABLE} "${configured}": it starts with http:// or https://`);
    }
    return configured.replace(/\/+$/, '');
  }
  const remoteWrite = env[REMOTE_WRITE_URL_VARIABLE] ?? '';
  for (const [pushPath, queryPath] of QUERY_PREFIXES) {
    if (HTTP_URL.test(remoteWrite) && remoteWrite.endsWith(pushPath)) {
      return `${remoteWrite.slice(0, -pushPath.length)}${queryPath}`;
    }
  }
  throw new Error(
    `set ${QUERY_URL_VARIABLE} to the Prometheus HTTP API of the metrics store: it cannot be derived from ${REMOTE_WRITE_URL_VARIABLE}`,
  );
}

/** The url of one API request: the path under the query url, with the parameters encoded. */
export function queryRequestUrl(base: string, path: string, params: Readonly<Record<string, string>>): string {
  return `${base}${path}?${new URLSearchParams(params).toString()}`;
}

function assertTestId(testId: string): string {
  if (!TEST_ID.test(testId)) {
    throw new Error(`refusing testid "${testId}": use lower case letters, digits and hyphens, at most 40 characters`);
  }
  return testId;
}

/** The passed steps of the run: the step metric with its rate as the value, only where the step passed. */
export function passedStepsQuery(testId: string): string {
  return `${K6_PREFIX}${STEP_PASSED_METRIC}{testid="${assertTestId(testId)}",result="pass"}`;
}

/** The p95 of every request of the run over one step hold, from the native histogram, in seconds. */
export function p95Query(testId: string, holdMinutes: number): string {
  return `histogram_quantile(0.95, sum(rate(${K6_PREFIX}http_req_duration_seconds{testid="${assertTestId(testId)}"}[${holdMinutes}m])))`;
}

/** A GET of the Prometheus HTTP API that answers with the parsed JSON body. */
export interface MetricsClient {
  get(path: string, params: Record<string, string>): Promise<unknown>;
}

export interface MeasureWindow {
  readonly testId: string;
  /** When the TestRun was applied, in epoch milliseconds. */
  readonly startMs: number;
  /** When the TestRun finished, in epoch milliseconds. */
  readonly endMs: number;
  /** The hold of one plateau step, the window the p95 is read over. */
  readonly holdMinutes: number;
}

/**
 * The maximum sustainable rate of the run with its p95, or undefined when the run has none: no step passed, or no
 * request was measured at the highest one. The p95 is read at the first sample of the highest passed step, which the
 * test writes at the end of that step's hold.
 */
export async function measureOutcome(client: MetricsClient, window: MeasureWindow): Promise<Outcome | undefined> {
  const steps = data(
    await client.get('/api/v1/query_range', {
      query: passedStepsQuery(window.testId),
      start: seconds(window.startMs),
      end: seconds(window.endMs),
      step: String(RANGE_STEP_SECONDS),
    }),
    'matrix',
  );
  let maximum: { rate: number; at: number } | undefined;
  for (const series of steps) {
    for (const [at, text] of (series as { values?: [number, string][] }).values ?? []) {
      const rate = Number(text);
      if (Number.isFinite(rate) && (maximum === undefined || rate > maximum.rate || (rate === maximum.rate && at < maximum.at))) {
        maximum = { rate, at };
      }
    }
  }
  if (maximum === undefined) {
    return undefined;
  }
  const latency = data(
    await client.get('/api/v1/query', { query: p95Query(window.testId, window.holdMinutes), time: String(maximum.at) }),
    'vector',
  );
  const p95Seconds = Number((latency[0] as { value?: [number, string] } | undefined)?.value?.[1]);
  if (!Number.isFinite(p95Seconds)) {
    return undefined;
  }
  return { kind: 'rate', rate: maximum.rate, p95Ms: Math.round(p95Seconds * 1000) };
}

function seconds(epochMs: number): string {
  return String(Math.floor(epochMs / 1000));
}

/** The result list of an API answer of the expected type. An error answer or another shape fails with its message. */
function data(answer: unknown, resultType: 'matrix' | 'vector'): unknown[] {
  const body = answer as { status?: string; error?: string; data?: { resultType?: string; result?: unknown } };
  if (body?.status !== 'success') {
    throw new Error(`the metrics store answered with an error: ${body?.error ?? JSON.stringify(answer)}`);
  }
  if (body.data?.resultType !== resultType || !Array.isArray(body.data.result)) {
    throw new Error(`the metrics store answered with ${body.data?.resultType ?? 'no result'}, expected a ${resultType}`);
  }
  return body.data.result;
}
