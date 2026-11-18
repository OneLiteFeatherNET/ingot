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
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { mount } from '@vue/test-utils'
import MdSegmentedButton from './MdSegmentedButton.vue'
import MdSegmentedOption from './MdSegmentedOption.vue'
import LegacySegmentedControl from '../tokens/SegmentedControl.vue'
import LegacySegmentedOption from '../tokens/SegmentedOption.vue'

// Reads the token file of the repository. No server and no network: the file is part of the source tree.
const SHAPE_TOKENS = resolve(import.meta.dirname, '../../styles/md3/shape-elevation-motion.css')

describe('MdSegmentedButton corner', () => {
  it('rounds the track with the segmented corner utility', () => {
    const wrapper = mount(MdSegmentedButton, { props: { label: 'Check by' } })

    expect(wrapper.classes(), 'the track takes its radius from the shape token').toContain('rounded-corner-segmented')
    expect(wrapper.classes(), 'the track keeps no arbitrary 20px value').not.toContain('rounded-[20px]')
  })

  it('defines the segmented corner as a 20px shape token', () => {
    const tokens = readFileSync(SHAPE_TOKENS, 'utf8')

    expect(tokens, 'the corner step is 20px in the md3 shape tokens').toContain('--md-sys-shape-corner-segmented: 20px;')
    expect(tokens, 'the corner step is exposed as the rounded utility').toContain('--radius-corner-segmented: var(--md-sys-shape-corner-segmented);')
  })
})

describe('MdSegmentedButton import paths', () => {
  it('keeps the old control path as the same component', () => {
    expect(LegacySegmentedControl, 'tokens/SegmentedControl.vue re-exports the md component').toBe(MdSegmentedButton)
  })

  it('keeps the old option path as the same component', () => {
    expect(LegacySegmentedOption, 'tokens/SegmentedOption.vue re-exports the md component').toBe(MdSegmentedOption)
  })
})

describe('MdSegmentedButton anchors', () => {
  it('keeps the segmented-control anchor on the track', () => {
    const wrapper = mount(MdSegmentedButton, { props: { label: 'Check by' } })

    expect(wrapper.attributes('data-ingot-component')).toBe('segmented-control')
  })

  it('keeps the segmented-option anchor on the segment', () => {
    const wrapper = mount(MdSegmentedOption)

    expect(wrapper.find('button').attributes('data-ingot-component'), 'the segment button keeps its anchor').toBe('segmented-option')
  })
})
