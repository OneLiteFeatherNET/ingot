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

import { describe, it, expect, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import MdFieldset from '../md/MdFieldset.vue'
import SearchFilters from './SearchFilters.vue'
import { DEFAULT_QUERY, parseSearchQuery } from './searchQuery'
import type { Facets } from './searchResult'
import { expectPrimitiveControls } from '../../helpers/primitiveControls'

const emptyFacets = { repository: [], extension: [], classifier: [], origin: [], snapshot: [] }

const facets: Facets = {
  repository: [{ value: 'releases', count: 4 }],
  extension: [{ value: 'jar', count: 3 }],
  classifier: [{ value: 'sources', count: 1 }],
  origin: [{ value: 'HOSTED', count: 4 }],
  snapshot: [{ value: 'false', count: 4 }]
}

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('SearchFilters', () => {
  it('titles each group of filters with the legend of a shared fieldset', () => {
    const wrapper = mount(SearchFilters, { props: { query: { ...DEFAULT_QUERY }, facets: emptyFacets } })

    const legends = wrapper.findAllComponents(MdFieldset).map(group => group.find('legend').text())

    expect(legends, 'the chip groups, coordinates, date and size are shared fieldsets').toEqual([
      'Repository', 'File type', 'Classifier', 'Origin', 'Release or snapshot', 'Coordinates', 'Last modified', 'File size'
    ])
    expect(wrapper.findAll('fieldset'), 'no hand-written fieldset is left').toHaveLength(legends.length)
  })
})

describe('SearchFilters controls', () => {
  it('builds its text fields from primitives', () => {
    wrapper = mount(SearchFilters, { props: { query: parseSearchQuery(), facets } }) as VueWrapper

    expectPrimitiveControls(wrapper)
  })
})
