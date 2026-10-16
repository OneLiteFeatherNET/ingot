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
import RepositorySnippet from './RepositorySnippet.vue'

const data = {
  type: 'repository',
  repoId: 'releases-x',
  title: 'Ingot Repository',
  domain: 'http://localhost/releases'
}

const render = (lang) => mount(RepositorySnippet, { props: { configuration: { name: 'Maven', lang }, data } })

describe('RepositorySnippet Maven text', () => {
  it('copies the snippet exactly as written: one element per line, no blank line between them', () => {
    const text = render('xml').find('pre').element.textContent

    expect(text).toBe(
      '<repository>\n' +
      '  <id>releases-x</id>\n' +
      '  <name>Ingot Repository</name>\n' +
      '  <url>http://localhost/releases</url>\n' +
      '</repository>\n'
    )
  })

  it('lets the url wrap at the slash and before the placeholder, not inside a word', () => {
    const pre = render('xml').find('pre').element

    expect(pre.querySelectorAll('wbr').length).toBeGreaterThan(0)
    expect(pre.textContent).toContain('http://localhost/releases')
  })
})
