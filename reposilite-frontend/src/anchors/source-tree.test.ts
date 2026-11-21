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

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { listSourceTree } from './checks'

// The tree is built in a temporary directory of the test's own, so nothing is read from the repository sources.
let root!: string

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), 'anchors-tree-'))
})

afterEach(() => {
  rmSync(root, { recursive: true, force: true })
})

const write = (file: string, text: string) => {
  mkdirSync(dirname(join(root, file)), { recursive: true })
  writeFileSync(join(root, file), text)
}

describe('source tree read by the anchor selector rule', () => {
  it('lists TypeScript modules and components', () => {
    write('src/store/a.ts', '')
    write('src/store/b.js', '')
    write('src/components/C.vue', '')
    write('src/store/notes.json', '')

    expect(listSourceTree(root), 'every TypeScript module and component, and nothing else').toEqual([
      'src/components/C.vue',
      'src/store/a.ts',
    ])
  })
})
