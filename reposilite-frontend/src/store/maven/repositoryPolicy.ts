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


/*
 * The write policy label the dashboard shows next to a repository. The server reports the policy it derives from
 * `redeployment` and `readOnly`; the settings page edits it through writePolicy.js, so the choice is made in one place.
 */

const WRITE_POLICY_LABELS = {
  ALLOW: 'Allow redeploy',
  ALLOW_ONCE: 'Allow once',
  READ_ONLY: 'Read-only'
}

/* The label of the write policy the server reports, or nothing for a proxy or a group, which take no uploads. */
export const writePolicyLabelOf = (repository = {}) => WRITE_POLICY_LABELS[repository.writePolicy] ?? ''
