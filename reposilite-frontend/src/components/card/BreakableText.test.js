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
import BreakableText from './BreakableText.vue'

const url = 'http://localhost:8080/<repository>'

// Mounts the text in its own host element, so that only its nodes are read back.
const mountText = (text) => {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const wrapper = mount(BreakableText, { props: { text }, attachTo: host })
  return { wrapper, host }
}

// The text as drawn, with a "|" where each line break opportunity is.
const marked = (node) => {
  let out = ''
  for (const child of node.childNodes) {
    if (child.nodeName === 'WBR') out += '|'
    else if (child.nodeType === Node.TEXT_NODE) out += child.data
    else out += marked(child)
  }
  return out
}

describe('BreakableText', () => {
  it('keeps the text exactly as given, since the line breaks hold no text', () => {
    const { wrapper } = mountText(url)

    expect(wrapper.text()).toBe(url)
  })

  it('offers a line break after the slash of the path, and before the placeholder', () => {
    const { host } = mountText(url)

    expect(marked(host)).toBe('http://|localhost:8080/|<repository>')
  })

  it('does not offer a break inside the double slash of the scheme', () => {
    const { host } = mountText('http://example.org/releases')

    expect(marked(host)).toBe('http://|example.org/|releases')
  })
})
