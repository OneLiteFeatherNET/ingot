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

export function runnerEnvironment(run: RunIdentity): RunEnvironment {
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
  return {
    variables: [{ name: 'K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM', value: 'true' }],
    arguments: ['--out', 'experimental-prometheus-rw', ...tagArguments],
  };
}
