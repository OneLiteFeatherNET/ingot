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
import { join } from 'node:path'

// The source text is read from disk. A ?raw import would be emptied by vitest, which does not process stylesheets
// for unit tests. The path is relative to the frontend directory, which is where vitest runs.
const stylesheet = readFileSync(join(process.cwd(), 'src', 'style.css'), 'utf8')

// The text of the block that starts at `start`, from its at-rule to the brace that closes it.
const blockAt = (css, start) => {
  let depth = 0
  for (let index = css.indexOf('{', start); index < css.length; index++) {
    if (css[index] === '{') depth++
    if (css[index] === '}' && --depth === 0) return css.slice(start, index + 1)
  }
  return ''
}

describe('global reduced motion rule in the stylesheet', () => {
  const baseLayer = stylesheet.indexOf('@layer base {')
  const reducedMotion = stylesheet.indexOf('@media (prefers-reduced-motion: reduce)', baseLayer)

  it('has a reduced motion block inside the base layer', () => {
    expect(baseLayer, 'the base layer of the stylesheet').toBeGreaterThanOrEqual(0)
    expect(reducedMotion, 'a prefers-reduced-motion block inside the base layer').toBeGreaterThan(baseLayer)
  })

  it('applies to every element', () => {
    expect(blockAt(stylesheet, reducedMotion), 'the selector of the reduced motion rule').toMatch(/\*/)
  })

  it('shortens animations and transitions to 0.01ms and plays each animation once', () => {
    const block = blockAt(stylesheet, reducedMotion)

    expect(block, 'the animation duration').toContain('animation-duration: 0.01ms')
    expect(block, 'the animation iteration count').toContain('animation-iteration-count: 1')
    expect(block, 'the transition duration').toContain('transition-duration: 0.01ms')
  })
})

// The top-level blocks of a stylesheet as { header, body }, comments removed first. The header is the text between the
// previous statement and the opening brace, so it reads as the selector or at-rule of the block.
const topLevelBlocks = (css) => {
  const text = css.replace(/\/\*[\s\S]*?\*\//g, '')
  const blocks = []
  let depth = 0
  let boundary = 0
  for (let index = 0; index < text.length; index++) {
    const character = text[index]
    if (character === '{') {
      if (depth === 0) {
        blocks.push({ header: text.slice(boundary, index).trim(), open: index })
      }
      depth++
    } else if (character === '}') {
      depth--
      if (depth === 0) {
        const last = blocks[blocks.length - 1]
        last.body = text.slice(last.open + 1, index)
        boundary = index + 1
      }
    } else if (character === ';' && depth === 0) {
      boundary = index + 1
    }
  }
  return blocks
}

// The selectors that sit outside every cascade layer. The two token hooks, :root and .dark, are allowed: they only
// declare custom properties. Blocks of at-rules are skipped, except @media, which is searched for the same rules.
const unlayeredSelectors = (css) => {
  const found = []
  const visit = (text) => {
    for (const block of topLevelBlocks(text)) {
      if (block.header.startsWith('@media')) {
        visit(block.body)
      } else if (!block.header.startsWith('@') && !block.header.startsWith(':root') && block.header !== '.dark') {
        found.push(block.header)
      }
    }
  }
  visit(css)
  return found
}

describe('cascade layers of the dashboard styles', () => {
  const materialSheets = ['color.css', 'typography.css', 'shape-elevation-motion.css', 'state-layer.css', 'touch-target.css']
    .map(name => [name, readFileSync(join(process.cwd(), 'src', 'styles', 'md3', name), 'utf8')])

  it('orders the layers so that ingot-plugins comes last', () => {
    expect(stylesheet, 'the layer statement names ingot-plugins as the last layer').toContain(
      '@layer theme, base, components, utilities, ingot-plugins;'
    )
  })

  it('leaves no selector outside a layer in the stylesheet', () => {
    expect(unlayeredSelectors(stylesheet), 'every selector of src/style.css sits inside a layer or is a utility').toEqual([])
  })

  for (const [name, source] of materialSheets) {
    it(`leaves no selector outside a layer in src/styles/md3/${name}`, () => {
      expect(unlayeredSelectors(source), `every selector of ${name} sits inside a layer or is a utility`).toEqual([])
    })
  }
})

describe('container utility', () => {
  const utilityAt = stylesheet.indexOf('@utility container {')
  const containerUtility = utilityAt < 0 ? '' : blockAt(stylesheet, utilityAt)

  it('is declared as a utility in src/style.css', () => {
    expect(utilityAt, 'a @utility container block in src/style.css').toBeGreaterThanOrEqual(0)
  })

  it('sets no width, so the built-in container keeps the width steps of the dashboard', () => {
    expect(containerUtility, 'the container utility only adds the gutter').not.toMatch(/max-width|width:/)
  })

  it('takes the 16px gutter below 600px and the 24px gutter from 600px from the window margin tokens', () => {
    expect(containerUtility, 'the compact gutter is the compact window margin').toContain(
      'padding-inline: var(--md-sys-window-margin-compact)'
    )
    expect(containerUtility, 'the medium gutter is the medium window margin from 600px').toMatch(
      /@media \(width >= 600px\)\s*\{\s*padding-inline: var\(--md-sys-window-margin-medium\)/
    )
  })
})
