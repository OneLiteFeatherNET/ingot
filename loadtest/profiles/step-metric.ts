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
// The step result metric (design D9): one sample per plateau step, with the step rate as its value and a result tag.
// Pure: the sink is injected, so the unit tests use a fake and never load the k6 runtime. The k6 sink is in
// step-metric-k6.ts.

export const STEP_PASSED_METRIC = 'ingot_load_step_passed';

export interface MetricSample {
  readonly name: string;
  readonly value: number;
  readonly tags: Record<string, string>;
}

export interface MetricSink {
  add(name: string, value: number, tags: Record<string, string>): void;
}

export interface StepOutcome {
  /** The arrival rate of the step in requests per second. */
  readonly rate: number;
  /** Whether the step passed the pass rule for its whole hold. */
  readonly passed: boolean;
}

/** Emits the result of one plateau step, at the end of the step, to the sink. */
export function emitStepPassed(sink: MetricSink, step: StepOutcome): void {
  if (!Number.isFinite(step.rate) || step.rate < 0) {
    throw new Error(
      `refusing a step rate of ${step.rate}: a rate is a finite number of requests per second, not negative`,
    );
  }
  sink.add(STEP_PASSED_METRIC, step.rate, { result: step.passed ? 'pass' : 'fail' });
}
