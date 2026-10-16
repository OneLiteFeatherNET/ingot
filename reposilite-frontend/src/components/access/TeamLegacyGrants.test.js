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
import { mount } from '@vue/test-utils'
import TeamLegacyGrants from './TeamLegacyGrants.vue'

const GRANTS = () => [
  { repository: null, pathPrefix: null, role: 'READ' },
  { repository: 'releases', pathPrefix: '/com/example/', role: 'DEPLOY' }
]

describe('TeamLegacyGrants', () => {
  let wrapper

  const mountGrants = (grants = GRANTS()) => {
    wrapper = mount(TeamLegacyGrants, { props: { grants } })
  }

  const rows = () => wrapper.findAll('[data-test="legacy-team-grant"]')

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('starts collapsed so the grants do not crowd the page', () => {
    mountGrants()

    expect(wrapper.find('details').attributes('open')).toBeUndefined()
  })

  it('names the section Legacy grants with the number of grants', () => {
    mountGrants()

    expect(wrapper.find('summary').text()).toBe('Legacy grants (2)')
  })

  it('names a grant without a repository Every repository and without a prefix Whole repository', () => {
    mountGrants()

    expect(rows()[0].find('[data-test="legacy-team-grant-repository"]').text()).toBe('Every repository')
    expect(rows()[0].find('[data-test="legacy-team-grant-prefix"]').text()).toBe('Whole repository')
  })

  it('shows the repository and the path prefix of a narrow grant without outer slashes', () => {
    mountGrants()

    expect(rows()[1].find('[data-test="legacy-team-grant-repository"]').text()).toBe('releases')
    expect(rows()[1].find('[data-test="legacy-team-grant-prefix"]').text()).toBe('com/example')
  })

  it('shows the role as a chip with its explanation', () => {
    mountGrants()

    expect(rows()[1].find('[data-test="legacy-team-grant-role"]').text()).toBe('Deploy')
    expect(rows()[1].text()).toContain('Read, and deploy new artifacts.')
  })

  it('is read only: it holds no input, no button and no editor', () => {
    mountGrants()

    expect(wrapper.findAll('input, button, textarea, select')).toHaveLength(0)
  })

  it('shows nothing for a team without grants', () => {
    mountGrants([])

    expect(wrapper.find('[data-test="legacy-team-grants"]').exists()).toBe(false)
  })
})
