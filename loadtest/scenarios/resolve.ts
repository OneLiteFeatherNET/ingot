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

// The resolve scenario: a Maven or Gradle client reads one artifact from a release repository.

import { check } from 'k6';
import { sha1 } from 'k6/crypto';
import http from 'k6/http';
import { basicAuthHeader } from '../lib/auth.ts';
import { statusIn, statusIs } from '../lib/checks.ts';
import {
  RESOLVE_EXPECTED_STATUSES,
  RESOLVE_FIXTURE_COORDINATE,
  RESOLVE_REPOSITORY,
  type Coordinate,
  type ResolveClient,
  resolveFixture,
  resolveRequests,
} from './resolve-plan.ts';
import { thresholdFor } from '../lib/thresholds.ts';
import { profileOptions } from '../profiles/options.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

/** The executor of the profile the runner chose, with the p95 limit of this scenario. */
export const options = profileOptions(__ENV, 'resolve', thresholdFor('resolve').p95Ms);

const FIXTURE_SEED = 42;
// 200 for a new fixture. 409 means an earlier run with the same seed left identical bytes, and a release repository
// refuses a redeploy, so the fixture is already in place.
const FIXTURE_STATUSES = [200, 409];

/** Uploads the fixture once, before the iterations. The uploader is the load test user, given by the runner. */
export function seedResolveFixture(repository: string = RESOLVE_REPOSITORY): Coordinate {
  const auth = basicAuthHeader(__ENV.LOADTEST_USER ?? '', __ENV.LOADTEST_PASSWORD ?? '');
  const files = resolveFixture(
    RESOLVE_FIXTURE_COORDINATE,
    FIXTURE_SEED,
    (bytes) => sha1(bytes.buffer as ArrayBuffer, 'hex'),
    repository,
  );
  for (const file of files) {
    // The jar is a generated array that owns its buffer, so the buffer is exactly the body.
    const body = typeof file.body === 'string' ? file.body : (file.body.buffer as ArrayBuffer);
    const response = http.put(`${BASE_URL}${file.path}`, body, {
      headers: auth,
      responseCallback: http.expectedStatuses(...FIXTURE_STATUSES),
    });
    check(response, { [`fixture ${file.path} present`]: statusIn(FIXTURE_STATUSES) });
  }
  return RESOLVE_FIXTURE_COORDINATE;
}

/** One client reads its files for one artifact. Anonymous reads, as a client without a token does. */
export function resolveIteration(coordinate: Coordinate, client: ResolveClient): void {
  for (const request of resolveRequests(client, coordinate)) {
    const response = http.get(`${BASE_URL}${request.path}`, {
      tags: { scenario: 'resolve', kind: request.kind },
      responseCallback: http.expectedStatuses(...RESOLVE_EXPECTED_STATUSES),
    });
    check(response, { [`resolve ${request.kind} answered 200`]: statusIs(200) });
  }
}

export function setup(): Coordinate {
  return seedResolveFixture();
}

export default function (coordinate: Coordinate): void {
  resolveIteration(coordinate, __ITER % 2 === 0 ? 'maven' : 'gradle');
}
