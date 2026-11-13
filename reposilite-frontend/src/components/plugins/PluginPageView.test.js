import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setContributions } from '../../navigation/registry'
import { createMemoryHistory, createRouter } from 'vue-router'
import PluginPageView from './PluginPageView.vue'

// The blocks of a page link through the router, so each mount gets a fresh one.
const mountPage = async () => {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })
  await router.push('/')
  await router.isReady()
  wrapper = mount(PluginPageView, { props: { plugin: 'acme-mirror', pageId: 'mirrors' }, global: { plugins: [router] } })
  return wrapper
}

// The manifest is built per test, and the registry is cleared after each one, so no test reads another's manifest.
const manifest = () => ({
  version: 1,
  pages: [{
    plugin: 'acme-mirror',
    id: 'mirrors',
    title: 'Mirror credentials',
    description: 'Rotate the upstream credentials of mirrored repositories.',
    group: 'Security',
    area: 'users',
    blocks: [],
  }],
  sections: [],
  enabledPlugins: ['acme-mirror'],
})

// The mounted page is unmounted before the registry is cleared, so no clear re-renders a page of this test.
let wrapper
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  setContributions(null)
})

describe('PluginPageView', () => {
  it('draws the description of the page, and the plugin that provides it, under the heading of the frame', async () => {
    setContributions(manifest())

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Rotate the upstream credentials of mirrored repositories.')
    expect(wrapper.text(), 'the plugin is named under the description').toContain('Provided by the acme-mirror plugin')
  })

  it('draws no heading of its own, because the Administration frame names the page', async () => {
    setContributions(manifest())

    const wrapper = await mountPage()

    expect(wrapper.findAll('h1, h2, h3'), 'the page has no heading of its own').toEqual([])
  })
})
