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
// The breakpoint profile (design D3): ramp the arrival rate of one scenario until a threshold breaks. Pure: it returns
// k6 options and reads neither the clock nor the network. The run stops at the rate where the threshold broke, which
// gives the upper bound U of the plateau phase.
//
// A warm-up at a low constant rate runs before the ramp. k6 evaluates a threshold over every sample since the start of
// the run, so the slow first requests of a cold JVM would otherwise stay in the p95 and abort the ramp long before the
// server is saturated. The aborting thresholds therefore only count requests tagged with the ramp phase, and the ramp
// starts when the warm-up ends: the break rate is read from the ramp start time, not from the start of the run.

import type { ConstantArrivalRateScenario, ObjectThreshold, RampingArrivalRateScenario } from 'k6/options';
import { MAX_ERROR_RATE } from '../lib/thresholds.ts';
import { ARRIVAL_TIME_UNIT, arrivalVus } from './arrival.ts';

/** The ramp reaches this rate when no threshold breaks. Provisional: it is set per run through the input. */
export const BREAKPOINT_DEFAULT_CEILING = 1_000;
/** The ramp runs this long to reach the ceiling by default. */
export const BREAKPOINT_DEFAULT_MINUTES = 10;
export const BREAKPOINT_START_RATE = 1;
/** The warm-up holds this rate, in requests per second, before the ramp starts. */
export const BREAKPOINT_WARMUP_RATE = 5;
/** The warm-up runs this long. The ramp starts when it ends. */
export const BREAKPOINT_WARMUP_SECONDS = 120;
/** The tag value of the warm-up requests, which no threshold counts. */
export const WARMUP_PHASE = 'warmup';
/** The tag value of the ramp requests, which the aborting thresholds count. */
export const RAMP_PHASE = 'ramp';
/**
 * The first seconds of the ramp cannot abort the run either, because a threshold over a handful of samples is noise. k6
 * counts the delay from the start of the run, so it covers the warm-up as well.
 */
export const BREAKPOINT_RAMP_ABORT_DELAY_SECONDS = 30;
export const BREAKPOINT_DELAY_ABORT_EVAL = `${BREAKPOINT_WARMUP_SECONDS + BREAKPOINT_RAMP_ABORT_DELAY_SECONDS}s`;

export interface BreakpointInput {
  /** The exported function of the scenario module that each iteration runs. */
  readonly exec: string;
  /** The p95 latency limit in milliseconds, from the thresholds of the scenario. */
  readonly p95Ms: number;
  /** The highest rate the ramp reaches, in requests per second. */
  readonly ceiling?: number;
  /** How long the ramp takes to reach the ceiling, in minutes. */
  readonly minutes?: number;
  /** Stops the run at the first broken threshold. Off runs the whole ramp, as the first measured run does. */
  readonly abortOnFail?: boolean;
}

export interface BreakpointProfile {
  /** The low constant rate before the ramp. Its requests carry the warm-up phase tag and break no threshold. */
  readonly warmup: ConstantArrivalRateScenario;
  readonly scenario: RampingArrivalRateScenario;
  readonly thresholds: Record<string, ObjectThreshold[]>;
}

/** Builds the warm-up, the ramping arrival rate of the breakpoint run and its aborting thresholds on the ramp. */
export function breakpointScenario(input: BreakpointInput): BreakpointProfile {
  const ceiling = input.ceiling ?? BREAKPOINT_DEFAULT_CEILING;
  const minutes = input.minutes ?? BREAKPOINT_DEFAULT_MINUTES;
  if (!Number.isFinite(ceiling) || ceiling < 1) {
    throw new Error(`refusing a ceiling of ${ceiling}: the ramp reaches at least one request per second`);
  }
  if (!Number.isFinite(minutes) || minutes < 1) {
    throw new Error(`refusing a breakpoint ramp of ${minutes} minutes: the ramp runs at least one minute`);
  }
  if (!Number.isFinite(input.p95Ms) || input.p95Ms <= 0) {
    throw new Error(`refusing a p95 limit of ${input.p95Ms} ms: a limit is a positive number of milliseconds`);
  }
  const warmup: ConstantArrivalRateScenario = {
    executor: 'constant-arrival-rate',
    rate: BREAKPOINT_WARMUP_RATE,
    timeUnit: ARRIVAL_TIME_UNIT,
    duration: `${BREAKPOINT_WARMUP_SECONDS}s`,
    exec: input.exec,
    tags: { phase: WARMUP_PHASE },
    ...arrivalVus(BREAKPOINT_WARMUP_RATE),
  };
  const scenario: RampingArrivalRateScenario = {
    executor: 'ramping-arrival-rate',
    startTime: `${BREAKPOINT_WARMUP_SECONDS}s`,
    startRate: BREAKPOINT_START_RATE,
    timeUnit: ARRIVAL_TIME_UNIT,
    stages: [{ target: ceiling, duration: `${minutes}m` }],
    exec: input.exec,
    tags: { phase: RAMP_PHASE },
    ...arrivalVus(ceiling),
  };
  const abort = { abortOnFail: input.abortOnFail ?? true, delayAbortEval: BREAKPOINT_DELAY_ABORT_EVAL };
  return {
    warmup,
    scenario,
    // The error rate breaks too: a refused connection is a failed request with a duration near zero, so a server that
    // stops answering would otherwise keep the p95 low.
    thresholds: {
      [`http_req_duration{phase:${RAMP_PHASE}}`]: [
        {
          threshold: `p(95)<${input.p95Ms}`,
          ...abort,
        },
      ],
      [`http_req_failed{phase:${RAMP_PHASE}}`]: [{ threshold: `rate<${MAX_ERROR_RATE}`, ...abort }],
    },
  };
}

/**
 * The rate the ramp had reached a number of seconds after the start of the run, for example when the run aborted. The
 * warm-up is subtracted first: before the ramp starts there is no ramp rate, and the result is undefined.
 */
export function breakpointRateAt(
  secondsSinceRunStart: number,
  input: Pick<BreakpointInput, 'ceiling' | 'minutes'> = {},
): number | undefined {
  const ceiling = input.ceiling ?? BREAKPOINT_DEFAULT_CEILING;
  const rampSeconds = (input.minutes ?? BREAKPOINT_DEFAULT_MINUTES) * 60;
  const secondsIntoRamp = secondsSinceRunStart - BREAKPOINT_WARMUP_SECONDS;
  if (!Number.isFinite(secondsIntoRamp) || secondsIntoRamp < 0) {
    return undefined;
  }
  const share = Math.min(1, secondsIntoRamp / rampSeconds);
  return BREAKPOINT_START_RATE + (ceiling - BREAKPOINT_START_RATE) * share;
}
