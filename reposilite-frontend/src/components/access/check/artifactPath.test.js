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

import { describe, it, expect } from 'vitest'
import { pathQueryOf } from './artifactPath'

/*
 * The check by path sends the question as it was typed: the repository (optional), the path inside
 * it and the action. The server reads the directories of the path, so nothing here derives a groupId.
 */

describe('pathQueryOf', () => {
  it('sends the repository, the full path and the action as typed', () => {
    expect(pathQueryOf({ repository: 'releases', path: 'net/onelitefeather/mycelium-bom/1.8.9/mycelium-bom-1.8.9.pom', action: 'deploy' }))
      .toEqual({ field: '', problem: '', query: { repository: 'releases', path: 'net/onelitefeather/mycelium-bom/1.8.9/mycelium-bom-1.8.9.pom', action: 'deploy' } })
  })

  it('does not derive a groupId or artifactId from the path', () => {
    expect(pathQueryOf({ repository: 'releases', path: 'net/lib', action: 'read' }).query)
      .toEqual({ repository: 'releases', path: 'net/lib', action: 'read' })
  })

  it('asks for the repository when it is empty', () => {
    expect(pathQueryOf({ repository: '  ', path: 'net/lib', action: 'read' }))
      .toEqual({ field: 'repository', problem: 'Enter the repository the path is in', query: null })
  })

  it('removes the spaces around the path but keeps its slashes', () => {
    expect(pathQueryOf({ repository: 'releases', path: '  /net/lib/  ', action: 'read' }).query.path).toBe('/net/lib/')
  })

  it('asks for a path when it is empty', () => {
    expect(pathQueryOf({ repository: 'releases', path: ' ', action: 'read' }))
      .toEqual({ field: 'path', problem: 'Enter the path of the file or directory inside the repository', query: null })
  })

  it('refuses a repository name that is not a name', () => {
    expect(pathQueryOf({ repository: 'rel ases', path: 'net/lib', action: 'read' }))
      .toEqual({ field: 'repository', problem: "Use letters, digits, '_' and '-' in a repository name", query: null })
  })

  it('refuses an action that is not read, deploy or delete', () => {
    expect(pathQueryOf({ repository: 'releases', path: 'net/lib', action: 'manage' }))
      .toEqual({ field: 'action', problem: "The action 'manage' is not an artifact action, choose read, deploy or delete", query: null })
  })
})
