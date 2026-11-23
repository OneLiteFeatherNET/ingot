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

const FIELD_COMPONENTS = [
  '../components/md/MdTextField.vue',
  '../components/md/MdTextArea.vue',
  '../components/md/MdSelect.vue',
  '../components/md/MdChipInput.vue'
]
const TOKENS = ['md-field-focus', 'md-field-error', 'md-field-underline-focus', 'md-field-underline-error']

describe('field edge shadows', () => {
  it.each(FIELD_COMPONENTS)('%s takes no arbitrary shadow', (name) => {
    const source = readFileSync(new URL(name, import.meta.url), 'utf8')
    expect(source, `${name} draws its field edge with an arbitrary shadow value`).not.toMatch(/shadow-\[/)
  })

  it.each(TOKENS)('the theme defines the shadow token %s', (token) => {
    const sheet = readFileSync('src/styles/md3/shape-elevation-motion.css', 'utf8')
    expect(sheet, `--shadow-${token} is missing from the theme`).toContain(`--shadow-${token}:`)
  })
})
