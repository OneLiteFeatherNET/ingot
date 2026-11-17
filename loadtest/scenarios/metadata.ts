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
// The metadata scenario: a client reads the maven-metadata.xml of an artifact, which lists its versions.

import { check } from 'k6';
import http from 'k6/http';
import { statusIs } from '../lib/checks.ts';
import { RESOLVE_REPOSITORY, type Coordinate } from './resolve-plan.ts';
import { seedResolveFixture } from './resolve.ts';
import { METADATA_EXPECTED_STATUSES, metadataPath } from './remaining-plan.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

export function metadataIteration(coordinate: Coordinate): void {
  const response = http.get(`${BASE_URL}${metadataPath(RESOLVE_REPOSITORY, coordinate)}`, {
    tags: { scenario: 'metadata' },
    responseCallback: http.expectedStatuses(...METADATA_EXPECTED_STATUSES),
  });
  check(response, { 'metadata answered 200': statusIs(200) });
}

export function setup(): Coordinate {
  return seedResolveFixture();
}

export default function (coordinate: Coordinate): void {
  metadataIteration(coordinate);
}
