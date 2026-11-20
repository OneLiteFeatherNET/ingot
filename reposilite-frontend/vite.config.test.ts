// @vitest-environment node
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
import { fileURLToPath } from 'node:url'
import { stripHtmlComments } from './vite.config'

const indexHtml = readFileSync(fileURLToPath(new URL('./index.html', import.meta.url)), 'utf8')

const transform = (html) => stripHtmlComments().transformIndexHtml.handler(html)

const withoutComments = (html) => html.replace(/<!--[\s\S]*?-->/g, '')
const normalizeWhitespace = (html) => html.replace(/\s+/g, ' ').trim()
const placeholdersOf = (html) => html.match(/\{\{REPOSILITE\.[A-Z_.]+\}\}/g) ?? []
const tagsOf = (html) => html.match(/<[^>]+>/g) ?? []

describe('served index.html', () => {
  it('does not carry the license comment of the source file', () => {
    const served = transform(indexHtml)

    expect(served, 'the Apache license notice must not be sent to every visitor').not.toContain('Licensed under the Apache License')
    expect(served, 'the dzikoysk copyright line must not be sent to every visitor').not.toContain('Copyright (c) 2023 dzikoysk')
  })

  it('carries no HTML comment at all', () => {
    expect(transform(indexHtml)).not.toMatch(/<!--/)
  })

  it('keeps every {{REPOSILITE.*}} placeholder in its place and order', () => {
    expect(placeholdersOf(transform(indexHtml))).toEqual(placeholdersOf(indexHtml))
  })

  it('keeps every element outside the comments byte-identical', () => {
    expect(tagsOf(transform(indexHtml))).toEqual(tagsOf(withoutComments(indexHtml)))
  })

  it('keeps the text of the document apart from the removed comments', () => {
    expect(normalizeWhitespace(transform(indexHtml))).toBe(normalizeWhitespace(withoutComments(indexHtml)))
  })
})

describe('stripHtmlComments plugin', () => {
  it('runs only for the build', () => {
    expect(stripHtmlComments().apply).toBe('build')
  })

  it('keeps conditional comments', () => {
    const html = '<head><!--[if IE]><link rel="stylesheet" href="ie.css"><![endif]--></head>'

    expect(transform(html)).toBe(html)
  })

  it('keeps comment-like text inside a script element', () => {
    const html = '<script>const marker = "<!-- not a comment -->"</script>'

    expect(transform(html)).toBe(html)
  })

  it('keeps comment-like text inside a style element', () => {
    const html = '<style>/* x */ a::before { content: "<!-- y -->" }</style>'

    expect(transform(html)).toBe(html)
  })

  it('removes a comment that sits next to a script element', () => {
    expect(transform('<!-- note --><script>run()</script>')).toBe('<script>run()</script>')
  })
})
