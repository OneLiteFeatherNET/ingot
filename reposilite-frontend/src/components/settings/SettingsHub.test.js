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
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import SettingsHub from './SettingsHub.vue'

const shell = { render: () => null }

const routes = [
  { name: 'settings', path: '/settings/:domain?', component: shell },
  { name: 'settings-repositories', path: '/settings/repositories', component: shell },
  { name: 'dashboard', path: '/dashboard', component: shell }
]

// Each test builds its own counts and router, so no test can change what another one reads.
const counts = () => [
  { key: 'page:repositories', label: 'Repositories', count: 7 },
  { key: 'settings:cleanup', label: 'Cleanup policies', count: 0 }
]

let wrapper

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

const mountHub = async (props = {}) => {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/settings')
  await router.isReady()
  wrapper = mount(SettingsHub, {
    props: { glance: counts(), dashboard: true, ...props },
    global: { plugins: [router] },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

describe('SettingsHub anchor', () => {
  it('carries the settings-hub anchor on its root element', async () => {
    await mountHub()

    expect(wrapper.element.getAttribute('data-ingot-component'), 'the root is the anchor').toBe('settings-hub')
  })
})

describe('SettingsHub At a glance', () => {
  it('draws each collection with its entry count, under the label of its tree entry', async () => {
    await mountHub()

    const terms = wrapper.findAll('dt').map(term => term.text())
    const values = wrapper.findAll('dd').map(value => value.text())
    expect(terms, 'the labels').toEqual(['Repositories', 'Cleanup policies'])
    expect(values, 'the counts, rendered together').toEqual(['7', '0'])
  })

  it('draws a zero as 0 without a warning colour', async () => {
    await mountHub()

    const zero = wrapper.findAll('dd').find(value => value.text() === '0')
    expect(zero, 'the zero count is drawn').toBeDefined()
    expect(zero.classes(), 'no error or warning colour on a zero').not.toContain('text-error')
  })

  it('draws the At a glance heading above the counts', async () => {
    await mountHub()

    expect(wrapper.get('h2').text()).toBe('At a glance')
  })
})

describe('SettingsHub dashboard link', () => {
  it('links to the Dashboard, which shows the instance status', async () => {
    await mountHub({ dashboard: true })

    const link = wrapper.get('[data-test="settings-hub-dashboard"]')
    expect(link.attributes('href')).toBe('/dashboard')
    expect(wrapper.text()).toContain('Instance status and resources are on the')
  })

  it('draws no dashboard link to a visitor without the dashboard node', async () => {
    await mountHub({ dashboard: false })

    expect(wrapper.find('[data-test="settings-hub-dashboard"]').exists()).toBe(false)
  })
})

describe('SettingsHub content', () => {
  it('draws no card grid and no copy of the Administration tree', async () => {
    await mountHub()

    expect(wrapper.find('[data-test="settings-card"]').exists(), 'no card').toBe(false)
    const links = wrapper.findAll('a').map(link => link.attributes('href'))
    expect(links, 'the only link is the dashboard link').toEqual(['/dashboard'])
  })

  it('does not repeat the removed line that sent sign in and security to Access', async () => {
    await mountHub()

    expect(wrapper.text()).not.toContain('Sign-in and security live in')
  })
})
