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

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// A Tailwind important modifier is a trailing or leading bang on a utility, as in `size-6!` or `!px-2`.
const IMPORTANT_UTILITY = /class="[^"]*(?:[\w\]]!(?:\s|")|\s!\w)/

describe('important modifier', () => {
  it.each([
    '../components/header/LoginModal.vue',
    '../components/search/SearchView.vue',
    '../components/header/MenuPanel.vue',
    '../components/repositories/RepositoriesTable.vue',
    '../components/md/MdSelect.vue',
    '../components/md/MdDialog.vue',
    '../components/browser/AdjustmentsModal.vue',
    '../components/browser/DeleteEntryModal.vue'
  ])('is not used in the template of %s', (path) => {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    expect(source, `${path} sets a utility with the important modifier`).not.toMatch(IMPORTANT_UTILITY)
  })

  it.each([
    '../components/md/MdSelect.vue',
    '../components/md/MdDialog.vue',
    '../components/browser/AdjustmentsModal.vue',
    '../components/browser/DeleteEntryModal.vue'
  ])('is not used in a bound class string of %s', (path) => {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    expect(source, `${path} binds a utility with the important modifier`).not.toMatch(/['"][\w/:-]+!['"]/)
  })

  // The vue-final-modal stylesheet is unlayered, and an unlayered rule beats every layered utility whatever its
  // specificity. That is why the scrim utilities needed the important modifier. Importing the stylesheet into the
  // base layer puts it below the utilities, so bg-scrim/md-scrim wins on its own.
  it('imports the vue-final-modal stylesheet into a layer below the utilities', () => {
    const css = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'style.css'), 'utf8')
    expect(css, 'style.css imports the vue-final-modal stylesheet into the base layer').toMatch(
      /@import\s+["'][./]*node_modules\/vue-final-modal\/dist\/style\.css["']\s+layer\(base\)/
    )
    const main = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', 'main.ts'), 'utf8')
    expect(main, 'main.ts does not import it a second time, unlayered').not.toContain('vue-final-modal/style.css')
  })
})
