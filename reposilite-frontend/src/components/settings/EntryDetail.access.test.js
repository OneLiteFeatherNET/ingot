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

import { describe, it, expect, vi, afterEach } from 'vitest'
import { markRaw, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { createAjv } from '@jsonforms/core'
import { defaultStyles, vanillaRenderers } from '@dzikoysk/vue-vanilla'
import { createMemoryHistory, createRouter } from 'vue-router'
import EntryDetail from './EntryDetail.vue'

const holder = vi.hoisted(() => ({ session: null }))

vi.mock('../../store/session', () => ({ useSession: () => holder.session }))

const collection = {
  key: 'repositories',
  title: 'Repositories',
  noun: 'repository',
  itemSchema: { type: 'object', properties: { id: { type: 'string', title: 'Id' } } }
}

let wrapper

const mountDetail = async (props = {}) => {
  const repositoryAccess = vi.fn(async () => ({ data: { repository: 'releases', visibility: 'PUBLIC', entries: [] } }))
  holder.session = { isManager: ref(true), client: ref({ identity: { repositoryAccess } }) }
  wrapper = mount(EntryDetail, {
    props: {
      collection,
      id: 'releases',
      entry: { id: 'releases' },
      renderers: markRaw([...vanillaRenderers]),
      validator: markRaw(createAjv({ useDefaults: true })),
      ...props
    },
    global: {
      provide: { styles: defaultStyles },
      plugins: [createRouter({ history: createMemoryHistory(), routes: [] })]
    }
  })
  await flushPromises()
  return repositoryAccess
}

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('EntryDetail access section', () => {
  it('shows who has access below the form for a repository', async () => {
    const repositoryAccess = await mountDetail({ showAccess: true })

    expect(wrapper.find('[data-test="repository-access"]').exists()).toBe(true)
    expect(repositoryAccess).toHaveBeenCalledWith('releases')
  })

  it('reports the same changes with the section as without it, so the form state is untouched', async () => {
    await mountDetail()
    const without = wrapper.emitted('change')?.length ?? 0
    wrapper.unmount()

    await mountDetail({ showAccess: true })

    expect(wrapper.emitted('change')?.length ?? 0).toBe(without)
  })

  it('has no section for entries that are not repositories', async () => {
    const repositoryAccess = await mountDetail()

    expect(wrapper.find('[data-test="repository-access"]').exists()).toBe(false)
    expect(repositoryAccess).not.toHaveBeenCalled()
  })
})
