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
import { mount, type VueWrapper } from '@vue/test-utils'
import TeamLegacyGrants from './TeamLegacyGrants.vue'
import MdCard from '../md/MdCard.vue'
import MdDisclosure from '../md/MdDisclosure.vue'
import type { TeamGrant } from './accessTeams'

const GRANTS = (): TeamGrant[] => [
  { repository: null, pathPrefix: null, role: 'READ' },
  { repository: 'releases', pathPrefix: '/com/example/', role: 'DEPLOY' }
]

describe('TeamLegacyGrants', () => {
  let wrapper: VueWrapper | undefined

  const mountGrants = (grants: TeamGrant[] = GRANTS()) => {
    wrapper = mount(TeamLegacyGrants, { props: { grants } })
  }

  const rows = () => wrapper!.findAll('[data-test="legacy-team-grant"]')

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('starts collapsed so the grants do not crowd the page', () => {
    mountGrants()

    expect(wrapper!.find('details').attributes('open')).toBeUndefined()
  })

  it('names the section Legacy grants with the number of grants', () => {
    mountGrants()

    expect(wrapper!.find('summary').text()).toBe('Legacy grants (2)')
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

    expect(wrapper!.findAll('input, button, textarea, select')).toHaveLength(0)
  })

  it('draws the grants on the shared low card', () => {
    mountGrants()

    const cards = wrapper!.findAllComponents(MdCard)

    expect(cards, 'the grants sit on one card primitive, not on a hand-built container').toHaveLength(1)
    expect(cards[0].props('variant'), 'the tinted panel is the low card').toBe('low')
    expect(cards[0].find('[data-test="legacy-team-grants"]').exists(), 'the collapsible grants are inside the card').toBe(true)
  })

  it('folds the grants in the shared disclosure inside the card', () => {
    mountGrants()

    const card = wrapper!.findAllComponents(MdCard)[0]
    const disclosures = card.findAllComponents(MdDisclosure)

    expect(disclosures, 'the grants fold in one disclosure primitive, not in a hand-built details element').toHaveLength(1)
    expect(disclosures[0].props('open'), 'the disclosure starts collapsed').toBe(false)
    expect(disclosures[0].find('summary').text(), 'the disclosure summary names the grants').toBe('Legacy grants (2)')
  })

  it('keeps the grants element around the disclosure', () => {
    mountGrants()

    const grants = wrapper!.find('[data-test="legacy-team-grants"]')

    expect(grants.exists(), 'the grants element is still rendered').toBe(true)
    expect(grants.findAllComponents(MdDisclosure), 'the grants element holds the shared disclosure').toHaveLength(1)
  })

  it('labels the grant list with the disclosure summary', () => {
    mountGrants()

    const labelledBy = wrapper!.find('ul').attributes('aria-labelledby')

    expect(labelledBy, 'the grant list names its label').toBeTruthy()
    expect(wrapper!.find(`summary [id="${labelledBy}"]`).text(), 'the label is the summary text').toBe('Legacy grants (2)')
  })

  it('shows nothing for a team without grants', () => {
    mountGrants([])

    expect(wrapper!.find('[data-test="legacy-team-grants"]').exists()).toBe(false)
  })
})
