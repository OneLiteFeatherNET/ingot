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

// The deploy scenario: an authenticated client uploads artifacts of 1 KB to 200 MB, each under a version of its own.

import { check } from 'k6';
import http from 'k6/http';
import { ARTIFACT_SIZES, type ArtifactSize, generateArtifact } from '../lib/artifacts.ts';
import { basicAuthHeader } from '../lib/auth.ts';
import { statusIs } from '../lib/checks.ts';
import { profileOptions } from '../profiles/options.ts';
import { DEPLOY_EXPECTED_STATUSES, DEPLOY_P95_MS, DEPLOY_SIZES, deployPath } from './deploy-plan.ts';

/** The executor of the profile the runner chose, with the p95 limit of this scenario. */
export const options = profileOptions(__ENV, 'deploy', DEPLOY_P95_MS);

/** The pass rule of every plateau step, run by the verdict scenarios the plateau profile adds. */
export { stepVerdict } from '../profiles/step-verdict-k6.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';
const REPOSITORY = 'snapshots';
const COORDINATE = { groupId: 'net.onelitefeather.loadtest', artifactId: 'deploy-fixture' } as const;

// The bytes of a size are generated the first time a VU needs them and kept for that VU. The 200 MB body is large, so
// it is never generated in setup() and never sent through the setup data.
const bodies = new Map<ArtifactSize, Uint8Array>();

function bodyOf(size: ArtifactSize): Uint8Array {
  let body = bodies.get(size);
  if (body === undefined) {
    body = generateArtifact(DEPLOY_SIZES.indexOf(size) + 1, ARTIFACT_SIZES[size]);
    bodies.set(size, body);
  }
  return body;
}

export function deployIteration(sequence: number, size: ArtifactSize): void {
  // generateArtifact returns an array that owns its whole buffer, so the buffer is exactly the body.
  const body = bodyOf(size).buffer as ArrayBuffer;
  const response = http.put(`${BASE_URL}${deployPath(REPOSITORY, COORDINATE, sequence)}`, body, {
    headers: basicAuthHeader(__ENV.LOADTEST_USER ?? '', __ENV.LOADTEST_PASSWORD ?? ''),
    tags: { scenario: 'deploy', size },
    responseCallback: http.expectedStatuses(...DEPLOY_EXPECTED_STATUSES),
  });
  check(response, { [`deploy ${size} answered 200`]: statusIs(200) });
}

export default function (): void {
  const size = DEPLOY_SIZES[__ITER % DEPLOY_SIZES.length] ?? '1KB';
  // The sequence is unique across VUs and iterations, so no two deploys of a run share a path.
  deployIteration(__VU * 1_000_000 + __ITER, size);
}
