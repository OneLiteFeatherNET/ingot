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
import { mount } from '@vue/test-utils'
import MdFieldset from '../md/MdFieldset.vue'
import SearchFilters from './SearchFilters.vue'
import { DEFAULT_QUERY } from './searchQuery'

const facets = { repository: [], extension: [], classifier: [], origin: [], snapshot: [] }

describe('SearchFilters', () => {
  it('titles each group of filters with the legend of a shared fieldset', () => {
    const wrapper = mount(SearchFilters, { props: { query: { ...DEFAULT_QUERY }, facets } })

    const legends = wrapper.findAllComponents(MdFieldset).map(group => group.find('legend').text())

    expect(legends, 'the chip groups, coordinates, date and size are shared fieldsets').toEqual([
      'Repository', 'File type', 'Classifier', 'Origin', 'Release or snapshot', 'Coordinates', 'Last modified', 'File size'
    ])
    expect(wrapper.findAll('fieldset'), 'no hand-written fieldset is left').toHaveLength(legends.length)
  })
})
