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

import type { Coordinate } from './resolve-plan.ts';
import { deployIteration } from './deploy.ts';
import { DEPLOY_SIZES } from './deploy-plan.ts';
import { MIX_WEIGHTS, pickScenario } from './mix-plan.ts';
import { resolveIteration } from './resolve.ts';

export { seedResolveFixture as setup } from './resolve.ts';

export default function (coordinate: Coordinate): void {
  const scenario = pickScenario(MIX_WEIGHTS, Math.random());
  if (scenario === 'resolve') {
    resolveIteration(coordinate, __ITER % 2 === 0 ? 'maven' : 'gradle');
  } else if (scenario === 'deploy') {
    deployIteration(__VU * 1_000_000 + __ITER, DEPLOY_SIZES[__ITER % DEPLOY_SIZES.length] ?? '1KB');
  } else {
    throw new Error(`the mix has no iteration for scenario ${scenario}`);
  }
}
