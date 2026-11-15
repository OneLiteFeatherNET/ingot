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
 * The coordinates an artifact template asks for: the groupId (required, a dot separated name or the
 * whole '*') and the artifactId (optional, one name). The rules are the server's: no ':' in a name,
 * no '*' inside a name, no empty part. Each check returns the message for the field, or an empty
 * string. Pure.
 */

const NAME_PART = /^[A-Za-z0-9_-]+$/

/** The problem with the dot separated names of a groupId or artifactId, or an empty string. */
const namesProblem = (label, value) => {
  const parts = value.split('.')
  if (parts.some(part => part === '')) return `A ${label} cannot start, end or repeat a '.'`
  if (parts.some(part => !NAME_PART.test(part))) {
    return `Use letters, digits, '_' and '-' in ${label === 'groupId' ? 'a groupId' : 'an artifactId'}, with '.' between its parts`
  }
  return ''
}

/** The message for a groupId that cannot be used, or an empty string. */
const groupIdProblem = (groupId) => {
  const text = String(groupId ?? '')
  if (text === '') return 'Enter a groupId, for example net.onelitefeather'
  if (text === '*') return ''
  if (text.includes(':')) return "A groupId cannot contain ':', the artifactId goes into its own field"
  if (text.includes('*')) return "Use '*' only as the whole groupId"
  return namesProblem('groupId', text)
}

/** The message for an artifactId that cannot be used, or an empty string. An empty artifactId is fine. */
const artifactIdProblem = (artifactId, groupId) => {
  const text = String(artifactId ?? '')
  if (text === '') return ''
  if (text.includes(':')) return "An artifactId cannot contain ':'"
  if (text === '*') return 'Leave the artifactId empty to cover every artifact of the group'
  if (groupId === '*') return 'A groupId of * cannot have an artifactId, name the groupId'
  if (text.includes('*')) return "Use '*' only as the whole artifactId"
  return namesProblem('artifactId', text)
}

export { artifactIdProblem, groupIdProblem }
