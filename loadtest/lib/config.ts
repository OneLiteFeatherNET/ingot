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
// Shared constants and the option parsing of the load test runner. Nothing here reads the clock, the network or the
// environment, so the unit tests can run it as it is.

/** The k6 release that runs the scripts. The one place that pins it; the runner reads the image from here. */
export const K6_VERSION = '1.8.2';
export const K6_IMAGE = `grafana/k6:${K6_VERSION}`;

/** The only namespace the load tests may run in. Production and staging are never a valid target. */
export const LOAD_NAMESPACE = 'ingot-loadtest';

export const TOPOLOGIES = [
  'monolith',
  'ha',
  'split',
  'scalable',
  'scalable-read-1',
  'scalable-read-2',
  'scalable-read-4',
] as const;

export const PROFILES = ['breakpoint', 'plateau', 'soak'] as const;

export const SCENARIOS = [
  'resolve',
  'resolve-miss',
  'metadata',
  'deploy',
  'mirror',
  'auth',
  'search-browse',
  'dashboard',
  'mix',
] as const;

export type Topology = (typeof TOPOLOGIES)[number];
export type Profile = (typeof PROFILES)[number];
export type Scenario = (typeof SCENARIOS)[number];

/** A plateau step is held for 15 minutes by default. Warm-up needs several minutes, so 10 is the floor. */
export const PLATEAU_DEFAULT_HOLD_MINUTES = 15;
export const PLATEAU_MIN_HOLD_MINUTES = 10;

/** The label of the node pool that runs reference runs only (design D12). The runner selects and tolerates it. */
export const LOADTEST_NODE_LABEL = 'ingot.onelitefeather.dev/loadtest';

/** The Helm release of the Ingot under test. Its pods carry this value in the `app.kubernetes.io/instance` label. */
export const INGOT_RELEASE = 'ingot-loadtest';

export function assertLoadNamespace(namespace: string): string {
  if (namespace !== LOAD_NAMESPACE) {
    throw new Error(`refusing namespace "${namespace}": load tests run only in "${LOAD_NAMESPACE}"`);
  }
  return namespace;
}

export function assertPlateauHold(minutes: number): number {
  if (!(minutes >= PLATEAU_MIN_HOLD_MINUTES)) {
    throw new Error(
      `refusing a plateau hold of ${minutes} minutes: a step is held at least ${PLATEAU_MIN_HOLD_MINUTES} minutes`,
    );
  }
  return minutes;
}

export interface RunOptions {
  readonly topology: Topology;
  readonly profile: Profile;
  readonly scenario: Scenario;
}

/** Reads `--topology`, `--profile` and `--scenario` from an argument list, each followed by its value. */
export function parseRunOptions(argv: readonly string[]): RunOptions {
  return {
    topology: pick('--topology', argv, TOPOLOGIES),
    profile: pick('--profile', argv, PROFILES),
    scenario: pick('--scenario', argv, SCENARIOS),
  };
}

function pick<T extends string>(flag: string, argv: readonly string[], allowed: readonly T[]): T {
  const index = argv.indexOf(flag);
  const value = index === -1 ? undefined : argv[index + 1];
  if (value === undefined) {
    throw new Error(`missing ${flag}; expected one of: ${allowed.join(', ')}`);
  }
  const match = allowed.find((candidate) => candidate === value);
  if (match === undefined) {
    throw new Error(`unknown value "${value}" for ${flag}; expected one of: ${allowed.join(', ')}`);
  }
  return match;
}
