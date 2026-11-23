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

import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { mount } from '@vue/test-utils'
import MdSegmentedButton from './MdSegmentedButton.vue'
import MdSegmentedOption from './MdSegmentedOption.vue'
import MdDataTable from './MdDataTable.vue'

// The Tailwind default breakpoints are 640px (sm), 768px (md), 1024px (lg), 1280px (xl) and 1536px (2xl). The catalogue
// names the MD3 window classes instead: medium from 600px, expanded from 840px, large from 1240px, xlarge from 1600px.
// A class token starts a utility, so an object key such as `sm: 'p-2'` (a space after the colon) is not matched.
const DEFAULT_BREAKPOINT = /(?<![\w-])(?:max-)?(?:sm|md|lg|xl|2xl):[\w[!-]/

describe('md catalogue window classes', () => {
  const files = readdirSync(import.meta.dirname).filter((name) => name.endsWith('.vue'))

  it.each(files)('%s uses no Tailwind default breakpoint prefix', (name) => {
    const source = readFileSync(`${import.meta.dirname}/${name}`, 'utf8')

    expect(source, `${name} names its widths with the MD3 window classes`).not.toMatch(DEFAULT_BREAKPOINT)
  })

  it('keeps the segmented track on one line from the medium window class', () => {
    const wrapper = mount(MdSegmentedButton, { props: { label: 'Check by' } })

    expect(wrapper.classes(), 'the track stops wrapping from 600px').toContain('medium:flex-nowrap')
    expect(wrapper.classes(), 'the former sm prefix is gone').not.toContain('sm:flex-nowrap')
  })

  it('lets a segmented option shrink from the medium window class', () => {
    const classes = mount(MdSegmentedOption).find('button').classes()

    expect(classes, 'the option drops its min-content width from 600px').toContain('medium:min-w-0')
    expect(classes, 'the former sm prefix is gone').not.toContain('sm:min-w-0')
  })

  it('shows a low-priority table column from the expanded window class', () => {
    const wrapper = mount(MdDataTable, {
      props: {
        caption: 'Repositories',
        columns: [{ key: 'name', label: 'Name' }, { key: 'size', label: 'Size', priority: 'low' as const }],
        rows: [{ name: 'a', size: 1 }],
      },
    })
    const header = wrapper.findAll('thead th')[1]

    expect(header.classes(), 'the low column is shown from 840px').toContain('expanded:table-cell')
    expect(header.classes(), 'the former md prefix is gone').not.toContain('md:table-cell')
  })
})
