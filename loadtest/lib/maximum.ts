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
// The maximum sustainable rate of a topology and a scenario (design D3). A pure function: the caller hands in the
// plateau steps with their verdicts, so the result needs neither k6 nor a clock nor the network.

/** One plateau step as the pass rule judged it: its arrival rate and whether it passed. */
export interface MeasuredStep {
  /** The arrival rate of the step in requests per second. */
  readonly rate: number;
  /** Whether the step passed the pass rule for its whole hold. */
  readonly passed: boolean;
}

/**
 * Returns the rate of the highest passing step, or undefined when there is no maximum to report.
 * No maximum is reported when the breakpoint was not reached, because the plateau then did not start from the limit,
 * and when no step passes or there are no steps. Refuses a rate that is not a finite non-negative number.
 */
export function maximumSustainableRate(
  steps: readonly MeasuredStep[],
  breakpointReached: boolean,
): number | undefined {
  for (const step of steps) {
    if (!Number.isFinite(step.rate) || step.rate < 0) {
      throw new Error(
        `refusing a step rate of ${step.rate}: a rate is a finite number of requests per second, not negative`,
      );
    }
  }
  if (!breakpointReached) {
    return undefined;
  }
  let maximum: number | undefined;
  for (const step of steps) {
    if (step.passed && (maximum === undefined || step.rate > maximum)) {
      maximum = step.rate;
    }
  }
  return maximum;
}
