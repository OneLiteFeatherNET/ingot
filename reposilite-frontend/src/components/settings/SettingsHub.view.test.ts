import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import SettingsHub from './SettingsHub.vue'
import type { SettingsGlanceRow } from './settingsHub'

const shell = { render: () => null }

const routes: RouteRecordRaw[] = [
  { name: 'settings', path: '/settings/:domain?', component: shell },
  { name: 'settings-repositories', path: '/settings/repositories', component: shell }
]

// Each test builds its own counts and router, so no test can change what another one reads.
const counts = (): SettingsGlanceRow[] => [
  { key: 'page:repositories', label: 'Repositories', count: 7, to: { name: 'settings-repositories' } },
  { key: 'settings:cleanup', label: 'Cleanup policies', count: 0, to: { name: 'settings', params: { domain: 'cleanup' } } }
]

// The application is served under /repo/, so every link of the page must carry that base.
const BASE = '/repo/'

// Assigned by mountHub before every test that reads it; afterEach unmounts it.
let wrapper: VueWrapper<InstanceType<typeof SettingsHub>>

afterEach(() => {
  wrapper?.unmount()
})

const mountHub = async (props: Partial<{ glance: SettingsGlanceRow[] }> = {}) => {
  const router = createRouter({ history: createMemoryHistory(BASE), routes })
  await router.push('/settings')
  await router.isReady()
  wrapper = mount(SettingsHub, {
    props: { glance: counts(), ...props },
    global: { plugins: [router] },
    attachTo: document.body
  })
  await flushPromises()
  return wrapper
}

const cardsOf = () => wrapper.findAll('[data-test="glance-card"]')

describe('SettingsHub anchor', () => {
  it('carries the settings-hub anchor on its root element', async () => {
    await mountHub()

    expect(wrapper.attributes('data-ingot-component'), 'the root is the anchor').toBe('settings-hub')
  })
})

describe('SettingsHub At a glance', () => {
  it('draws each collection as a stat card with its label and count', async () => {
    await mountHub()

    const labels = cardsOf().map(card => card.get('.md-label-large').text())
    const values = cardsOf().map(card => card.get('p.md-headline-medium').text())
    expect(labels, 'the labels, under the labels of the tree').toEqual(['Repositories', 'Cleanup policies'])
    expect(values, 'the counts').toEqual(['7', '0'])
  })

  it('draws a zero as 0 without a warning colour', async () => {
    await mountHub()

    const zero = cardsOf()[1].get('p.md-headline-medium')
    expect(zero.text()).toBe('0')
    expect(zero.classes(), 'no error or warning colour on a zero').not.toContain('text-error')
  })

  it('makes each whole card a link to its page, named by its label and count', async () => {
    await mountHub()

    const links = wrapper.findAll('a')
    expect(links.map(link => link.attributes('aria-label')), 'one accessible name per card').toEqual(['Repositories, 7', 'Cleanup policies, 0'])
    expect(links.map(link => link.attributes('href')), 'the links carry the base of the application').toEqual([
      '/repo/settings/repositories',
      '/repo/settings/cleanup'
    ])
  })

  it('draws the At a glance heading in the title medium size of the hub sections', async () => {
    await mountHub()

    const heading = wrapper.get('h2')
    expect(heading.text()).toBe('At a glance')
    expect(heading.classes(), 'the same size as Needs attention and Cluster').toContain('md-title-medium')
  })
})

describe('SettingsHub has no link to a Dashboard page', () => {
  it('draws no link to /dashboard, because the instance status is on Overview', async () => {
    await mountHub()

    expect(wrapper.findAll('a').filter(link => link.attributes('href') === '/dashboard'), 'no link to the old page').toEqual([])
    expect(wrapper.text()).not.toContain('Instance status and resources are on the')
  })
})

describe('SettingsHub content', () => {
  it('draws no card of a page beyond the counts, and no copy of the Administration tree', async () => {
    await mountHub()

    expect(wrapper.find('[data-test="settings-card"]').exists(), 'no card of a page').toBe(false)
    expect(wrapper.findAll('a').map(link => link.attributes('aria-label')), 'the only links are the counts').toEqual(['Repositories, 7', 'Cleanup policies, 0'])
  })

  it('does not repeat the removed line that sent sign in and security to Access', async () => {
    await mountHub()

    expect(wrapper.text()).not.toContain('Sign-in and security live in')
  })

  it('sets the glance grid columns on the expanded and large window classes', async () => {
    await mountHub()

    const grid = cardsOf()[0].element.parentElement!
    expect(grid.classList.contains('expanded:grid-cols-3'), 'three columns from 840 pixels').toBe(true)
    expect(grid.classList.contains('large:grid-cols-6'), 'six columns from 1240 pixels').toBe(true)
    expect([...grid.classList].filter(name => /^(sm|md|lg|xl|2xl):/.test(name)), 'no default breakpoint prefix').toEqual([])
  })
})
