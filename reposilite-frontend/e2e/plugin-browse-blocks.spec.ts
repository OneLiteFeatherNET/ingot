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

import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import type { components } from '../src/types/api'
import { siteUrl } from './support/api'
import { signInAsAdmin } from './support/session'

/*
 * The browse blocks of a plugin page in a real browser: a filter, the list it drives and a code block. The page is
 * added to the manifest the server answers, and the list source answers a fixed response, so the test needs no plugin
 * of its own. Everything else (sign in, routing, drawing) is the dashboard of the shared server of the run.
 */

const PLUGIN = 'e2e-blocks'
const PAGE_ID = 'browse'
const SOURCE = `/api/plugins/${PLUGIN}/items`

// A dependency line and an address without a break, so a code block that does not wrap would widen the page.
const CODE = [
  'repositories {',
  '    maven("https://repo.example.org/releases/net/onelitefeather/an-artifact-with-a-very-long-name-that-does-not-break")',
  '}',
  'dependencies {',
  '    implementation("net.onelitefeather.example:an-artifact-with-a-very-long-name-that-does-not-break:1.2.3-SNAPSHOT")',
  '}',
].join('\n')

const BLOCKS = [
  {
    type: 'filter',
    controls: [
      { type: 'select', key: 'loader', label: 'Loader', options: [{ value: 'paper', label: 'Paper' }, { value: 'velocity', label: 'Velocity' }] },
      { type: 'text', key: 'query', label: 'Name' },
    ],
  },
  { type: 'list', source: SOURCE },
  { type: 'code', text: CODE, label: 'Gradle (Kotlin)', language: 'kotlin' },
]

const PAGE = { plugin: PLUGIN, id: PAGE_ID, area: '*', title: 'Browse blocks', description: 'A filter, a list and a code block.', blocks: BLOCKS }

const LIST = {
  items: [
    {
      title: 'an-artifact-with-a-very-long-name-that-does-not-break-anywhere-in-its-own-title',
      subtitle: 'net.onelitefeather.example:an-artifact-with-a-very-long-name-that-does-not-break:1.2.3-SNAPSHOT',
      chips: ['paper', 'velocity', '1.21.4'],
      href: '/browse/releases/net/onelitefeather/example',
    },
    { title: 'second', subtitle: 'net.onelitefeather.example:second:1.0.0', chips: ['paper'] },
  ],
  next: null,
  facets: { loader: [{ value: 'paper', count: 2 }, { value: 'velocity', count: 1 }] },
}

// The manifest of the server with the page of this test added, so the real plugins of the run stay in it.
const addPage = async (page: Page) => {
  await page.route('**/api/frontend/plugin-ui', async (route) => {
    const response = await route.fetch()
    const manifest = await response.json() as components['schemas']['PluginUiManifest']
    await route.fulfill({ response, json: { ...manifest, pages: [...(manifest.pages ?? []), PAGE] } })
  })
  await page.route(`**${SOURCE}*`, route => route.fulfill({ json: LIST }))
}

const openPage = async (page: Page) => {
  await addPage(page)
  await signInAsAdmin(page)
  await page.goto(siteUrl(`/settings/plugins/${PLUGIN}/${PAGE_ID}`))
  await expect(page.getByRole('link', { name: LIST.items[0].title }), 'first list row').toBeVisible()
  await expect(page.getByText('Gradle (Kotlin)'), 'code label').toBeVisible()
}

const pluginPage = (page: Page) => page.locator('[data-ingot-component="plugin-page"]')
const codeBlock = (page: Page) => page.locator('[data-ingot-component="plugin-block"][data-ingot-block="code"]')

test.describe('plugin browse blocks', () => {
  test.use({ permissions: ['clipboard-read', 'clipboard-write'] })

  for (const width of [320, 600, 1440]) {
    test(`draws the filter, the list and the code block without a horizontal page scroll at ${width} px`, async ({ page }) => {
      // given: the viewport of the width under test
      await page.setViewportSize({ width, height: 900 })

      // when: the page with the three blocks is opened
      await openPage(page)

      // then: neither the plugin page, nor an element around it, nor a block inside it is wider than its own box, so
      // nothing scrolls sideways, whichever element of the layout would hold the scroll
      const overflowing = await pluginPage(page).evaluate((root) => {
        const around: Element[] = []
        for (let element: Element | null = root; element; element = element.parentElement) around.push(element)
        return [...around, ...root.querySelectorAll('*')]
          .filter(element => element.scrollWidth > element.clientWidth + 1 && element.clientWidth > 0)
          .map(element => `${element.tagName.toLowerCase()} (${element.scrollWidth} > ${element.clientWidth})`)
      })
      expect(overflowing, `elements wider than their box at ${width} px`).toEqual([])
      const box = await codeBlock(page).locator('pre').boundingBox()
      expect(box!.x, `left edge of the code box at ${width} px`).toBeGreaterThanOrEqual(0)
      expect(box!.x + box!.width, `right edge of the code box at ${width} px`).toBeLessThanOrEqual(width)
    })
  }

  test('announces "Copied" in the status region of the code block and writes the code text', async ({ page }) => {
    // given: the page is open, and the status region is in the page before the first click, empty
    await page.setViewportSize({ width: 320, height: 900 })
    await openPage(page)
    const status = codeBlock(page).getByRole('status')
    await expect(status, 'status region before the click').toHaveCount(1)
    await expect(status, 'status region before the click').toHaveText('')

    // when: the copy action is pressed
    await codeBlock(page).getByRole('button', { name: 'Copy', exact: true }).click()

    // then: the region announces the copy, and the clipboard holds the code text and nothing else
    await expect(status, 'status region after the click').toHaveText('Copied')
    expect(await page.evaluate(() => navigator.clipboard.readText()), 'clipboard text').toBe(CODE)
  })
})
