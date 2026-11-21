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
// The k6 environment of one run: the remote write output with native histograms, and the tags of design D8 on every
// sample. Only the values are checked here; no k6 runtime and no cluster is needed to build them.

export interface RunIdentity {
  /** Generated per run, a lower case DNS label such as `lt-3k9x2a`. */
  readonly testId: string;
  readonly topology: string;
  readonly profile: string;
  readonly scenario: string;
  /** The image tag the runner installed. */
  readonly ingotVersion: string;
}

/** Where the run sends its samples and its requests. */
export interface RunTargets {
  /** The Prometheus remote write endpoint of the metrics store, from INGOT_LOADTEST_RW_URL. */
  readonly remoteWriteUrl: string;
  /** The Ingot Service the scenario sends its requests to. */
  readonly baseUrl: string;
  /** The profile inputs handed to the script, see `scriptVariables`. */
  readonly scriptVariables?: Readonly<Record<string, string>>;
}

/** The profile inputs the scenarios read (profiles/options.ts). The runner passes those that are set through. */
export const SCRIPT_VARIABLES = [
  'LOADTEST_CEILING',
  'LOADTEST_RAMP_MINUTES',
  'LOADTEST_ABORT_ON_FAIL',
  'LOADTEST_BREAKPOINT',
  'LOADTEST_HOLD_MINUTES',
  'LOADTEST_RATE',
  'LOADTEST_SOAK_MINUTES',
] as const;

export function scriptVariables(env: Readonly<Record<string, string | undefined>>): Record<string, string> {
  const variables: Record<string, string> = {};
  for (const name of SCRIPT_VARIABLES) {
    const value = env[name];
    if (value) {
      variables[name] = value;
    }
  }
  return variables;
}

export interface EnvironmentVariable {
  readonly name: string;
  readonly value: string;
}

export interface RunEnvironment {
  readonly variables: readonly EnvironmentVariable[];
  /** Arguments of `k6 run`, passed as one string to the TestRun. */
  readonly arguments: readonly string[];
}

const TEST_ID = /^[a-z0-9][a-z0-9-]{0,39}$/;
const TAG_VALUE = /^[A-Za-z0-9._-]+$/;
const HTTP_URL = /^https?:\/\/\S+$/;

/** The variable the runner reads the remote write endpoint from. Without it k6 writes to its own localhost. */
export const REMOTE_WRITE_URL_VARIABLE = 'INGOT_LOADTEST_RW_URL';

export function remoteWriteUrl(env: Readonly<Record<string, string | undefined>>): string {
  const url = env[REMOTE_WRITE_URL_VARIABLE];
  if (!url) {
    throw new Error(`set ${REMOTE_WRITE_URL_VARIABLE} to the Prometheus remote write endpoint of the metrics store`);
  }
  return url;
}

export function runnerEnvironment(run: RunIdentity, targets: RunTargets): RunEnvironment {
  if (!TEST_ID.test(run.testId)) {
    throw new Error(`refusing testid "${run.testId}": use lower case letters, digits and hyphens, at most 40 characters`);
  }
  const tags: readonly [string, string][] = [
    ['testid', run.testId],
    ['topology', run.topology],
    ['scenario', run.scenario],
    ['profile', run.profile],
    ['ingot_version', run.ingotVersion],
  ];
  const tagArguments = tags.flatMap(([name, value]) => {
    if (!TAG_VALUE.test(value)) {
      throw new Error(`refusing ${name} "${value}": a tag value holds letters, digits, dots, underscores and hyphens`);
    }
    return ['--tag', `${name}=${value}`];
  });
  if (!HTTP_URL.test(targets.remoteWriteUrl)) {
    throw new Error(`refusing remote write url "${targets.remoteWriteUrl}": it starts with http:// or https://`);
  }
  return {
    variables: [
      { name: 'K6_PROMETHEUS_RW_SERVER_URL', value: targets.remoteWriteUrl },
      { name: 'K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM', value: 'true' },
      { name: 'BASE_URL', value: targets.baseUrl },
    ],
    arguments: [
      '--out', 'experimental-prometheus-rw',
      ...tagArguments,
      ...scriptArguments(run.profile, targets.scriptVariables ?? {}),
      '--include-system-env-vars',
    ],
  };
}

/** The `--env` arguments of `k6 run` and `k6 archive`: the profile, then the profile inputs. */
export function scriptArguments(profile: string, variables: Readonly<Record<string, string>>): string[] {
  return [`LOADTEST_PROFILE=${profile}`, ...Object.entries(variables).map(([name, value]) => `${name}=${value}`)]
    .flatMap((variable) => ['--env', variable]);
}
