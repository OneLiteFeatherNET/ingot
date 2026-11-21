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

// The mix scenario: each iteration runs one of the scenarios, picked by the weights in mix-plan.ts.

import { authIteration } from './auth.ts';
import { dashboardIteration } from './dashboard.ts';
import { deployIteration } from './deploy.ts';
import { DEPLOY_SIZES } from './deploy-plan.ts';
import { metadataIteration } from './metadata.ts';
import { MIX_WEIGHTS, pickScenario } from './mix-plan.ts';
import { missIteration } from './resolve-miss.ts';
import { type Coordinate, RESOLVE_FIXTURE_COORDINATE } from './resolve-plan.ts';
import { resolveIteration, seedResolveFixture } from './resolve.ts';
import { PRIVATE_REPOSITORY } from './remaining-plan.ts';
import { searchBrowseIteration } from './search-browse.ts';
import { thresholdFor } from '../lib/thresholds.ts';
import { profileOptions } from '../profiles/options.ts';

/** The executor of the profile the runner chose, with the p95 limit of this scenario. */
export const options = profileOptions(__ENV, 'mix', thresholdFor('mix').p95Ms);

export function setup(): Coordinate {
  seedResolveFixture(PRIVATE_REPOSITORY);
  return seedResolveFixture();
}

export default function (coordinate: Coordinate): void {
  const scenario = pickScenario(MIX_WEIGHTS, Math.random());
  switch (scenario) {
    case 'resolve':
      resolveIteration(coordinate, __ITER % 2 === 0 ? 'maven' : 'gradle');
      break;
    case 'resolve-miss':
      missIteration(RESOLVE_FIXTURE_COORDINATE);
      break;
    case 'metadata':
      metadataIteration(coordinate);
      break;
    case 'deploy':
      deployIteration(__VU * 1_000_000 + __ITER, DEPLOY_SIZES[__ITER % DEPLOY_SIZES.length] ?? '1KB');
      break;
    case 'auth':
      authIteration();
      break;
    case 'search-browse':
      searchBrowseIteration();
      break;
    case 'dashboard':
      dashboardIteration();
      break;
    default:
      throw new Error(`the mix has no iteration for scenario ${scenario}`);
  }
}
