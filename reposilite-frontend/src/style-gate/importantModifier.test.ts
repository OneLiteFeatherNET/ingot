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
import { describe, expect, it } from 'vitest'

// A Tailwind important modifier is a trailing or leading bang on a utility, as in `size-6!` or `!px-2`.
const IMPORTANT_UTILITY = /class="[^"]*(?:[\w\]]!(?:\s|")|\s!\w)/

describe('important modifier', () => {
  it.each([
    '../components/header/LoginModal.vue',
    '../components/search/SearchView.vue',
    '../components/header/MenuPanel.vue',
    '../components/repositories/RepositoriesTable.vue'
  ])('is not used in the template of %s', (path) => {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    expect(source, `${path} sets a utility with the important modifier`).not.toMatch(IMPORTANT_UTILITY)
  })
})
