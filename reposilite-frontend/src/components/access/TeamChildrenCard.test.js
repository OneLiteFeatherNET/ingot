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

// @vitest-environment jsdom

import { describe, it, expect, afterEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../router/routes'
import TeamChildrenCard from './TeamChildrenCard.vue'

/*
 * The card lists the child teams as links. It gets a fresh router for every test, so no history is
 * shared.
 */

const shell = { render: () => null }

describe('TeamChildrenCard', () => {
  let wrapper

  const mountCard = async (names) => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
    })
    await router.push('/access/teams')
    await router.isReady()
    wrapper = mount(TeamChildrenCard, { props: { names }, global: { plugins: [router] } })
    await flushPromises()
    return router
  }

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('lists each child team under the heading Child teams', async () => {
    await mountCard(['ops', 'release'])

    expect(wrapper.find('h4').text()).toBe('Child teams')
    expect(wrapper.findAll('[data-test="child-team"]').map(link => link.text())).toEqual(['ops', 'release'])
  })

  it('links each child team to its page', async () => {
    await mountCard(['ops', 'release'])

    expect(wrapper.findAll('[data-test="child-team"]').map(link => link.attributes('href')))
      .toEqual(['/access/teams/ops', '/access/teams/release'])
  })

  it('opens the child team when its link is pressed', async () => {
    const router = await mountCard(['ops'])

    await wrapper.find('[data-test="child-team"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/access/teams/ops')
  })

  it('says so when no team inherits from the team', async () => {
    await mountCard([])

    expect(wrapper.find('[data-test="children-empty"]').text()).toBe('No team inherits from this team.')
    expect(wrapper.find('[data-test="child-team"]').exists()).toBe(false)
  })
})
