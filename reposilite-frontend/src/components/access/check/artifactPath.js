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
 * The check tool's artifact path: the artifact directory (its groupId directories, then the artifactId)
 * becomes the node the server decides, qualified by the repository when one is given. The rules of the
 * groupId and the artifactId are the ones of the templates. Pure.
 */

import { artifactIdProblem, groupIdProblem } from '../nodes/artifactNodes'
import { ARTIFACT_ACTIONS } from '../nodes/nodeGrammar'

const REPOSITORY_NAME = /^[A-Za-z0-9_-]+$/

/**
 * The node an artifact path names, as { node, problem }. node is empty while there is a problem. The
 * groupId is the directories above the last one, joined with '.', and the artifactId is the last one.
 */
const artifactNodeFromPath = ({ repository = '', path = '', action = 'read' }) => {
  const repo = String(repository ?? '').trim()
  if (repo !== '' && !REPOSITORY_NAME.test(repo)) {
    return { node: '', problem: "Use letters, digits, '_' and '-' in a repository name" }
  }
  if (!ARTIFACT_ACTIONS.includes(action)) {
    return { node: '', problem: `The action '${action}' is not an artifact action, choose read, deploy or delete` }
  }
  const segments = String(path ?? '').split('/').filter(part => part !== '')
  if (segments.length < 2) {
    return { node: '', problem: 'Enter the artifact directory, for example net/onelitefeather/mycelium-bom' }
  }
  if (segments.some(part => part === '.' || part === '..')) {
    return { node: '', problem: "Use the names of the directories, without '.' or '..' segments" }
  }
  const artifactId = segments[segments.length - 1]
  const groupId = segments.slice(0, -1).join('.')
  const problem = groupIdProblem(groupId) || artifactIdProblem(artifactId, groupId)
  if (problem !== '') return { node: '', problem }
  const prefix = repo === '' ? 'ingot.artifact.' : `ingot.repository.${repo}.artifact.`
  return { node: `${prefix}${groupId}:${artifactId}.${action}`, problem: '' }
}

export { artifactNodeFromPath }
