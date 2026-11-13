import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setContributions } from '../../navigation/registry'
import PluginPageView from './PluginPageView.vue'

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

afterEach(() => setContributions(null))

describe('PluginPageView', () => {
  it('draws the description of the page, and the plugin that provides it, under the heading of the frame', () => {
    setContributions(manifest())

    const wrapper = mount(PluginPageView, { props: { plugin: 'acme-mirror', pageId: 'mirrors' } })

    expect(wrapper.text()).toContain('Rotate the upstream credentials of mirrored repositories.')
    expect(wrapper.text(), 'the plugin is named under the description').toContain('Provided by the acme-mirror plugin')
  })

  it('draws no heading of its own, because the Administration frame names the page', () => {
    setContributions(manifest())

    const wrapper = mount(PluginPageView, { props: { plugin: 'acme-mirror', pageId: 'mirrors' } })

    expect(wrapper.findAll('h1, h2, h3'), 'the page has no heading of its own').toEqual([])
  })
})
