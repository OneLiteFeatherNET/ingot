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
// The dashboard scenario: a user signs in through the dashboard API and then reads the list of repositories.

import { check } from 'k6';
import http from 'k6/http';
import { statusIs } from '../lib/checks.ts';
import { DASHBOARD_EXPECTED_STATUSES, LOGIN_PATH, REPOSITORIES_PATH, loginBody } from './remaining-plan.ts';
import { thresholdFor } from '../lib/thresholds.ts';
import { profileOptions } from '../profiles/options.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

/** The executor of the profile the runner chose, with the p95 limit of this scenario. */
export const options = profileOptions(__ENV, 'dashboard', thresholdFor('dashboard').p95Ms);

export function dashboardIteration(): void {
  const login = http.post(
    `${BASE_URL}${LOGIN_PATH}`,
    loginBody(__ENV.LOADTEST_USER ?? '', __ENV.LOADTEST_PASSWORD ?? ''),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { scenario: 'dashboard', kind: 'login' },
      responseCallback: http.expectedStatuses(...DASHBOARD_EXPECTED_STATUSES),
    },
  );
  check(login, { 'login answered 200': statusIs(200) });

  // The session cookie of the login is kept by k6 for this virtual user.
  const repositories = http.get(`${BASE_URL}${REPOSITORIES_PATH}`, {
    tags: { scenario: 'dashboard', kind: 'repositories' },
    responseCallback: http.expectedStatuses(...DASHBOARD_EXPECTED_STATUSES),
  });
  check(repositories, { 'repositories answered 200': statusIs(200) });
}

export default function (): void {
  dashboardIteration();
}
