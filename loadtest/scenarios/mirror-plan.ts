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

// The request list of the mirror scenario: a proxy repository of Ingot fetches the seeded artifacts from the in-cluster upstream
// mock of task 5.3. Pure: no k6 module, so the Node unit tests run it. The k6 scenario file only sends these requests.

import { ARTIFACT_SIZES, type ArtifactSize } from '../lib/artifacts.ts';

/** The proxy repository that mirrors the upstream. The runner must configure it under this name, with the upstream below. */
export const MIRROR_REPOSITORY = 'mirror';
/** The upstream mock inside the load test namespace. The mirror never leaves the cluster. */
export const MIRROR_UPSTREAM_BASE_URL = 'http://upstream-mock.ingot-loadtest.svc.cluster.local:8080';
/** The mirror answers 200 for every seeded artifact. Anything else is a failed proxy fetch. */
export const MIRROR_EXPECTED_STATUSES: readonly number[] = [200];
/** Every seeded size, in the order of the generator. Iterations cycle through them. */
export const MIRROR_SIZES: readonly ArtifactSize[] = Object.keys(ARTIFACT_SIZES) as ArtifactSize[];

/** The path that Ingot answers for a seeded artifact of the mirror. */
export function mirrorPath(size: ArtifactSize): string {
  return `/${MIRROR_REPOSITORY}/seeded/${size}.bin`;
}

/** The URL of the same artifact on the upstream mock, which the proxy fetches. */
export function mirrorUpstreamUrl(size: ArtifactSize): string {
  return `${MIRROR_UPSTREAM_BASE_URL}/seeded/${size}.bin`;
}

/** The size of an iteration. A fixed cycle keeps two runs of the same length identical. */
export function mirrorSizeFor(iteration: number): ArtifactSize {
  const size = MIRROR_SIZES[iteration % MIRROR_SIZES.length];
  if (size === undefined) {
    throw new Error('the mirror has no seeded size');
  }
  return size;
}
