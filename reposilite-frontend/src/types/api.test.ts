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

import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

/*
 * src/types/api.ts is generated from docs/openapi/ingot-openapi.json. This test regenerates it into a temporary
 * directory and fails when the committed file differs, so a spec change cannot land without its types.
 */

const frontendDir = resolve(import.meta.dirname, '../..')
const generator = join(frontendDir, 'scripts/generate-api-types.ts')
const committed = join(frontendDir, 'src/types/api.ts')

describe('generated api types', () => {
  let workDir: string

  beforeEach(() => {
    workDir = mkdtempSync(join(tmpdir(), 'api-types-'))
  })

  afterEach(() => {
    rmSync(workDir, { recursive: true, force: true })
  })

  it('match the committed src/types/api.ts', () => {
    const output = join(workDir, 'api.ts')

    // The generator is TypeScript, so node loads it through tsx, as `npm run types:api` does.
    execFileSync(process.execPath, ['--import', 'tsx', generator, '--output', output], { cwd: frontendDir, stdio: 'pipe' })

    expect(readFileSync(output, 'utf8'), 'src/types/api.ts is stale: run npm run types:api and commit the result').toBe(
      readFileSync(committed, 'utf8'),
    )
  }, 60_000)
})
