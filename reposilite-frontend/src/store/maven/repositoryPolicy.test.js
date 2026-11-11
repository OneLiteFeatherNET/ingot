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
import { writePolicyLabelOf } from './repositoryPolicy'

describe('the write policy label of a repository in the dashboard', () => {
  it('names the policy the server reports', () => {
    expect(writePolicyLabelOf({ writePolicy: 'ALLOW' })).toBe('Allow redeploy')
    expect(writePolicyLabelOf({ writePolicy: 'ALLOW_ONCE' })).toBe('Allow once')
    expect(writePolicyLabelOf({ writePolicy: 'READ_ONLY' })).toBe('Read-only')
  })

  it('shows nothing for a declared proxy or group, which report no write policy', () => {
    expect(writePolicyLabelOf({ type: 'PROXY', writePolicy: null })).toBe('')
    expect(writePolicyLabelOf({ type: 'GROUP' })).toBe('')
  })
})
