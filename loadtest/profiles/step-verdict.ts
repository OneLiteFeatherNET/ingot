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
// The verdict of a plateau step (design D3 and D9). The pass rule needs every minute of the hold across all virtual users
// and all load generator pods, and no single virtual user sees that. So one extra scenario per step starts when the hold
// ends, reads the minutes back from the metrics store the run writes to, applies the pass rule and emits the step metric.
// This file is the pure part: no k6 runtime, no clock and no network. The k6 part is step-verdict-k6.ts.

import type { SharedIterationsScenario } from 'k6/options';
import type { StepWindow } from '../lib/pass-rule.ts';
import type { PlateauStep } from './plateau.ts';
import type { MetricSink } from './step-metric.ts';

/** The function of the scenario module each verdict scenario runs. Every plateau scenario module exports it. */
export const VERDICT_EXEC = 'stepVerdict';

/**
 * How long the verdict waits after the hold before it reads the store, so the last minute of the hold has been pushed.
 * k6 pushes its remote write samples every five seconds by default.
 */
export const VERDICT_DELAY_SECONDS = 15;

/** The name tag of the requests the verdict sends to the store. The step queries leave them out. */
export const VERDICT_REQUEST_NAME = 'ingot-load-step-verdict';

const K6_PREFIX = 'k6_';
const TEST_ID = /^[a-z0-9][a-z0-9-]{0,39}$/;
const HTTP_URL = /^https?:\/\/\S+$/;

/**
 * The Prometheus remote write paths and the path of the query API on the same host: Mimir pushes to /api/v1/push and
 * serves the API under /prometheus, a Prometheus receiver takes /api/v1/write and serves the API at its root.
 */
const QUERY_PREFIXES: readonly [string, string][] = [
  ['/api/v1/push', '/prometheus'],
  ['/api/v1/write', ''],
];

/** One verdict scenario per step, named verdict-1 to verdict-N, each starting when the hold of its step ends. */
export function plateauVerdictScenarios(
  steps: readonly PlateauStep[],
  scenario: string,
  p95Ms: number,
): Record<string, SharedIterationsScenario> {
  const scenarios: Record<string, SharedIterationsScenario> = {};
  for (const step of steps) {
    scenarios[`verdict-${step.index}`] = {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      startTime: `${step.index * step.holdMinutes}m`,
      exec: VERDICT_EXEC,
      env: {
        LOADTEST_STEP_RATE: String(step.rate),
        LOADTEST_STEP_HOLD_MINUTES: String(step.holdMinutes),
        LOADTEST_STEP_P95_MS: String(p95Ms),
        LOADTEST_STEP_SCENARIO: scenario,
      },
    };
  }
  return scenarios;
}

export interface VerdictInput {
  readonly testId: string;
  readonly scenario: string;
  /** The arrival rate of the step in requests per second. */
  readonly rate: number;
  readonly holdMinutes: number;
  readonly p95Ms: number;
  /** When the hold of the step started, in epoch seconds. */
  readonly stepStartSeconds: number;
}

/**
 * Reads the step of a verdict scenario from its variables and the run tags. The verdict scenario starts when the hold
 * ends, so the hold started one hold before the verdict scenario did.
 */
export function verdictInput(
  env: Readonly<Record<string, string | undefined>>,
  tags: Readonly<Record<string, string>> | undefined,
  scenarioStartMs: number,
): VerdictInput {
  const testId = assertTestId(tags?.testid ?? '');
  const holdMinutes = requiredNumber(env, 'LOADTEST_STEP_HOLD_MINUTES');
  const scenario = env.LOADTEST_STEP_SCENARIO;
  if (!scenario) {
    throw new Error('set LOADTEST_STEP_SCENARIO: the verdict scenario names the scenario it judges');
  }
  return {
    testId,
    scenario,
    rate: requiredNumber(env, 'LOADTEST_STEP_RATE'),
    holdMinutes,
    p95Ms: requiredNumber(env, 'LOADTEST_STEP_P95_MS'),
    stepStartSeconds: Math.round((scenarioStartMs - holdMinutes * 60_000) / 1000),
  };
}

/** The base url of the Prometheus HTTP API on the host of the remote write endpoint the run writes to. */
export function queryApiUrl(remoteWriteUrl: string): string {
  for (const [pushPath, queryPath] of QUERY_PREFIXES) {
    if (HTTP_URL.test(remoteWriteUrl) && remoteWriteUrl.endsWith(pushPath)) {
      return `${remoteWriteUrl.slice(0, -pushPath.length)}${queryPath}`;
    }
  }
  throw new Error(`the query API cannot be derived from the remote write endpoint "${remoteWriteUrl}"`);
}

