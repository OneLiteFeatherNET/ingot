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

import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('axios', () => ({ default: { get: vi.fn() } }))
vi.mock('./client', () => ({ createURL: (path) => path, handleRequest: (config) => config }))
vi.mock('../navigation/registry', () => ({ setContributions: vi.fn() }))

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')

const HASH_A = '3f9c1a2b7d4e6f80'
const HASH_B = '0a1b2c3d4e5f6071'

const pathOf = (plugin, hash) => `/api/frontend/plugin-styles/${plugin}/${hash}.css`
const entry = (plugin, hash = HASH_A) => ({ plugin, path: pathOf(plugin, hash), sha256: `${hash}${'0'.repeat(48)}` })

/** The head of a dashboard document before any plugin stylesheet: its own stylesheet link and a style element. */
const dashboardHead = () => {
  document.head.innerHTML = ''
  const own = document.createElement('link')
  own.rel = 'stylesheet'
  own.href = '/assets/index-own.css'
  const style = document.createElement('style')
  style.textContent = '.own { color: red; }'
  document.head.append(own, style)
}

const pluginLinks = () => [...document.head.querySelectorAll('link')].filter((link) => link.hasAttribute('data-ingot-plugin-style'))

describe('applyPluginStyles', () => {
  let applyPluginStyles

  beforeEach(async () => {
    // A fresh module per test: the module keeps the links it added, so no test sees the links of another.
    vi.resetModules()
    ;({ applyPluginStyles } = await import('./pluginStyles'))
    dashboardHead()
  })

  it('appends one stylesheet link per entry after the dashboard stylesheet', () => {
    // when: the manifest lists one plugin stylesheet
    applyPluginStyles([entry('example')])

    // then: the link is the last element of the head, after the dashboard's own link and style element
    const last = document.head.lastElementChild
    expect(last.tagName, 'last element of the head').toBe('LINK')
    expect(last.getAttribute('rel'), 'rel').toBe('stylesheet')
    expect(last.getAttribute('href'), 'href from the manifest').toBe(pathOf('example', HASH_A))
    expect(last.getAttribute('data-ingot-plugin-style'), 'plugin attribute').toBe('example')
  })

  it('removes the link of a plugin that the manifest no longer lists', () => {
    // given: a link for the plugin example
    applyPluginStyles([entry('example')])

    // when: a later manifest lists no stylesheet
    applyPluginStyles([])

    // then: the link is gone and the dashboard's own stylesheet is still there
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
    expect(document.head.querySelector('link[href="/assets/index-own.css"]'), 'own stylesheet').not.toBeNull()
  })

  it('replaces the link when the hash changes and leaves exactly one link', () => {
    // given: a link for the plugin example under its first hash
    applyPluginStyles([entry('example', HASH_A)])

    // when: the manifest names the same plugin under a new hash
    applyPluginStyles([entry('example', HASH_B)])

    // then: one link remains, and it points to the new hash
    const links = pluginLinks()
    expect(links, 'plugin links').toHaveLength(1)
    expect(links[0].getAttribute('href'), 'href').toBe(pathOf('example', HASH_B))
  })

  it('keeps the link as it is when the manifest is unchanged', () => {
    // given: a link for the plugin example
    applyPluginStyles([entry('example')])
    const before = pluginLinks()[0]

    // when: the same manifest is applied again
    applyPluginStyles([entry('example')])

    // then: the same element is kept and no second link is added
    expect(pluginLinks(), 'plugin links').toHaveLength(1)
    expect(pluginLinks()[0], 'kept link').toBe(before)
  })

  it('adds no link for a path outside the served pattern', () => {
    // when: the manifest carries paths that do not name a stylesheet of the server
    applyPluginStyles([
      { plugin: 'example', path: '/api/frontend/plugin-ui', sha256: '' },
      { plugin: 'example', path: 'https://example.org/x.css', sha256: '' },
      { plugin: 'example', path: '/api/frontend/plugin-styles/example/../../x.css', sha256: '' },
      { plugin: 'example', path: '/api/frontend/plugin-styles/example/3F9C1A2B7D4E6F80.css', sha256: '' },
    ])

    // then: no link is added
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
  })

  it('adds nothing for an empty list', () => {
    // when: the manifest lists no stylesheet
    applyPluginStyles([])

    // then: the head keeps its two elements
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
    expect(document.head.children, 'head children').toHaveLength(2)
  })

  it('adds nothing when the manifest carries no stylesheet list at all', () => {
    // when: an older server answers without the field
    applyPluginStyles(undefined)

    // then: nothing is added
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
  })

  it('removes a link whose stylesheet fails to load and keeps the dashboard styles', () => {
    // given: a link for the plugin example
    applyPluginStyles([entry('example')])

    // when: the browser reports that the stylesheet did not load
    pluginLinks()[0].dispatchEvent(new Event('error'))

    // then: the link is removed, and the dashboard's own stylesheet is untouched
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
    expect(document.head.querySelector('link[href="/assets/index-own.css"]'), 'own stylesheet').not.toBeNull()
  })

  it('declares the ingot-plugins layer after the Tailwind layers and the imports in the dashboard stylesheet', () => {
    // when: the dashboard stylesheet is read as source
    const source = readFileSync(resolve(frontendRoot, 'src', 'style.css'), 'utf8')
    const declaration = source.indexOf('@layer theme, base, components, utilities, ingot-plugins;')
    const tailwind = source.indexOf('@import "tailwindcss";')
    const lastImport = source.lastIndexOf('@import ')

    // then: the declaration exists, and it sits after the Tailwind import and after every other import
    expect(declaration, 'layer declaration').toBeGreaterThan(-1)
    expect(declaration, 'position after the tailwind import').toBeGreaterThan(tailwind)
    expect(declaration, 'position after the last import').toBeGreaterThan(lastImport)
  })
})

describe('the plugin manifest of the dashboard', () => {
  beforeEach(() => {
    vi.resetModules()
    dashboardHead()
  })

  it('adds the stylesheet links of the manifest once it is loaded', async () => {
    // given: a manifest that lists the stylesheet of the plugin example
    const axios = (await import('axios')).default
    axios.get.mockResolvedValueOnce({ data: { version: 1, pages: [], sections: [], enabledPlugins: ['example'], stylesheets: [entry('example')] } })
    const { loadPluginUi } = await import('./pluginUi')

    // when: the manifest is loaded
    await loadPluginUi()

    // then: the link of the sheet is in the head
    const links = pluginLinks()
    expect(links, 'plugin links').toHaveLength(1)
    expect(links[0].getAttribute('href'), 'href').toBe(pathOf('example', HASH_A))
  })

  it('removes the stylesheet links when the visitor signs out', async () => {
    // given: a loaded manifest with a stylesheet
    const axios = (await import('axios')).default
    axios.get.mockResolvedValueOnce({ data: { version: 1, pages: [], sections: [], enabledPlugins: ['example'], stylesheets: [entry('example')] } })
    const { loadPluginUi, clearPluginUi } = await import('./pluginUi')
    await loadPluginUi()

    // when: the session ends
    clearPluginUi()

    // then: the link is gone
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
  })

  it('adds no link when the manifest cannot be loaded', async () => {
    // given: a manifest request that is refused
    const axios = (await import('axios')).default
    axios.get.mockRejectedValueOnce(new Error('401'))
    const { loadPluginUi } = await import('./pluginUi')

    // when: the manifest is loaded
    await loadPluginUi()

    // then: no plugin stylesheet is added
    expect(pluginLinks(), 'plugin links').toHaveLength(0)
  })
})
