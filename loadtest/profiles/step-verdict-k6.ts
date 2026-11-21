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
// The k6 part of the step verdict: it waits for the last samples of the hold, reads the minutes of the step from the
// metrics store, applies the pass rule and emits the step metric. It imports the k6 runtime, so no unit test imports
// this file; the pure parts are tested in step-verdict.ts, pass-rule.ts and step-metric.ts.

import { sleep } from 'k6';
import exec from 'k6/execution';
import http from 'k6/http';
import { evaluateStep } from '../lib/pass-rule.ts';
import { MAX_ERROR_RATE } from '../lib/thresholds.ts';
import { STEP_PASSED_METRIC, emitStepPassed } from './step-metric.ts';
import { k6MetricSink } from './step-metric-k6.ts';
import {
  VERDICT_DELAY_SECONDS,
  VERDICT_REQUEST_NAME,
  type StepSeries,
  queryApiUrl,
  queryRangeUrl,
  stepQueries,
  stepRange,
  stepWindows,
  verdictInput,
  withTags,
} from './step-verdict.ts';

// Built in the init context, because k6 refuses a metric declared during an iteration.
const SINK = k6MetricSink([STEP_PASSED_METRIC]);

/** The exec function of every verdict scenario. A plateau scenario module re-exports it. */
export function stepVerdict(): void {
  sleep(VERDICT_DELAY_SECONDS);
  const input = verdictInput(__ENV, exec.test.options.tags, exec.scenario.startTime);
  const base = queryApiUrl(__ENV.K6_PROMETHEUS_RW_SERVER_URL ?? '');
  const range = stepRange(input.stepStartSeconds, input.holdMinutes);
  const queries = stepQueries(input.testId);
  const answers = {} as Record<StepSeries, unknown>;
  for (const series of Object.keys(queries) as StepSeries[]) {
    const response = http.get(queryRangeUrl(base, queries[series], range), { tags: { name: VERDICT_REQUEST_NAME } });
    try {
      answers[series] = response.json();
    } catch {
      console.error(`step verdict: the metrics store answered ${response.status} to the ${series} query`);
      answers[series] = undefined;
    }
  }
  const verdict = evaluateStep(stepWindows(answers, input.stepStartSeconds, input.holdMinutes), {
    p95Ms: input.p95Ms,
    maxErrorRate: MAX_ERROR_RATE,
  });
  if (!verdict.passed) {
    console.warn(`step at ${input.rate}/s failed: ${JSON.stringify(verdict)}`);
  }
  emitStepPassed(withTags(SINK, { scenario: input.scenario }), { rate: input.rate, passed: verdict.passed });
}
