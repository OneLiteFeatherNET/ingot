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
import { artifactIdProblem, groupIdProblem } from './artifactNodes'

/*
 * The coordinates a template asks for. They follow the server's grammar: a groupId is dot separated
 * names or the whole '*', an artifactId is optional and names one artifact, and neither may hold a ':'
 * or a '*' inside a name.
 */

describe('groupIdProblem', () => {
  it('asks for a groupId when it is empty', () => {
    expect(groupIdProblem('')).toBe('Enter a groupId, for example net.onelitefeather')
  })

  it.each(['net', 'net.onelitefeather', 'net.one-litefeather_2', '*'])('accepts %s', (groupId) => {
    expect(groupIdProblem(groupId)).toBe('')
  })

  it('refuses a colon, because the artifactId has its own field', () => {
    expect(groupIdProblem('net.onelitefeather:mycelium')).toBe("A groupId cannot contain ':', the artifactId goes into its own field")
  })

  it('refuses a wildcard inside the groupId', () => {
    expect(groupIdProblem('net.*')).toBe("Use '*' only as the whole groupId")
    expect(groupIdProblem('ne*t')).toBe("Use '*' only as the whole groupId")
  })

  it('refuses an empty part', () => {
    expect(groupIdProblem('net..onelitefeather')).toBe("A groupId cannot start, end or repeat a '.'")
    expect(groupIdProblem('.net')).toBe("A groupId cannot start, end or repeat a '.'")
  })

  it('refuses characters that are not allowed in a name', () => {
    expect(groupIdProblem('net/onelitefeather')).toBe("Use letters, digits, '_' and '-' in a groupId, with '.' between its parts")
  })
})

describe('artifactIdProblem', () => {
  it('accepts an empty artifactId, which covers every artifact of the group', () => {
    expect(artifactIdProblem('', 'net.onelitefeather')).toBe('')
  })

  it('accepts a name', () => {
    expect(artifactIdProblem('mycelium-bom', 'net.onelitefeather')).toBe('')
  })

  it('refuses a colon', () => {
    expect(artifactIdProblem('my:lib', 'net')).toBe("An artifactId cannot contain ':'")
  })

  it('tells to leave the artifactId empty instead of a lone star', () => {
    expect(artifactIdProblem('*', 'net')).toBe('Leave the artifactId empty to cover every artifact of the group')
  })

  it('refuses a wildcard inside the artifactId', () => {
    expect(artifactIdProblem('my*lib', 'net')).toBe("Use '*' only as the whole artifactId")
  })

  it('refuses an artifactId together with the groupId *', () => {
    expect(artifactIdProblem('mycelium', '*')).toBe("A groupId of * cannot have an artifactId, name the groupId")
  })

  it('refuses characters that are not allowed in a name', () => {
    expect(artifactIdProblem('my lib', 'net')).toBe("Use letters, digits, '_' and '-' in an artifactId, with '.' between its parts")
  })
})
