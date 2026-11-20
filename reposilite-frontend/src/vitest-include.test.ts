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
import { join, relative } from 'node:path'
import { createVitest } from 'vitest/node'

// The configuration of the real test run, applied to a project tree that is built in a temporary directory. Nothing
// is read from the repository's own sources, and each test gets a tree of its own.
const CONFIG = join(process.cwd(), 'vitest.config.ts')

let root: string

beforeEach(() => {
  root = mkdtempSync(join(tmpdir(), 'vitest-include-'))
})

afterEach(() => {
  rmSync(root, { recursive: true, force: true })
})

const collected = async (files: string[]): Promise<string[]> => {
  for (const file of files) {
    mkdirSync(join(root, file, '..'), { recursive: true })
    writeFileSync(join(root, file), "import { it } from 'vitest'\nit('runs', () => {})\n")
  }
  const vitest = await createVitest('test', { config: CONFIG, root, watch: false, reporters: [] })
  try {
    const specifications = await vitest.globTestSpecifications()
    return specifications.map((specification) => relative(root, specification.moduleId).split('\\').join('/')).sort()
  } finally {
    await vitest.close()
  }
}

describe('which files the unit run collects', () => {
  it('collects a TypeScript test under src', async () => {
    expect(await collected(['src/sample.test.ts']), 'the .test.ts file under src').toEqual(['src/sample.test.ts'])
  })

  it('collects a TypeScript test under server', async () => {
    expect(await collected(['server/sample.test.ts']), 'the .test.ts file under server').toEqual(['server/sample.test.ts'])
  })

  it('still collects a JavaScript test under src', async () => {
    expect(await collected(['src/sample.test.js']), 'the .test.js file under src').toEqual(['src/sample.test.js'])
  })

  it('collects the TypeScript test of the build config', async () => {
    expect(await collected(['vite.config.test.ts']), 'the test of vite.config.ts').toEqual(['vite.config.test.ts'])
  })

  it('does not collect a test outside src and server', async () => {
    expect(await collected(['e2e/sample.test.ts']), 'a test file in a directory the unit run does not cover').toEqual([])
  })
})
