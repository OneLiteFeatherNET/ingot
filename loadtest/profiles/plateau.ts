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
// The step list of the plateau profile (design D3): five constant-rate steps at 60, 70, 80, 90 and 100 percent of the
// breakpoint. Pure: no clock, no k6 runtime, no network. The k6 script maps each step to a constant-arrival-rate executor.

import type { ConstantArrivalRateScenario } from 'k6/options';
import { PLATEAU_DEFAULT_HOLD_MINUTES, assertPlateauHold } from '../lib/config.ts';
import { ARRIVAL_TIME_UNIT, arrivalVus } from './arrival.ts';

/** The share of the breakpoint each plateau step runs at, in the order the steps run. */
export const PLATEAU_PERCENTAGES = [60, 70, 80, 90, 100] as const;

export interface PlateauStep {
  /** The position of the step in the plateau, counted from one. */
  readonly index: number;
  /** The share of the breakpoint this step runs at, in percent. */
  readonly percent: number;
  /** The arrival rate in requests per second, rounded to a whole number because k6 takes whole rates. */
  readonly rate: number;
  /** How long the step is held at its rate, in minutes. */
  readonly holdMinutes: number;
}

/**
 * Returns the five plateau steps for a breakpoint given in requests per second.
 * Refuses a breakpoint that is not a finite number of at least one request per second, and a hold under the minimum.
 */
export function plateauSteps(
  breakpoint: number,
  holdMinutes: number = PLATEAU_DEFAULT_HOLD_MINUTES,
): readonly PlateauStep[] {
  if (!Number.isFinite(breakpoint) || breakpoint < 1) {
    throw new Error(
      `refusing a breakpoint of ${breakpoint}: a plateau starts from a breakpoint of at least one request per second`,
    );
  }
  const hold = assertPlateauHold(holdMinutes);
  return PLATEAU_PERCENTAGES.map((percent, offset) => ({
    index: offset + 1,
    percent,
    rate: Math.round((breakpoint * percent) / 100),
    holdMinutes: hold,
  }));
}

/**
 * Maps the plateau steps to k6 constant-arrival-rate scenarios, named plateau-1 to plateau-N. Each step starts when the
 * steps before it have ended, so the steps run one after another in one run. Each step runs the given exec function.
 */
export function plateauScenarios(
  steps: readonly PlateauStep[],
  exec: string,
): Record<string, ConstantArrivalRateScenario> {
  const scenarios: Record<string, ConstantArrivalRateScenario> = {};
  for (const step of steps) {
    scenarios[`plateau-${step.index}`] = {
      executor: 'constant-arrival-rate',
      rate: step.rate,
      timeUnit: ARRIVAL_TIME_UNIT,
      duration: `${step.holdMinutes}m`,
      startTime: `${(step.index - 1) * step.holdMinutes}m`,
      exec,
      ...arrivalVus(step.rate),
    };
  }
  return scenarios;
}
