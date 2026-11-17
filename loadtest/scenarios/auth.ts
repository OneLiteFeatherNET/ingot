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
// The auth scenario: a client reads a file of a private repository, once with Basic credentials and once with a Bearer
// token. Each request carries its scheme as a tag, so the credential cache can be read per scheme.

import { check } from 'k6';
import http from 'k6/http';
import { statusIs } from '../lib/checks.ts';
import { RESOLVE_FIXTURE_COORDINATE, artifactBase } from './resolve-plan.ts';
import { seedResolveFixture } from './resolve.ts';
import { AUTH_EXPECTED_STATUSES, PRIVATE_REPOSITORY, authRequests } from './remaining-plan.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

export function authIteration(): void {
  const path = `${artifactBase(PRIVATE_REPOSITORY, RESOLVE_FIXTURE_COORDINATE)}.pom`;
  const credentials = {
    user: __ENV.LOADTEST_USER ?? '',
    password: __ENV.LOADTEST_PASSWORD ?? '',
    token: __ENV.LOADTEST_TOKEN ?? '',
  };
  for (const request of authRequests(path, credentials)) {
    const response = http.get(`${BASE_URL}${request.path}`, {
      headers: request.headers,
      tags: { scenario: 'auth', scheme: request.scheme },
      responseCallback: http.expectedStatuses(...AUTH_EXPECTED_STATUSES),
    });
    check(response, { [`auth ${request.scheme} answered 200`]: statusIs(200) });
  }
}

export function setup(): void {
  seedResolveFixture(PRIVATE_REPOSITORY);
}

export default function (): void {
  authIteration();
}
