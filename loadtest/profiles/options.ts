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
// The k6 options of a scenario script for the profile the runner chose. The runner passes LOADTEST_PROFILE and the
// profile inputs as script variables, and a scenario exports what this returns as its `options`. Without it k6 runs a
// single iteration. Pure: the caller hands in __ENV, so the unit tests pass a plain object.

import type { Options } from 'k6/options';
import { PROFILES, type Profile } from '../lib/config.ts';
import { breakpointScenario } from './breakpoint.ts';
import { plateauScenarios, plateauSteps } from './plateau.ts';
import { soakScenario } from './soak.ts';
import { plateauVerdictScenarios } from './step-verdict.ts';

export type ScriptVariables = Readonly<Record<string, string | undefined>>;

/** The function of the scenario module that each iteration runs. */
const EXEC = 'default';

export function profileOptions(env: ScriptVariables, scenario: string, p95Ms: number): Options {
  switch (profileOf(env)) {
    case 'breakpoint': {
      const breakpoint = breakpointScenario({
        exec: EXEC,
        p95Ms,
        ceiling: optionalNumber(env, 'LOADTEST_CEILING'),
        minutes: optionalNumber(env, 'LOADTEST_RAMP_MINUTES'),
        abortOnFail: env.LOADTEST_ABORT_ON_FAIL !== 'false',
      });
      return { scenarios: { [scenario]: breakpoint.scenario }, thresholds: breakpoint.thresholds };
    }
    case 'plateau': {
      const steps = plateauSteps(
        requiredNumber(env, 'LOADTEST_BREAKPOINT'),
        optionalNumber(env, 'LOADTEST_HOLD_MINUTES'),
      );
      return { scenarios: { ...plateauScenarios(steps, EXEC), ...plateauVerdictScenarios(steps, scenario, p95Ms) } };
    }
    case 'soak':
      return {
        scenarios: {
          [scenario]: soakScenario({
            exec: EXEC,
            rate: requiredNumber(env, 'LOADTEST_RATE'),
            minutes: optionalNumber(env, 'LOADTEST_SOAK_MINUTES'),
          }),
        },
      };
  }
}

function profileOf(env: ScriptVariables): Profile {
  const value = env.LOADTEST_PROFILE;
  const profile = PROFILES.find((candidate) => candidate === value);
  if (profile === undefined) {
    throw new Error(`set LOADTEST_PROFILE to one of ${PROFILES.join(', ')}, got "${value ?? ''}"`);
  }
  return profile;
}

function optionalNumber(env: ScriptVariables, name: string): number | undefined {
  const value = env[name];
  if (value === undefined || value === '') {
    return undefined;
  }
  const number = Number(value);
  if (!Number.isFinite(number)) {
    throw new Error(`${name} must be a number, got "${value}"`);
  }
  return number;
}

function requiredNumber(env: ScriptVariables, name: string): number {
  const number = optionalNumber(env, name);
  if (number === undefined) {
    throw new Error(`set ${name}: the profile ${env.LOADTEST_PROFILE ?? ''} needs it`);
  }
  return number;
}
