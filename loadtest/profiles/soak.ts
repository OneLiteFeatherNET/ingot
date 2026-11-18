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
// The soak profile: hold the maximum sustainable rate for a long time (design D3). Pure: it returns k6 options and
// reads neither the clock nor the network.

import type { ConstantArrivalRateScenario } from 'k6/options';
import { ARRIVAL_TIME_UNIT, arrivalVus } from './arrival.ts';

/** A soak holds its rate for two hours by default. */
export const SOAK_DEFAULT_MINUTES = 120;

export interface SoakInput {
  /** The maximum sustainable rate of the topology and scenario, in requests per second. */
  readonly rate: number;
  /** The exported function of the scenario module that each iteration runs. */
  readonly exec: string;
  /** How long the rate is held, in minutes. */
  readonly minutes?: number;
}

export function soakScenario(input: SoakInput): ConstantArrivalRateScenario {
  const minutes = input.minutes ?? SOAK_DEFAULT_MINUTES;
  if (!Number.isFinite(input.rate) || input.rate < 1) {
    throw new Error(`refusing a soak rate of ${input.rate}: a soak holds at least one request per second`);
  }
  if (!Number.isFinite(minutes) || minutes < 1) {
    throw new Error(`refusing a soak of ${minutes} minutes: a soak runs at least one minute`);
  }
  return {
    executor: 'constant-arrival-rate',
    rate: input.rate,
    timeUnit: ARRIVAL_TIME_UNIT,
    duration: `${minutes}m`,
    exec: input.exec,
    ...arrivalVus(input.rate),
  };
}
