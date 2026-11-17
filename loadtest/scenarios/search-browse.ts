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
// The search-browse scenario: a client runs a search and lists a directory of the fixture.

import { check } from 'k6';
import http from 'k6/http';
import { statusIs } from '../lib/checks.ts';
import { RESOLVE_FIXTURE_COORDINATE, RESOLVE_REPOSITORY } from './resolve-plan.ts';
import { seedResolveFixture } from './resolve.ts';
import { BROWSE_EXPECTED_STATUSES, directoryPath, searchPath } from './remaining-plan.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

export function searchBrowseIteration(): void {
  const query = `${RESOLVE_FIXTURE_COORDINATE.groupId}:${RESOLVE_FIXTURE_COORDINATE.artifactId}`;
  const search = http.get(`${BASE_URL}${searchPath(query)}`, {
    tags: { scenario: 'search-browse', kind: 'search' },
    responseCallback: http.expectedStatuses(...BROWSE_EXPECTED_STATUSES),
  });
  check(search, { 'search answered 200': statusIs(200) });

  const browse = http.get(`${BASE_URL}${directoryPath(RESOLVE_REPOSITORY, RESOLVE_FIXTURE_COORDINATE)}`, {
    tags: { scenario: 'search-browse', kind: 'browse' },
    responseCallback: http.expectedStatuses(...BROWSE_EXPECTED_STATUSES),
  });
  check(browse, { 'browse answered 200': statusIs(200) });
}

export function setup(): void {
  seedResolveFixture();
}

export default function (): void {
  searchBrowseIteration();
}
