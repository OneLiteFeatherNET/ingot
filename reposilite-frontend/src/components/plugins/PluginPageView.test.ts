import { describe, it, expect, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { setContributions, setReaderPages } from '../../navigation/registry'
import { createMemoryHistory, createRouter } from 'vue-router'
import PluginPageView from './PluginPageView.vue'

// The blocks of a page and the not found view link through the router, so each mount gets a fresh one.
const mountPage = async (props: Record<string, unknown> = {}): Promise<VueWrapper> => {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'overview', component: { render: () => null } }, { path: '/:pathMatch(.*)*', component: { render: () => null } }] })
  await router.push('/')
  await router.isReady()
  wrapper = mount(PluginPageView, { props: { plugin: 'acme-mirror', pageId: 'mirrors', ...props }, global: { plugins: [router] } }) as VueWrapper
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
  setReaderPages(null)
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

describe('PluginPageView for readers', () => {
  const readerManifest = {
    version: 1,
    pages: [{
      plugin: 'acme-mirror',
      id: 'mirrors',
      title: 'Mirrored projects',
      description: 'The upstream projects this instance mirrors.',
      blocks: [{ type: 'form', source: '/api/plugins/acme-mirror/settings', target: '/api/plugins/acme-mirror/settings', method: 'PUT' }],
    }],
  }

  it('draws the reader page with its title as the heading of the page', async () => {
    setReaderPages(readerManifest)

    const wrapper = await mountPage({ reader: true })

    expect(wrapper.find('h1').text(), 'a reader page has no frame, so it names itself').toBe('Mirrored projects')
    expect(wrapper.text()).toContain('The upstream projects this instance mirrors.')
    expect(wrapper.text(), 'the plugin is named under the description').toContain('Provided by the acme-mirror plugin')
  })

  it('reads the reader manifest only, not the admin page of the same id', async () => {
    setContributions(manifest())

    const wrapper = await mountPage({ reader: true })

    expect(wrapper.text(), 'an admin page is never shown at a reader address').not.toContain('Rotate the upstream credentials')
    expect(wrapper.find('#not-found-title').text(), 'the not found view is shown in its place').toBe('Page not found')
  })

  it('draws a writing block of a reader page as unsupported', async () => {
    setReaderPages(readerManifest)

    const wrapper = await mountPage({ reader: true })

    expect(wrapper.text(), 'the form block is refused for readers').toContain('Unsupported block: form')
  })
})
