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
// The pass rule of a plateau step (design D3). A pure function: the caller hands in the measurements of each minute of
// the hold, so the decision needs neither k6 nor a clock nor the network.

/** The measurements of one minute of a plateau step. */
export interface StepWindow {
  /** The p95 latency of the minute in milliseconds. */
  readonly p95Ms: number;
  /** Failed requests divided by all requests of the minute, from 0 to 1. */
  readonly errorRate: number;
  /** Iterations the arrival rate could not start during the minute. */
  readonly droppedIterations: number;
}

/** The limits of one scenario. The p95 limit is set per scenario; the error rate limit is the design's one percent. */
export interface PassLimits {
  readonly p95Ms: number;
  readonly maxErrorRate: number;
}

export type Breach = 'p95' | 'error-rate' | 'dropped-iterations';

export type StepVerdict =
  | { readonly passed: true }
  | { readonly passed: false; readonly reason: 'no-windows' }
  | { readonly passed: false; readonly reason: 'breach'; readonly window: number; readonly breach: Breach };

/**
 * A step passes only when every minute of its hold meets all three limits: the p95 latency is under its limit, the error
 * rate is under its limit and no iteration was dropped. A missing or NaN measurement counts as a breach, and a hold
 * without any minute does not pass, because nothing was shown to hold.
 */
export function evaluateStep(windows: readonly StepWindow[], limits: PassLimits): StepVerdict {
  if (windows.length === 0) {
    return { passed: false, reason: 'no-windows' };
  }
  for (const [index, window] of windows.entries()) {
    const breach = breachOf(window, limits);
    if (breach !== undefined) {
      return { passed: false, reason: 'breach', window: index, breach };
    }
  }
  return { passed: true };
}

function breachOf(window: StepWindow, limits: PassLimits): Breach | undefined {
  // Written as negated comparisons so that NaN fails the check instead of slipping through.
  if (!(window.p95Ms < limits.p95Ms)) {
    return 'p95';
  }
  if (!(window.errorRate < limits.maxErrorRate)) {
    return 'error-rate';
  }
  if (!(window.droppedIterations === 0)) {
    return 'dropped-iterations';
  }
  return undefined;
}
