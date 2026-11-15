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
 * The write policy of a hosted repository. It is not a stored key of its own: it is read from `redeployment`
 * and `readOnly`, and a choice writes only the key it changes. Read only keeps `redeployment`, so choosing
 * Allow once or Allow again afterwards restores the earlier state. Nothing here touches Vue or the network.
 */

export const WRITE_CHOICES = [
  { value: 'allow', label: 'Allow' },
  { value: 'allowOnce', label: 'Allow once' },
  { value: 'readOnly', label: 'Read only' }
]

/* The choice a hosted entry is in. A missing redeployment is off, as the server reads it. */
export const writePolicyOf = (entry = {}) => {
  if (entry.readOnly === true) return 'readOnly'
  return entry.redeployment === true ? 'allow' : 'allowOnce'
}

/*
 * The entry with the choice applied. The same entry comes back when the choice is already the stored one, so a save
 * does not rewrite a repository the visitor only looked at. The given entry is never changed.
 */
export const writePolicyChange = (entry, choice) => {
  if (writePolicyOf(entry) === choice) return entry
  const next = { ...entry }
  if (choice === 'readOnly') {
    next.readOnly = true
    return next
  }
  delete next.readOnly
  next.redeployment = choice === 'allow'
  return next
}
