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

// A Tailwind default weight utility such as `font-medium` or `font-normal`, as opposed to a type scale weight token.
const DEFAULT_WEIGHT = /class="(?:[^"]*\s)?font-(?:thin|extralight|light|normal|medium|semibold|bold|extrabold|black)(?:\s|")/

describe('hand-set font weight', () => {
  it.each([
    '../components/access/ResetPasswordDialog.vue',
    '../components/migration/SyncStep.vue'
  ])('is not used in the template of %s', (path) => {
    const source = readFileSync(new URL(path, import.meta.url), 'utf8')
    expect(source, `${path} sets a weight outside the type scale tokens`).not.toMatch(DEFAULT_WEIGHT)
  })
})
