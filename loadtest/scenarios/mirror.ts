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

// The mirror scenario: a client reads a seeded artifact through a proxy repository whose upstream is the in-cluster mock.
// Bodies are discarded, so a 200 MB artifact is never held in memory per virtual user.

import { check } from 'k6';
import exec from 'k6/execution';
import http from 'k6/http';
import { statusIs } from '../lib/checks.ts';
import { mirrorPath, MIRROR_EXPECTED_STATUSES, mirrorSizeFor } from './mirror-plan.ts';
import { thresholdFor } from '../lib/thresholds.ts';
import { profileOptions } from '../profiles/options.ts';

const BASE_URL = __ENV.BASE_URL ?? 'http://localhost:8080';

/** The executor of the profile the runner chose, with the p95 limit of this scenario. */
export const options = profileOptions(__ENV, 'mirror', thresholdFor('mirror').p95Ms);

/** The pass rule of every plateau step, run by the verdict scenarios the plateau profile adds. */
export { stepVerdict } from '../profiles/step-verdict-k6.ts';

export default function (): void {
  const size = mirrorSizeFor(exec.scenario.iterationInInstance);
  const response = http.get(`${BASE_URL}${mirrorPath(size)}`, {
    tags: { scenario: 'mirror', size },
    responseType: 'none',
    responseCallback: http.expectedStatuses(...MIRROR_EXPECTED_STATUSES),
  });
  check(response, { 'mirror answered 200': statusIs(200) });
}
