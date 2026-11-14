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

// @vitest-environment node

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/*
 * The shared page gutter is a CSS rule, so the test reads the stylesheet source. Vitest does
 * not process CSS imports, which is why the source is read directly.
 *
 * MD3 window size classes set the margins: 16px on compact windows (below 600px), 24px on
 * medium and expanded windows (600px and up). The margins are the window margin tokens, and the
 * rule is the container utility, which Tailwind places in the utilities layer.
 */
const globalStyles = readFileSync(fileURLToPath(new URL('../../style.css', import.meta.url)), 'utf8')

describe('shared container gutter', () => {
  it('pads the container 16px on compact windows', () => {
    expect(globalStyles).toMatch(/@utility container \{[\s\S]*?padding-inline: var\(--md-sys-window-margin-compact\);/)
  })

  it('pads the container 24px from 600px up, the medium window size', () => {
    expect(globalStyles).toMatch(/@utility container \{[\s\S]*?@media \(width >= 600px\) \{\s*padding-inline: var\(--md-sys-window-margin-medium\);\s*\}/)
  })

  it('declares the gutter as a utility, not as a plain class rule', () => {
    expect(globalStyles, 'the container gutter is a @utility, so Tailwind layers it with the other utilities').toContain('@utility container {')
    expect(globalStyles, 'no plain .container rule remains').not.toMatch(/(^|\n)\s*\.container \{/)
  })

  it('no longer uses the 40px padding of the old rule', () => {
    expect(globalStyles).not.toContain('px-10 max-sm:px-4')
  })
})

/*
 * Every view and the header take their side margins from the shared .container rule. A view
 * that sets its own px-* utility would win over the layered rule and break the 16px / 24px
 * window margins, so no container may carry one.
 */
describe('views use the shared window margins', () => {
  const containerFiles = [
    '../../components/header/DefaultHeader.vue',
    '../../components/dashboard/DashboardView.vue',
    '../../components/layout/AdministrationFrame.vue'
  ]

  it.each(containerFiles)('%s does not override the container gutter', (file) => {
    const source = readFileSync(fileURLToPath(new URL(file, import.meta.url)), 'utf8')
    const containerClasses = [...source.matchAll(/class="([^"]*(?<![-\w])container(?![-\w])[^"]*)"/g)].map(match => match[1])

    expect(containerClasses.length, 'the file must still have a container element').toBeGreaterThan(0)
    for (const classes of containerClasses) {
      expect(classes, 'a px-* utility would override the shared margins').not.toMatch(/(^|[\s:])(?:[\w-]+:)*px-\d+/)
    }
  })
})
