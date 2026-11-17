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

// The weights of the mix scenario, in one file. Pure, so the Node unit tests can check them.

import type { Scenario } from '../lib/config.ts';

/** Maven and Gradle clients read far more than they write. to be tuned by the maintainer: the weights are a placeholder. */
export const MIX_WEIGHTS: Readonly<Partial<Record<Scenario, number>>> = {
  resolve: 0.9,
  deploy: 0.1,
};

/** Picks a scenario for a random value in [0, 1), walking the weights in their declared order. */
export function pickScenario(weights: Readonly<Partial<Record<Scenario, number>>>, random: number): Scenario {
  let cumulative = 0;
  let last: Scenario | undefined;
  for (const [name, weight] of Object.entries(weights) as [Scenario, number][]) {
    cumulative += weight;
    last = name;
    if (random < cumulative) {
      return name;
    }
  }
  if (last === undefined) {
    throw new Error('the mix has no scenario to pick');
  }
  return last;
}
