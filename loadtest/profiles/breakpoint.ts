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

import type { ObjectThreshold, RampingArrivalRateScenario } from 'k6/options';
import { ARRIVAL_TIME_UNIT, arrivalVus } from './arrival.ts';

/** The ramp reaches this rate when no threshold breaks. Provisional: it is set per run through the input. */
export const BREAKPOINT_DEFAULT_CEILING = 1_000;
/** The ramp runs this long to reach the ceiling by default. */
export const BREAKPOINT_DEFAULT_MINUTES = 10;
export const BREAKPOINT_START_RATE = 1;
/** The first seconds of warm-up cannot abort the run, because connection pools and caches are still filling. */
export const BREAKPOINT_DELAY_ABORT_EVAL = '30s';

export interface BreakpointInput {
  /** The exported function of the scenario module that each iteration runs. */
  readonly exec: string;
  /** The p95 latency limit in milliseconds, from the thresholds of the scenario. */
  readonly p95Ms: number;
  /** The highest rate the ramp reaches, in requests per second. */
  readonly ceiling?: number;
  /** How long the ramp takes to reach the ceiling, in minutes. */
  readonly minutes?: number;
}

export interface BreakpointProfile {
  readonly scenario: RampingArrivalRateScenario;
  readonly thresholds: Record<string, ObjectThreshold[]>;
}

/** Builds the ramping arrival rate of the breakpoint run and its aborting p95 threshold. */
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
  const scenario: RampingArrivalRateScenario = {
    executor: 'ramping-arrival-rate',
    startRate: BREAKPOINT_START_RATE,
    timeUnit: ARRIVAL_TIME_UNIT,
    stages: [{ target: ceiling, duration: `${minutes}m` }],
    exec: input.exec,
    ...arrivalVus(ceiling),
  };
  return {
    scenario,
    thresholds: {
      http_req_duration: [
        {
          threshold: `p(95)<${input.p95Ms}`,
          abortOnFail: true,
          delayAbortEval: BREAKPOINT_DELAY_ABORT_EVAL,
        },
      ],
    },
  };
}
