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
 * The bodies of the calls that carry the connection. Every call sends the connection again, because the API has
 * no connection resource: the literal password comes from the password holder, a secret reference is the text
 * the operator typed. A body is null when a literal password is needed and the holder has none (after a reload),
 * so the caller asks for it first and no call is made without it.
 */

const connectionBodyOf = (connection, passwords) => {
  if (!connection) return null
  if (connection.passwordMode === 'reference') {
    return { url: connection.url, user: connection.user, password: connection.reference }
  }
  const password = passwords.recall()
  return password === null ? null : { url: connection.url, user: connection.user, password }
}

// The repositories a job works on: the included rows, each with its target name when the operator renamed it.
const jobBodyOf = ({ kind, connection, passwords, mapping }) => {
  const base = connectionBodyOf(connection, passwords)
  if (!base) return null
  const included = mapping.repositories.filter(repository => repository.included)
  const targets = Object.fromEntries(included
    .map(repository => [repository.nexusName, String(repository.targetName ?? '').trim()])
    .filter(([nexusName, target]) => target !== '' && target !== nexusName))
  return {
    ...base,
    kind,
    repositories: included.map(repository => repository.nexusName),
    targets,
    conflictPolicy: mapping.conflictPolicy,
  }
}

export { connectionBodyOf, jobBodyOf }