export type StepSeries = 'p95' | 'errorRate' | 'dropped';

/**
 * The three measurements of the pass rule, each evaluated at the end of one minute. The counters are read as their
 * value minus their value a minute before, with an absent series as zero, because a counter that first appears within
 * the minute has no earlier sample and `increase` would not see it. That is exactly the case of dropped iterations.
 */
export function stepQueries(testId: string): Record<StepSeries, string> {
  const run = `testid="${assertTestId(testId)}"`;
  const requests = `${run},name!="${VERDICT_REQUEST_NAME}"`;
  const change = (selector: string): string =>
    `((sum(${selector}) or vector(0)) - (sum(${selector} offset 1m) or vector(0)))`;
  return {
    p95: `histogram_quantile(0.95, sum(rate(${K6_PREFIX}http_req_duration_seconds{${requests}}[1m])))`,
    errorRate: `${change(`${K6_PREFIX}http_reqs_total{${requests},expected_response="false"}`)} / ${change(`${K6_PREFIX}http_reqs_total{${requests}}`)}`,
    dropped: change(`${K6_PREFIX}dropped_iterations_total{${run}}`),
  };
}

/** The range query parameters of a step: one evaluation at the end of every minute of the hold. */
export function stepRange(stepStartSeconds: number, holdMinutes: number): Record<string, string> {
  return {
    start: String(stepStartSeconds + 60),
    end: String(stepStartSeconds + holdMinutes * 60),
    step: '60',
  };
}

/**
 * The url of one range query. Encoded by hand because the k6 runtime has no URLSearchParams.
 */
export function queryRangeUrl(base: string, query: string, range: Readonly<Record<string, string>>): string {
  const params = Object.entries({ query, ...range })
    .map(([name, value]) => `${encodeURIComponent(name)}=${encodeURIComponent(value)}`)
    .join('&');
  return `${base}/api/v1/query_range?${params}`;
}

/**
 * Turns the range answers into one window per minute of the hold. A minute an answer lacks, and every minute of an
 * answer that is an error, is NaN, which the pass rule counts as a breach.
 */
export function stepWindows(
  answers: Readonly<Record<StepSeries, unknown>>,
  stepStartSeconds: number,
  holdMinutes: number,
): StepWindow[] {
  const p95 = valuesOf(answers.p95);
  const errorRate = valuesOf(answers.errorRate);
  const dropped = valuesOf(answers.dropped);
  const windows: StepWindow[] = [];
  for (let minute = 1; minute <= holdMinutes; minute++) {
    const at = stepStartSeconds + minute * 60;
    windows.push({
      p95Ms: (p95.get(at) ?? Number.NaN) * 1000,
      errorRate: errorRate.get(at) ?? Number.NaN,
      droppedIterations: dropped.get(at) ?? Number.NaN,
    });
  }
  return windows;
}

/** A sink that adds the given tags to every sample, so the step metric names its scenario. */
export function withTags(sink: MetricSink, tags: Readonly<Record<string, string>>): MetricSink {
  return {
    add(name, value, sampleTags) {
      sink.add(name, value, { ...tags, ...sampleTags });
    },
  };
}

function valuesOf(answer: unknown): Map<number, number> {
  const values = new Map<number, number>();
  const body = answer as { status?: string; data?: { resultType?: string; result?: unknown } } | undefined;
  if (body?.status !== 'success' || body.data?.resultType !== 'matrix' || !Array.isArray(body.data.result)) {
    return values;
  }
  // Every query sums to one series, so the first one holds the answer.
  const series = body.data.result[0] as { values?: [number, string][] } | undefined;
  for (const [at, text] of series?.values ?? []) {
    values.set(Math.round(at), Number(text));
  }
  return values;
}

function assertTestId(testId: string): string {
  if (!TEST_ID.test(testId)) {
    throw new Error(`refusing testid "${testId}": use lower case letters, digits and hyphens, at most 40 characters`);
  }
  return testId;
}

function requiredNumber(env: Readonly<Record<string, string | undefined>>, name: string): number {
  const value = env[name];
  const number = value === undefined || value === '' ? Number.NaN : Number(value);
  if (!Number.isFinite(number)) {
    throw new Error(`set ${name} to a number, got "${value ?? ''}"`);
  }
  return number;
}
