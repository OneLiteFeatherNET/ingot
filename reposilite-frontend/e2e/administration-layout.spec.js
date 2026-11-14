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

import { test, expect } from './support/fixtures.js'
import { siteUrl } from './support/api.js'

// Repositories that make the repository list taller than the window, so that it scrolls.
const FILLER_REPOSITORIES = 8

// Headless Chromium hides the classic scrollbars by default, so a page that scrolls takes no room and the gutter is not
// seen. This file runs without that flag (a launch option, which only a top-level use can set), so the scrollbar of the
// list takes its room in the window, as it does on Windows and on Linux desktops without overlay scrollbars.
test.use({ launchOptions: { ignoreDefaultArgs: ['--hide-scrollbars'] } })

// The administration frame keeps still between its pages (rework-administration-layout, D1 and D3). The heading and the
// content start sit at the same place on the Overview, a page of a group, a list page and a detail page, whatever the page
// draws in its header. The frame keeps its width when a vertical scrollbar comes and goes.
test.describe('the administration layout between pages', () => {
  // The positions of the frame on the page on screen: the header, the heading, the content start and the side navigation.
  const measureFrame = (page) => page.evaluate(() => {
    const frame = document.querySelector('section[data-ingot-component="administration-frame"]')
    const top = (element) => (element ? element.getBoundingClientRect().top : Number.NaN)
    const tree = [...frame.querySelectorAll('nav[aria-label="Administration"]')].find(nav => nav.querySelector('a'))
    return {
      width: frame.getBoundingClientRect().width,
      headerHeight: frame.querySelector(':scope > header').getBoundingClientRect().height,
      headingTop: top(frame.querySelector('#administration-title')),
      contentTop: top(frame.querySelector('[data-ingot-component="side-nav-layout"]')),
      treeTop: top(tree),
    }
  })

  for (const width of [320, 600, 1440]) {
    test(`keeps the frame still between administration pages at ${width} pixels`, async ({ adminPage: page, seed }) => {
      const repository = await seed.repository('layout-still-detail')
      await page.setViewportSize({ width, height: 900 })
      const pages = [
        { name: 'Overview', path: '/settings', ready: () => page.getByRole('button', { name: 'Download as JSON', exact: true }) },
        { name: 'page of a group (Maintenance)', path: '/maintenance', ready: () => page.getByRole('heading', { name: 'Maintenance', level: 1 }) },
        { name: 'list page (Repositories)', path: '/settings/repositories', ready: () => page.getByRole('heading', { name: 'Repositories', level: 1 }) },
        { name: 'detail page (repository)', path: `/settings/repositories/${repository}`, ready: () => page.getByRole('heading', { name: repository, level: 1 }) },
      ]

      const measured = []
      for (const entry of pages) {
        await page.goto(siteUrl(entry.path))
        await expect(entry.ready(), `the ${entry.name} is loaded`).toBeVisible()
        measured.push({ name: entry.name, ...(await measureFrame(page)) })
      }

      const reference = measured[0]
      for (const entry of measured.slice(1)) {
        expect(entry.headingTop, `the heading top of the ${entry.name} against the Overview, in px`).toBeCloseTo(reference.headingTop, 0)
        // Below 600 pixels the Overview wraps its three actions onto a second row, which is accepted (see design D3). The
        // header height and the content start are measured where the actions share a row with the heading or stay on one line.
        if (width < 600) continue
        expect(entry.headerHeight, `the header height of the ${entry.name} against the Overview, in px`).toBeCloseTo(reference.headerHeight, 0)
        expect(entry.contentTop, `the content start of the ${entry.name} against the Overview, in px`).toBeCloseTo(reference.contentTop, 0)
      }
    })
  }

  test('keeps the side navigation level with the content start at 1440 pixels on every page that draws it', async ({ adminPage: page, seed }) => {
    const repository = await seed.repository('layout-still-tree')
    await page.setViewportSize({ width: 1440, height: 900 })
    for (const path of ['/settings', '/maintenance', '/settings/repositories', `/settings/repositories/${repository}`]) {
      await page.goto(siteUrl(path))
      await expect(page.locator('#administration-title'), `the heading of ${path}`).toBeVisible()
      const measure = await measureFrame(page)
      if (Number.isNaN(measure.treeTop)) continue
      expect(measure.treeTop, `the side navigation top of ${path} against its content start, in px`).toBeCloseTo(measure.contentTop, 0)
    }
  })

  test('keeps the frame the same width with and without a vertical scrollbar at 1440 pixels', async ({ adminPage: page, seed }) => {
    // Eight repositories make the list longer than the window at 600 pixels and shorter than it at 4000 pixels.
    for (let index = 0; index < FILLER_REPOSITORIES; index++) {
      await seed.repository(`gutter-filler-${index}`)
    }
    await page.setViewportSize({ width: 1440, height: 600 })
    await page.goto(siteUrl('/settings/repositories'))
    await expect(page.getByRole('button', { name: 'New repository', exact: true })).toBeVisible()
    const scrolls = await page.evaluate(() => document.documentElement.scrollHeight > document.documentElement.clientHeight)
    expect(scrolls, 'the list is taller than the window, so it scrolls').toBe(true)
    const scrollbarWidth = await page.evaluate(() => window.innerWidth - document.documentElement.clientWidth)
    expect(scrollbarWidth, 'the vertical scrollbar takes room in the window, in px').toBeGreaterThan(0)
    const withScrollbar = await measureFrame(page)

    await page.setViewportSize({ width: 1440, height: 4000 })
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight <= document.documentElement.clientHeight), 'the list fits the taller window').toBe(true)
    const withoutScrollbar = await measureFrame(page)

    expect(withScrollbar.width, 'the width of the frame with the scrollbar, in px').toBeCloseTo(withoutScrollbar.width, 0)
  })
})
