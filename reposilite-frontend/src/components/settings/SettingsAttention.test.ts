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

import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SettingsAttention from './SettingsAttention.vue'

const shell = { render: () => null }

const routes = [
  { name: 'settings', path: '/settings/:domain?', component: shell },
  { name: 'settings-repositories', path: '/settings/repositories', component: shell },
  { name: 'dashboard', path: '/dashboard', component: shell }
]

// Each test builds its own rows and router, so no test can change what another one reads.
const drafts = () => [
  { key: 'page:repositories', label: 'Repositories', to: { name: 'settings-repositories' } },
  { key: 'settings:javadoc', label: 'Javadoc', to: { name: 'settings', params: { domain: 'javadoc' } } }
]

const conflictOf = () => ({ kind: 'settings', name: 'javadoc', title: 'Javadoc', reviewed: false, server: null })

let wrapper: VueWrapper<InstanceType<typeof SettingsAttention>>

afterEach(() => {
  wrapper?.unmount()
})

type AttentionProps = Partial<{ rows: ReturnType<typeof drafts>, conflict: ReturnType<typeof conflictOf> | null }>

const mountAttention = async (props: AttentionProps = {}) => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/settings')
  await router.isReady()
  wrapper = mount(SettingsAttention, {
    props: { rows: [], conflict: null, ...props },
    global: { plugins: [router] },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

describe('SettingsAttention rows', () => {
  it('lists one row per domain with an unsaved draft, linking to that domain', async () => {
    await mountAttention({ rows: drafts() })

    const rows = wrapper.findAll('[data-test="attention-row"]')
    expect(rows.map(row => row.attributes('href')), 'each row links to its page').toEqual(['/settings/repositories', '/settings/javadoc'])
  })

  it('names each row "<label>, Unsaved changes" for a screen reader', async () => {
    await mountAttention({ rows: drafts() })

    expect(wrapper.findAll('[data-test="attention-row"]').map(row => row.attributes('aria-label')), 'the accessible names').toEqual([
      'Repositories, Unsaved changes',
      'Javadoc, Unsaved changes'
    ])
  })

  it('states the status in text, not by colour alone', async () => {
    await mountAttention({ rows: drafts() })

    const texts = wrapper.findAll('[data-test="attention-row"]').map(row => row.text())
    expect(texts.every(text => text.includes('Unsaved changes')), 'every row says Unsaved changes in text').toBe(true)
  })

  it('draws no row without a draft', async () => {
    await mountAttention({ rows: [] })

    expect(wrapper.find('[data-test="attention-row"]').exists()).toBe(false)
  })
})

describe('SettingsAttention review', () => {
  it('draws Review changes under the rows and emits review when it is pressed', async () => {
    await mountAttention({ rows: drafts() })

    const review = wrapper.findAll('button').find(button => button.text() === 'Review changes')
    expect(review, 'the review button is drawn').toBeDefined()
    await review?.trigger('click')

    expect(wrapper.emitted('review'), 'the parent opens the review of all drafts').toHaveLength(1)
  })

  it('draws no Review changes button without a draft', async () => {
    await mountAttention({ rows: [] })

    expect(wrapper.findAll('button').map(button => button.text())).not.toContain('Review changes')
  })
})

describe('SettingsAttention empty state', () => {
  it('reads "Nothing needs attention." with no draft and no conflict', async () => {
    await mountAttention({ rows: [], conflict: null })

    expect(wrapper.text()).toContain('Nothing needs attention.')
  })

  it('does not read "Nothing needs attention." while a row is drawn', async () => {
    await mountAttention({ rows: drafts() })

    expect(wrapper.text()).not.toContain('Nothing needs attention.')
  })
})

describe('SettingsAttention as cards', () => {
  it('draws the empty state as an outlined card with a check icon and the text', async () => {
    await mountAttention({ rows: [], conflict: null })

    const card = wrapper.get('[data-test="attention-empty"]')
    expect(card.attributes('data-variant'), 'an outlined card, the size of a stat card').toBe('outlined')
    expect(card.find('svg').exists(), 'a check icon').toBe(true)
    expect(card.text()).toContain('Nothing needs attention.')
    expect(wrapper.find('[data-test="attention-card"]').exists(), 'no tonal card while nothing needs attention').toBe(false)
  })

  it('draws the rows in a card on the error container surface', async () => {
    await mountAttention({ rows: drafts() })

    const card = wrapper.get('[data-test="attention-card"]')
    expect(card.classes(), 'the tonal surface').toContain('bg-error-container')
    expect(card.findAll('[data-test="attention-row"]')).toHaveLength(2)
    expect(wrapper.find('[data-test="attention-empty"]').exists(), 'no empty card beside the rows').toBe(false)
  })

  it('gives each row an icon, its text and its link to the page', async () => {
    await mountAttention({ rows: drafts() })

    const row = wrapper.findAll('[data-test="attention-row"]')[0]
    expect(row.find('svg').exists(), 'an icon, so the state is not colour alone').toBe(true)
    expect(row.text()).toContain('Repositories')
    expect(row.text()).toContain('Unsaved changes')
    expect(row.attributes('href')).toBe('/settings/repositories')
  })

  it('keeps the Review changes button inside the card', async () => {
    await mountAttention({ rows: drafts() })

    const card = wrapper.get('[data-test="attention-card"]')
    expect(card.findAll('button').map(button => button.text())).toContain('Review changes')
  })

  it('keeps a save conflict inside the card, with its review button', async () => {
    await mountAttention({ rows: [], conflict: conflictOf() })

    const card = wrapper.get('[data-test="attention-card"]')
    expect(card.find('[data-test="attention-conflict"]').exists(), 'the conflict row is in the card').toBe(true)
  })

  it('draws the heading in the title large size of the page sections', async () => {
    await mountAttention({ rows: [] })

    expect(wrapper.get('h2').classes()).toContain('md-title-large')
  })
})

describe('SettingsAttention conflict', () => {
  it('draws a save conflict as a row that names the domain and says it changed elsewhere', async () => {
    await mountAttention({ rows: [], conflict: conflictOf() })

    const row = wrapper.get('[data-test="attention-conflict"]')
    expect(row.text(), 'the conflict names the domain').toContain('Javadoc')
    expect(row.text(), 'and says it changed elsewhere').toContain('changed elsewhere')
    expect(wrapper.text(), 'a conflict is not the empty state').not.toContain('Nothing needs attention.')
  })

  it('emits review-conflict when the conflict row is opened for review', async () => {
    await mountAttention({ rows: [], conflict: conflictOf() })

    const review = wrapper.get('[data-test="attention-conflict"]').findAll('button').find(button => button.text() === 'Review conflict')
    expect(review, 'the conflict row has a review button').toBeDefined()
    await review?.trigger('click')

    expect(wrapper.emitted('review-conflict'), 'the parent reviews the stored settings').toHaveLength(1)
  })
})
