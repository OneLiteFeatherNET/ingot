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
import { collectSources } from './sources'

// The tree is built in a temporary directory of the test's own, so nothing is read from the repository sources.
let src: string

beforeEach(() => {
  src = join(mkdtempSync(join(tmpdir(), 'style-gate-sources-')), 'src')
  mkdirSync(src)
})

afterEach(() => {
  rmSync(dirname(src), { recursive: true, force: true })
})

const write = (file: string, text: string) => {
  mkdirSync(dirname(join(src, file)), { recursive: true })
  writeFileSync(join(src, file), text)
}

describe('sources scanned by the style gate', () => {
  it('collects a TypeScript module', () => {
    write('store/fixture.ts', "export const colour = '#3A5F85'\n")

    expect(collectSources(src).map((source) => source.file), 'the .ts module is scanned').toEqual(['src/store/fixture.ts'])
  })

  it('leaves out a TypeScript test', () => {
    write('store/fixture.test.ts', "export const colour = '#3A5F85'\n")

    expect(collectSources(src), 'tests are not shipped sources').toEqual([])
  })

  it('does not scan a JavaScript module', () => {
    write('store/fixture.js', "export const colour = '#3A5F85'\n")

    expect(collectSources(src), 'no .js module is left under src').toEqual([])
  })
})
