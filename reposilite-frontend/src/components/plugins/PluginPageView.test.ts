import { describe, it, expect, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { setContributions } from '../../navigation/registry'
import { createMemoryHistory, createRouter } from 'vue-router'
import PluginPageView from './PluginPageView.vue'

// The blocks of a page link through the router, so each mount gets a fresh one.
const mountPage = async (): Promise<VueWrapper> => {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { render: () => null } }] })
  await router.push('/')
  await router.isReady()
  wrapper = mount(PluginPageView, { props: { plugin: 'acme-mirror', pageId: 'mirrors' }, global: { plugins: [router] } }) as VueWrapper
  return wrapper
}

// The manifest is built per test, and the registry is cleared after each one, so no test reads another's manifest.
const manifest = (motion?: string) => ({
  version: 1,
  pages: [{
    plugin: 'acme-mirror',
    id: 'mirrors',
    title: 'Mirror credentials',
    description: 'Rotate the upstream credentials of mirrored repositories.',
    group: 'Security',
    area: 'users',
    blocks: [],
    ...(motion === undefined ? {} : { motion }),
  }],
  sections: [],
  enabledPlugins: ['acme-mirror'],
})

// The mounted page is unmounted before the registry is cleared, so no clear re-renders a page of this test.
let wrapper: VueWrapper | undefined
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

  it('takes the fade-through transition for a page without a motion field', async () => {
    setContributions(manifest())

    const wrapper = await mountPage()

    expect(wrapper.find('transition-stub').attributes('name'), 'the route transition is the default').toBe('motion-fade-through')
  })

  it('takes the fade-through transition for an unknown motion value', async () => {
    setContributions(manifest('sparkle'))

    const wrapper = await mountPage()

    expect(wrapper.find('transition-stub').attributes('name'), 'an unknown value falls back to the default').toBe('motion-fade-through')
  })

  it('draws no transition for a page that opts out with motion none', async () => {
    setContributions(manifest('none'))

    const wrapper = await mountPage()

    expect(wrapper.find('transition-stub').exists(), 'the opt-out is an instant swap').toBe(false)
    expect(wrapper.text(), 'the page is still drawn').toContain('Rotate the upstream credentials of mirrored repositories.')
  })
})
