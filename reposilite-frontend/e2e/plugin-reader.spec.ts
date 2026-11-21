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
import { siteUrl } from './support/api'

/*
 * A plugin reader page in a real browser, opened by a visitor who is not signed in. The page is answered by the reader
 * manifest the browser receives, and its list source answers a fixed response, so the test needs no plugin of its own.
 * The server of the run has no reader page, so it does not claim /plugins; the document of that address is the
 * dashboard document, as it is on an instance with a reader page.
 */

const PLUGIN = 'e2e-reader'
const PAGE_ID = 'projects'
const PAGE_PATH = `/plugins/${PLUGIN}/${PAGE_ID}`
const SOURCE = `/api/plugins/${PLUGIN}/items`

const PAGE = {
  plugin: PLUGIN,
  id: PAGE_ID,
  title: 'Published projects',
  description: 'The projects this instance publishes, with the coordinates to depend on them.',
  blocks: [
    { type: 'list', source: SOURCE },
    { type: 'code', label: 'Gradle (Kotlin)', language: 'kotlin', text: 'implementation("net.onelitefeather.example:an-artifact-with-a-very-long-name-that-does-not-break:1.2.3-SNAPSHOT")' },
  ],
}

const LIST = {
  items: [
    {
      title: 'an-artifact-with-a-very-long-name-that-does-not-break-anywhere-in-its-own-title',
      subtitle: 'net.onelitefeather.example:an-artifact-with-a-very-long-name-that-does-not-break:1.2.3-SNAPSHOT',
      chips: ['paper', 'velocity'],
    },
  ],
  next: null,
}

// The reader manifest names the page of this test, its source answers the list, and the address answers the document.
const servePage = async (page: Page) => {
  await page.route('**/api/frontend/plugin-ui/readers', route => route.fulfill({ json: { version: 1, pages: [PAGE] } }))
  await page.route(`**${SOURCE}*`, route => route.fulfill({ json: LIST }))
  await page.route(siteUrl(PAGE_PATH), async route => route.fulfill({ response: await route.fetch({ url: siteUrl('/') }) }))
}

const railGroup = (page: Page, name: string) => page.getByRole('group', { name, exact: true })

test.describe('plugin reader page', () => {
  for (const width of [320, 600, 1440]) {
    test(`shows the reader page to an anonymous visitor without a horizontal scroll at ${width} px`, async ({ page }) => {
      // given: the viewport of the width under test and a reader page in the manifest
      await page.setViewportSize({ width, height: 900 })
      await servePage(page)

      // when: a visitor who is not signed in opens the reader address
      await page.goto(siteUrl(PAGE_PATH))

      // then: the page names itself, draws its blocks, asks for no sign in, and nothing on it scrolls sideways
      await expect(page.getByRole('heading', { level: 1, name: PAGE.title }), 'heading of the reader page').toBeVisible()
      await expect(page.getByText(LIST.items[0].title), 'first list row').toBeVisible()
      await expect(page.getByRole('heading', { name: /Sign in to open/ }), 'sign in prompt').toHaveCount(0)
      const overflowing = await page.locator('main').evaluate((root) => {
        const around: Element[] = []
        for (let element: Element | null = root; element; element = element.parentElement) around.push(element)
        return [...around, ...root.querySelectorAll('*')]
          .filter(element => element.scrollWidth > element.clientWidth + 1 && element.clientWidth > 0)
          .map(element => `${element.tagName.toLowerCase()} (${element.scrollWidth} > ${element.clientWidth})`)
      })
      expect(overflowing, `elements wider than their box at ${width} px`).toEqual([])
    })
  }

  test('lists the reader page under Plugin pages in the rail and opens it from there', async ({ page }) => {
    // given: a wide window and a reader page in the manifest
    await page.setViewportSize({ width: 1440, height: 900 })
    await servePage(page)
    await page.goto(siteUrl('/'))

    // when: the visitor chooses the page under Plugin pages
    await railGroup(page, 'Plugin pages').getByRole('link', { name: PAGE.title }).click()

    // then: the reader page is on screen at its address
    await expect(page, 'the reader address').toHaveURL(siteUrl(PAGE_PATH))
    await expect(page.getByRole('heading', { level: 1, name: PAGE.title }), 'heading of the reader page').toBeVisible()
  })

  test('draws no Plugin pages heading while there is no reader page', async ({ page }) => {
    // given: a wide window and the reader manifest of the server, which names no page
    await page.setViewportSize({ width: 1440, height: 900 })

    // when: the start page is opened
    await page.goto(siteUrl('/'))
    await expect(page.getByRole('link', { name: 'Browse', exact: true }).first(), 'the rail is drawn').toBeVisible()

    // then: the rail has no Plugin pages heading
    await expect(railGroup(page, 'Plugin pages'), 'Plugin pages heading').toHaveCount(0)
  })
})
