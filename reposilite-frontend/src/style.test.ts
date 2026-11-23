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
const blockAt = (css: string, start: number) => {
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
interface TopLevelBlock {
  header: string
  open: number
  body?: string
}

const topLevelBlocks = (css: string): TopLevelBlock[] => {
  const text = css.replace(/\/\*[\s\S]*?\*\//g, '')
  const blocks: TopLevelBlock[] = []
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
const unlayeredSelectors = (css: string) => {
  const found: string[] = []
  const visit = (text: string) => {
    for (const block of topLevelBlocks(text)) {
      if (block.header.startsWith('@media')) {
        visit(block.body as string)
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

// Every class selector of the dashboard stylesheets, found in the selector text of each rule (comments removed).
// Rules inside @utility and @custom-variant blocks are not selectors of the stylesheet, so they are skipped.
const classSelectorsOf = (css: string) => {
  const classes: string[] = []
  const visit = (text: string) => {
    for (const block of topLevelBlocks(text)) {
      if (block.header.startsWith('@media') || block.header.startsWith('@layer')) {
        visit(block.body as string)
      } else if (!block.header.startsWith('@')) {
        classes.push(...[...block.header.matchAll(/\.([a-zA-Z_][\w-]*)/g)].map((match) => match[1]))
      }
    }
  }
  visit(css)
  return classes
}

describe('class selectors of the dashboard stylesheets', () => {
  const sheets = ['color.css', 'typography.css', 'shape-elevation-motion.css', 'state-layer.css', 'touch-target.css']
    .map(name => [name, readFileSync(join(process.cwd(), 'src', 'styles', 'md3', name), 'utf8')])

  it('keeps only the .dark colour hook and the .vfm library hook as class selectors in src/style.css', () => {
    const unexpected = classSelectorsOf(stylesheet).filter((name) => name !== 'dark' && name !== 'vfm')
    expect(unexpected, 'a class selector in src/style.css that is not a documented hook: convert it to a utility').toEqual([])
  })

  for (const [name, source] of sheets) {
    it(`keeps only the .dark colour hook as a class selector in src/styles/md3/${name}`, () => {
      const unexpected = classSelectorsOf(source).filter((className) => className !== 'dark')
      expect(unexpected, `a class selector in ${name} that is not the dark colour hook: convert it to a utility`).toEqual([])
    })
  }
})

describe('reduced motion rule in the stylesheets', () => {
  const sources = [stylesheet, ...['color.css', 'typography.css', 'shape-elevation-motion.css', 'state-layer.css', 'touch-target.css']
    .map(name => readFileSync(join(process.cwd(), 'src', 'styles', 'md3', name), 'utf8'))]

  it('is written once across the dashboard stylesheet and the token files', () => {
    const count = sources.reduce((total, css) => total + css.split('prefers-reduced-motion').length - 1, 0)
    expect(count, 'the prefers-reduced-motion rule appears once, in the base layer of src/style.css').toBe(1)
  })
})

describe('scrollbar gutter of the root element', () => {
  // Every rule whose selector is the root element, `html`. Its declarations decide whether a page reserves the space of
  // a vertical scrollbar. The gutter is not reserved: a reserved gutter sits outside the page box, so the sticky top bar
  // and the page sheet cannot paint into it and stop short of the window edge. The frame keeps its left edge anyway,
  // because it is laid out from the left.
  const rootRules = [...stylesheet.matchAll(/^\s*html\s*\{/gm)].map((match) => blockAt(stylesheet, match.index))

  it('lets the root element keep no scrollbar gutter, so the top bar reaches the window edge', () => {
    expect(rootRules.join('\n'), 'a declaration scrollbar-gutter: auto in a rule for html').toContain('scrollbar-gutter: auto')
  })

  it('reserves no gutter on the root element', () => {
    expect(rootRules.join('\n'), 'no reserved gutter on html').not.toContain('scrollbar-gutter: stable')
  })
})

describe('motion transitions and the one reduced motion rule', () => {
  const motionStylesheets = ['src/styles/md3/motion-transitions.css', 'src/styles/md3/shape-elevation-motion.css']

  it('imports the named transition classes into the stylesheet', () => {
    expect(stylesheet, 'style.css imports the transition classes').toContain('@import "./styles/md3/motion-transitions.css";')
  })

  it('declares no second reduced motion block in the transition classes', () => {
    for (const path of motionStylesheets) {
      const css = readFileSync(join(process.cwd(), path), 'utf8')

      expect(css, `${path} leaves reduced motion to the rule of src/style.css`).not.toContain('prefers-reduced-motion')
    }
  })
})

describe('inverse corners of the content sheet', () => {
  // The sticky corner pieces paint the frame over the sheet's own corner while the page scrolls, so they repaint the
  // sheet's 1 pixel outline edge on their arc too. Without it the edge would break off at the top bar.
  for (const piece of ['pane-corner-start', 'pane-corner-end']) {
    it(`draws the 1 pixel outline edge on the arc of ${piece}`, () => {
      const block = blockAt(stylesheet, stylesheet.indexOf(`@utility ${piece} {`))

      expect(block, `the outline role in ${piece}`).toContain('var(--md-sys-color-outline)')
      expect(block, `the edge starts 1 pixel inside the arc of ${piece}`)
        .toContain('calc(var(--md-sys-shape-corner-extra-large) - 1px)')
    })
  }
})

describe('motion section of DESIGN.md', () => {
  const design = readFileSync(join(process.cwd(), 'DESIGN.md'), 'utf8')
  const start = design.indexOf('\n## Motion\n')
  const end = design.indexOf('\n## ', start + 1)
  const section = design.slice(start, end)

  it('has a motion section', () => {
    expect(start, 'a "## Motion" heading in DESIGN.md').toBeGreaterThanOrEqual(0)
  })

  it.each([
    'fade through',
    'shared axis x',
    'shared axis y',
    'menu from anchor',
    'dialog',
    'side sheet',
    'snackbar',
    'expand and collapse',
    'content fade',
    'indicator slide',
  ])('names the %s pattern', (pattern) => {
    expect(section.toLowerCase(), `the motion section names the ${pattern} pattern of add-dashboard-motion`).toContain(pattern)
  })

  it('names the transition wrapper and the opt-out field of the plugin page descriptor', () => {
    expect(section, 'the wrapper that applies the patterns').toContain('MdTransition')
    expect(section, 'the motion field of the plugin page descriptor').toContain('`motion`')
    expect(section, 'the default value of the field').toContain('`route`')
    expect(section, 'the opt-out value of the field').toContain('`none`')
  })

  it('states the 300 millisecond total of a page transition', () => {
    expect(section, 'the total of a page transition').toMatch(/300 ?ms|300 milliseconds/)
  })

  it('no longer describes the removed page rise', () => {
    const paragraphs = section.split(/\n\s*\n/)
    const risen = paragraphs.filter((paragraph) => /animate-md-page-enter|rises? 4 pixels|rise class/i.test(paragraph))
    expect(risen, 'no paragraph of the motion section describes the page rise').toEqual([])
  })
})

describe('md-scrollbar utility', () => {
  const start = stylesheet.indexOf('@utility md-scrollbar')
  const block = blockAt(stylesheet, start)

  it('is defined in the stylesheet', () => {
    expect(start, 'an @utility md-scrollbar block in src/style.css').toBeGreaterThanOrEqual(0)
  })

  it('draws a thin bar in the outline variant colour on a transparent track', () => {
    expect(block, 'the standard property for Firefox and Chromium').toContain('scrollbar-width: thin')
    expect(block, 'the thumb and track colour come from the colour tokens').toContain('scrollbar-color: var(--md-sys-color-outline-variant) transparent')
  })

  it('keeps the six pixel bar of the old rule for WebKit, scoped to the element that opts in', () => {
    expect(block, 'the WebKit bar is nested under the utility, not app-wide').toMatch(/&::-webkit-scrollbar\s*\{[^}]*height: 6px/)
    expect(block, 'the WebKit thumb takes the outline variant colour').toMatch(/&::-webkit-scrollbar-thumb\s*\{[^}]*var\(--md-sys-color-outline-variant\)/)
  })
})

describe('search-cancel-none utility', () => {
  const start = stylesheet.indexOf('@utility search-cancel-none')
  const block = blockAt(stylesheet, start)

  it('is defined in the stylesheet', () => {
    expect(start, 'an @utility search-cancel-none block in src/style.css').toBeGreaterThanOrEqual(0)
  })

  it('hides the browser clear button of a search input', () => {
    expect(block, 'the WebKit clear button is hidden').toMatch(/&::-webkit-search-cancel-button\s*\{[^}]*display: none/)
  })
})
