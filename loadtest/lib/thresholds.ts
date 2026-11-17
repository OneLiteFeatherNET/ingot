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
// The thresholds of every scenario, declared in one place (design D3). The numbers are a starting point only:
// every value is marked "to be tuned by the maintainer" and changes after the first reference runs.
// A run may override a value; the overrides are not part of this file.

import type { ArtifactSize } from './artifacts.ts';
import type { Scenario } from './config.ts';

/** A plateau step passes only while its error rate stays under this limit. Applies to every scenario. */
export const MAX_ERROR_RATE = 0.01;

export interface ScenarioThreshold {
  readonly scenario: Scenario;
  /** Set for the deploy scenario, whose latency depends on the artifact size. */
  readonly size?: ArtifactSize;
  /** The p95 latency limit in milliseconds. */
  readonly p95Ms: number;
}

// to be tuned by the maintainer: every value below except resolve (150 ms) and the 1 MB deploy (2 s) is a placeholder.
export const THRESHOLDS: readonly ScenarioThreshold[] = [
  { scenario: 'resolve', p95Ms: 150 },
  { scenario: 'resolve-miss', p95Ms: 150 },
  { scenario: 'metadata', p95Ms: 150 },
  { scenario: 'deploy', size: '1KB', p95Ms: 1_000 },
  { scenario: 'deploy', size: '1MB', p95Ms: 2_000 },
  { scenario: 'deploy', size: '50MB', p95Ms: 15_000 },
  { scenario: 'deploy', size: '200MB', p95Ms: 60_000 },
  { scenario: 'mirror', p95Ms: 300 },
  { scenario: 'auth', p95Ms: 300 },
  { scenario: 'search-browse', p95Ms: 500 },
  { scenario: 'dashboard', p95Ms: 500 },
  { scenario: 'mix', p95Ms: 500 },
];

export function thresholdFor(scenario: Scenario, size?: ArtifactSize): ScenarioThreshold {
  const entry = THRESHOLDS.find((candidate) => candidate.scenario === scenario && candidate.size === size);
  if (entry === undefined) {
    throw new Error(
      size === undefined
        ? `no threshold for scenario ${scenario}; a scenario with sizes needs one of them`
        : `no threshold for scenario ${scenario} with size ${size}`,
    );
  }
  return entry;
}
