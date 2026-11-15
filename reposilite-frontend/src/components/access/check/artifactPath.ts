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
 * The check by path: the repository the path is in (required, the server decides one repository), the path
 * inside it as typed, and the action, read, deploy or delete. The server decides the path, so the groupId and
 * the artifactId are never derived here. Pure.
 */

import { ARTIFACT_ACTIONS } from '../nodes/nodeGrammar'

const REPOSITORY_NAME = /^[A-Za-z0-9_-]+$/

/**
 * The query of a path check, as { field, problem, query }. A problem names the field it belongs to (repository,
 * path or action); the query is null while there is a problem. The path keeps its slashes and loses the spaces at
 * its ends.
 */
const pathQueryOf = ({ repository = '', path = '', action = 'read' }) => {
  const repo = String(repository ?? '').trim()
  const typedPath = String(path ?? '').trim()
  if (repo === '') return { field: 'repository', problem: 'Enter the repository the path is in', query: null }
  if (!REPOSITORY_NAME.test(repo)) {
    return { field: 'repository', problem: "Use letters, digits, '_' and '-' in a repository name", query: null }
  }
  if (typedPath === '') return { field: 'path', problem: 'Enter the path of the file or directory inside the repository', query: null }
  if (!ARTIFACT_ACTIONS.includes(action)) {
    return { field: 'action', problem: `The action '${action}' is not an artifact action, choose read, deploy or delete`, query: null }
  }
  return { field: '', problem: '', query: { repository: repo, path: typedPath, action } }
}

export { pathQueryOf }
