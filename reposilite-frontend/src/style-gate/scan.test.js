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
import { join } from 'node:path'
import { RULES } from './rules.js'
import { scanFile } from './scan.js'
import { createResolver } from './tailwind.js'

// Fixtures are strings built inside each test. Nothing is read from disk here, and the resolver is built per call.
const SRC = join(process.cwd(), 'src')

const ruleNamed = (id) => RULES.find((rule) => rule.id === id)

const found = async (ruleId, file, text) => scanFile(ruleNamed(ruleId), file, text, await createResolver(SRC))

const VUE = 'src/components/Fixture.vue'
const SCRIPT = 'src/store/fixture.js'

describe('rule 1: no style block in a component', () => {
  it('reports a style element in a component', async () => {
    const text = '<template><p>card</p></template>\n<style scoped>\n.card { color: red }\n</style>'

    expect(await found('style-block', VUE, text), 'the style element of the component').toEqual(['<style'])
  })

  it('does not report a style element in a script file', async () => {
    expect(await found('style-block', SCRIPT, "const css = '<style>'"), 'a script is not a component').toEqual([])
  })
})

describe('rule 2: no arbitrary values', () => {
  it('reports a window breakpoint written as a pixel literal', async () => {
    const text = '<div class="min-[600px]:flex">'

    expect(await found('arbitrary-value', VUE, text), 'the pixel breakpoint: use medium: instead').toEqual(['min-[600px]:flex'])
  })

  it('reports an arbitrary property', async () => {
    expect(await found('arbitrary-value', VUE, '<div class="[mask-type:luminance]">'), 'the arbitrary property').toEqual([
      '[mask-type:luminance]'
    ])
  })

  it('reports an arbitrary value in a script string', async () => {
    expect(await found('arbitrary-value', SCRIPT, "const root = 'p-[13px] flex'"), 'the arbitrary value in the class string').toEqual([
      'p-[13px]'
    ])
  })

  it('accepts the v4 shorthand for a named token', async () => {
    expect(await found('arbitrary-value', VUE, '<div class="bg-(--md-sys-color-surface)">'), 'the named token shorthand').toEqual([])
  })

  it('accepts a window class variant', async () => {
    expect(await found('arbitrary-value', VUE, '<div class="medium:flex max-medium:hidden">'), 'the window variants').toEqual([])
  })
})

describe('rule 3: no raw colour literals', () => {
  it('reports a hex literal in a class', async () => {
    expect(await found('raw-colour', VUE, '<div class="bg-[#3A5F85]">'), 'the hex literal in the class').toEqual(['#3A5F85'])
  })

  it('reports a hex literal in an inline style', async () => {
    expect(await found('raw-colour', VUE, '<p style="color: #3A5F85">'), 'the hex literal in the style').toEqual(['#3A5F85'])
  })

  it('reports a colour-mix with a literal percentage', async () => {
    const call = 'color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)'

    expect(await found('raw-colour', VUE, `<div :style="{ background: '${call}' }">`), 'the overlay with a literal percentage').toEqual([call])
  })

  it('does not report prose that names the hex form', async () => {
    expect(await found('raw-colour', SCRIPT, "const hint = 'Use a colour in the form #RRGGBB'"), 'the prose').toEqual([])
  })

  it('does not report a role colour', async () => {
    expect(await found('raw-colour', VUE, '<div class="bg-surface-container text-on-surface">'), 'the role colours').toEqual([])
  })

  it('does not scan the md3 colour file, which is the source of the tokens', async () => {
    expect(await found('raw-colour', 'src/styles/md3/color.css', '--md-sys-color-primary: #6750a4;'), 'the token file').toEqual([])
  })

  it('does not report a hex literal inside a data-ingot attribute', async () => {
    expect(await found('raw-colour', VUE, '<p data-ingot-part="#3A5F85">'), 'the anchor attribute').toEqual([])
  })
})

describe('rule 4: no palette colours and no non-MD3 defaults', () => {
  it('reports a Tailwind palette colour with any utility prefix', async () => {
    expect(await found('non-md3-default', VUE, '<p class="text-gray-500 bg-white">'), 'the palette colours').toEqual([
      'text-gray-500',
      'bg-white'
    ])
  })

  it('reports a default type size and line height', async () => {
    expect(await found('non-md3-default', VUE, '<p class="text-xs leading-6">'), 'the type defaults: use md-* type classes').toEqual([
      'text-xs',
      'leading-6'
    ])
  })

  it('reports a bare or default corner radius', async () => {
    expect(await found('non-md3-default', VUE, '<p class="rounded-lg rounded">'), 'the corner defaults: use rounded-corner-*').toEqual([
      'rounded-lg',
      'rounded'
    ])
  })

  it('reports a default elevation, motion, z-index and opacity', async () => {
    const text = '<p class="shadow-sm animate-pulse duration-300 ease-in z-10 opacity-50">'

    expect(await found('non-md3-default', VUE, text), 'the elevation, motion, z-index and opacity defaults').toEqual([
      'shadow-sm',
      'animate-pulse',
      'duration-300',
      'ease-in',
      'z-10',
      'opacity-50'
    ])
  })

  it('accepts the MD3 names for type, shape, elevation and colour', async () => {
    const text =
      '<p class="text-on-surface md-body-small rounded-corner-large rounded-tl-corner-extra-large shadow-elevation-2 bg-(--md-sys-color-surface)">'

    expect(await found('non-md3-default', VUE, text), 'the MD3 names').toEqual([])
  })
})

