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
import { mount } from '@vue/test-utils'
import { createMemoryHistory } from 'vue-router'
import { createAppRouter } from '../../router/create'
import NotFoundView from './NotFoundView.vue'

const mountNotFound = async () => {
  const router = createAppRouter({ history: createMemoryHistory(), component: { render: () => null } })
  await router.push('/releases/does/not/exist')
  return mount(NotFoundView, { global: { plugins: [router] } })
}

describe('NotFoundView', () => {
  it('says that the page does not exist', async () => {
    const wrapper = await mountNotFound()

    expect(wrapper.find('h2').text()).toBe('Page not found')
  })

  it('links back to overview through the router', async () => {
    const wrapper = await mountNotFound()
    const back = wrapper.findAll('a').find(link => link.text() === 'Back to overview')

    expect(back, 'a visitor needs a way out of a dead address').toBeDefined()
    expect(back.attributes('href')).toBe('/')
  })
})
