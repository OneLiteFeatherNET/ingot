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

// @vitest-environment node

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/*
 * A page container that already renders the shared gutter is written as MdPage. The views keep
 * the centring they had (`mx-auto`, now the `centred` prop), so the page does not move. The test
 * reads the view sources, so it mounts nothing and depends on no clock.
 */
const pageViews = [
  '../header/DefaultHeader.vue',
  '../header/HeaderHero.vue'
]

const readSource = (file: string) => readFileSync(fileURLToPath(new URL(file, import.meta.url)), 'utf8')

describe('page containers render through MdPage', () => {
  it.each(pageViews)('%s imports MdPage from the md components', (file) => {
    expect(readSource(file), 'the view must import MdPage').toMatch(/import MdPage from '\.\.\/md\/MdPage\.vue'/)
  })

  it.each(pageViews)('%s renders its page root as MdPage', (file) => {
    expect(readSource(file), 'the page root must be an MdPage element').toMatch(/<MdPage\b/)
  })

  it.each(pageViews)('%s keeps the centring of the container it replaced', (file) => {
    expect(readSource(file), 'MdPage must be centred, as the mx-auto container was').toMatch(/<MdPage\b[^>]*\bcentred\b/)
  })

  it.each(pageViews)('%s no longer writes the container class itself', (file) => {
    expect(readSource(file), 'the gutter belongs to MdPage, not to the view').not.toMatch(/class="[^"]*(?<![-\w])container(?![-\w])/)
  })
})

/*
 * The views below wrote `container mx-auto` themselves, so they kept the width steps of the
 * container and drifted from the full width pages. They render MdPage now and take the full
 * width after the side navigation, so their left edge matches the other pages.
 */
const fullWidthViews = [
  '../util/NotFoundView.vue',
  '../util/ViewLoadError.vue',
  '../util/ViewLoading.vue',
  '../footer/SiteFooter.vue',
  '../browser/FileBrowserView.vue',
  '../../pages/IndexPage.vue'
]

describe('full width views render through MdPage', () => {
  it.each(fullWidthViews)('%s imports MdPage from the md components', (file) => {
    expect(readSource(file), 'the view must import MdPage').toMatch(/import MdPage from '(?:\.\.\/)+(?:components\/)?md\/MdPage\.vue'/)
  })

  it.each(fullWidthViews)('%s renders MdPage without centring or a width of its own', (file) => {
    const pages = [...readSource(file).matchAll(/<MdPage\b[^>]*>/g)].map(match => match[0])

    expect(pages.length, 'the view must render an MdPage element').toBeGreaterThan(0)
    for (const page of pages) {
      expect(page, 'a full width page is not centred').not.toMatch(/\bcentred\b|\bmx-auto\b/)
      expect(page, 'the page container takes no max-w-* class').not.toMatch(/\bmax-w-/)
    }
  })

  it.each(fullWidthViews)('%s no longer writes the container class itself', (file) => {
    expect(readSource(file), 'the gutter belongs to MdPage, not to the view').not.toMatch(/(?<![:\w-])class="[^"]*(?<![-\w])container(?![-\w])/)
  })
})
