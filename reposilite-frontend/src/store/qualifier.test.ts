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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { reactive, ref } from 'vue'

// The route is replaced per test: its qualifier is the only part the store reads.
const { route } = vi.hoisted(() => ({ route: { params: { qualifier: undefined } } }))

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: () => {} })
}))

vi.mock('./session', () => ({
  useSession: () => ({ details: ref(undefined) })
}))

// Each test loads a fresh store, so the path and the reload counter of one test cannot reach
// the next.
const loadStore = async (path) => {
  vi.resetModules()
  route.params = reactive({ qualifier: path })
  return (await import('./qualifier')).default
}

describe('qualifier reloads', () => {
  beforeEach(() => {
    route.params = { qualifier: undefined }
  })

  it('reloads the listing once when a path is first browsed', async () => {
    const useQualifier = await loadStore('releases/net/example')

    const { qualifier } = useQualifier()

    expect(qualifier.path).toBe('releases/net/example')
    expect(qualifier.watchable, 'the first browse of a path is one reload').toBe(1)
  })

  it('does not reload again when another view reads the same path', async () => {
    const useQualifier = await loadStore('releases/net/example')
    const first = useQualifier()
    const reloads = first.qualifier.watchable

    useQualifier()

    expect(first.qualifier.watchable, 'a second view on the same path must not request the listing again')
      .toBe(reloads)
  })

  it('reloads when the browsed path changes', async () => {
    const useQualifier = await loadStore('releases/net/example')
    const { qualifier } = useQualifier()
    const reloads = qualifier.watchable

    route.params.qualifier = 'releases/net/other'
    await Promise.resolve()

    expect(qualifier.path).toBe('releases/net/other')
    expect(qualifier.watchable).toBe(reloads + 1)
  })
})
