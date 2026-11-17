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
// The resolve-miss scenario: a client asks a group repository for a version that no member holds. The 404 is the
// expected answer, so it counts as a success of the check and not as an error.

import { check } from 'k6';
import http from 'k6/http';
import { statusIn } from '../lib/checks.ts';
import { RESOLVE_FIXTURE_COORDINATE, type Coordinate } from './resolve-plan.ts';
import { MISS_EXPECTED_STATUSES, missPath } from './remaining-plan.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

export function missIteration(coordinate: Coordinate): void {
  const response = http.get(`${BASE_URL}${missPath(coordinate)}`, {
    tags: { scenario: 'resolve-miss' },
    responseCallback: http.expectedStatuses(...MISS_EXPECTED_STATUSES),
  });
  check(response, { 'miss answered 200 or 404': statusIn(MISS_EXPECTED_STATUSES) });
}

export default function (): void {
  missIteration(RESOLVE_FIXTURE_COORDINATE);
}
