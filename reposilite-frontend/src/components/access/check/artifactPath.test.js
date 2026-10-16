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
import { artifactNodeFromPath } from './artifactPath'

/*
 * The check tool can take an artifact path instead of a node. The path is the artifact directory,
 * groupId directories then the artifactId, and it becomes the node the server decides.
 */

describe('artifactNodeFromPath', () => {
  it('names the groupId from the directories above the artifactId and the artifactId from the last one', () => {
    expect(artifactNodeFromPath({ repository: '', path: 'net/onelitefeather/mycelium-bom', action: 'read' }))
      .toEqual({ node: 'ingot.artifact.net.onelitefeather:mycelium-bom.read', problem: '' })
  })

  it('qualifies the node with the repository when one is given', () => {
    expect(artifactNodeFromPath({ repository: 'releases', path: 'net/onelitefeather/mycelium-bom', action: 'deploy' }))
      .toEqual({ node: 'ingot.repository.releases.artifact.net.onelitefeather:mycelium-bom.deploy', problem: '' })
  })

  it('ignores the slashes at the start and the end', () => {
    expect(artifactNodeFromPath({ repository: '', path: '/net/example/lib/', action: 'delete' }).node)
      .toBe('ingot.artifact.net.example:lib.delete')
  })

  it('asks for the artifact directory when the path has one segment only', () => {
    expect(artifactNodeFromPath({ repository: '', path: 'mycelium-bom', action: 'read' }))
      .toEqual({ node: '', problem: 'Enter the artifact directory, for example net/onelitefeather/mycelium-bom' })
  })

  it('asks for a path when it is empty', () => {
    expect(artifactNodeFromPath({ repository: '', path: '  ', action: 'read' }).problem)
      .toBe('Enter the artifact directory, for example net/onelitefeather/mycelium-bom')
  })

  it('refuses a parent segment', () => {
    expect(artifactNodeFromPath({ repository: '', path: 'net/../lib', action: 'read' }).problem)
      .toBe("Use the names of the directories, without '.' or '..' segments")
  })

  it('refuses a directory name the groupId rules do not allow', () => {
    expect(artifactNodeFromPath({ repository: '', path: 'net/one*/lib', action: 'read' }).problem)
      .toBe("Use '*' only as the whole groupId")
  })

  it('refuses a repository name that is not a name', () => {
    expect(artifactNodeFromPath({ repository: 'rel ases', path: 'net/lib', action: 'read' }).problem)
      .toBe("Use letters, digits, '_' and '-' in a repository name")
  })

  it('refuses an action that is not one of read, deploy and delete', () => {
    expect(artifactNodeFromPath({ repository: '', path: 'net/lib', action: 'manage' }).problem)
      .toBe("The action 'manage' is not an artifact action, choose read, deploy or delete")
  })
})