describe('rule 5: no literal inline style', () => {
  it('reports a literal size in a static style', async () => {
    expect(await found('inline-style', VUE, '<p style="width: 80%">'), 'the literal width').toEqual(['80%'])
  })

  it('reports a literal unit in a bound style', async () => {
    expect(await found('inline-style', VUE, '<p :style="{ width: `${percent}%` }">'), 'the bound width').toEqual(['`${percent}%`'])
  })

  it('accepts a style that sets no literal value', async () => {
    const text = '<p style="display: none" :style="{ color: \'var(--md-sys-color-on-surface)\' }">'

    expect(await found('inline-style', VUE, text), 'the keywords and the token').toEqual([])
  })
})

describe('rule 6: every class token resolves', () => {
  it('reports a class that no stylesheet defines', async () => {
    expect(await found('class-token', VUE, '<p class="card-editor flex">'), 'the dangling hook').toEqual(['card-editor'])
  })

  it('reports a class that is built at runtime', async () => {
    expect(await found('class-token', VUE, '<p :class="`text-${tone}`">'), 'the runtime class').toEqual(['text-${tone}'])
  })

  it('accepts utilities, theme names and window variants', async () => {
    const text = '<p class="md-body-small bg-surface-container rounded-corner-large container medium:flex">'

    expect(await found('class-token', VUE, text), 'the known names').toEqual([])
  })

  it('reports an undefined class in a script string that holds a class list', async () => {
    const text = "const styles = { root: 'one-of-container flex flex-col gap-4' }"

    expect(await found('class-token', SCRIPT, text), 'the class list').toEqual(['one-of-container'])
  })

  it('reports an undefined class selected in a query', async () => {
    expect(await found('class-token', SCRIPT, "document.querySelector('.settings-save-bar')"), 'the selector').toEqual(['settings-save-bar'])
  })

  it('ignores an attribute selector in a query', async () => {
    expect(await found('class-token', SCRIPT, "document.querySelector('[data-test=\"confirm-button\"]')"), 'the data-test selector').toEqual([])
  })

  it('ignores prose that is not a class list', async () => {
    expect(await found('class-token', SCRIPT, "const hint = 'Use a colour in the form #RRGGBB'"), 'the prose').toEqual([])
  })

  it('does not report data-ingot attributes as classes', async () => {
    const text = '<p data-ingot-component="repository-detail" data-ingot-part="header">'

    expect(await found('class-token', VUE, text), 'the anchors').toEqual([])
  })
})

describe('rule 7: plain class selectors only where documented', () => {
  it('reports a class selector in a component style block', async () => {
    const text = '<style>\n.settings-view { color: red }\n</style>'

    expect(await found('class-selector', VUE, text), 'the selector in the component').toEqual(['.settings-view'])
  })

  it('reports a class selector outside the documented hooks of a stylesheet', async () => {
    expect(await found('class-selector', 'src/style.css', '.pane-corner { top: 0 }'), 'the undocumented helper').toEqual(['.pane-corner'])
  })

  it('accepts the documented dark and vfm hooks in a stylesheet', async () => {
    const text = ':root:not(.dark) { --x: 1 }\n.vfm { z-index: 1 }'

    expect(await found('class-selector', 'src/style.css', text), 'the documented hooks').toEqual([])
  })

  it('ignores a decimal and a file name in a stylesheet', async () => {
    const text = '@import "./color.css";\nbody { opacity: 0.5 }'

    expect(await found('class-selector', 'src/styles/md3/state-layer.css', text), 'the decimal and the path').toEqual([])
  })

  it('reports a class selector in a script query', async () => {
    expect(await found('class-selector', SCRIPT, "document.querySelector('.settings-save-bar')"), 'the query').toEqual(['.settings-save-bar'])
  })
})

describe('rule 8: inline svg only in the icon folders', () => {
  it('reports an inline svg outside the icon folders', async () => {
    expect(await found('inline-svg', 'src/components/layout/Nav.vue', '<svg viewBox="0 0 1 1"></svg>'), 'the inline icon').toEqual(['<svg'])
  })

  it('accepts an inline svg in the icon folders', async () => {
    expect(await found('inline-svg', 'src/components/icons/CloseIcon.vue', '<svg></svg>'), 'the icon component').toEqual([])
    expect(await found('inline-svg', 'src/components/md/MdIcon.vue', '<svg></svg>'), 'the md component').toEqual([])
  })
})
