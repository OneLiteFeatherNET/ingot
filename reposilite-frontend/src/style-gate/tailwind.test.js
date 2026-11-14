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
import { createResolver } from './tailwind.js'

// The resolver compiles the committed src/style.css with the Tailwind compiler. Each test builds its own resolver.
const SRC = join(process.cwd(), 'src')

describe('class tokens resolved against the compiled stylesheet', () => {
  it('accepts a utility that Tailwind builds from the theme', async () => {
    const resolver = await createResolver(SRC)

    expect(resolver.resolves('bg-surface-container'), 'the theme colour utility').toBe(true)
  })

  it('accepts a project utility declared with @utility', async () => {
    const resolver = await createResolver(SRC)

    expect(resolver.resolves('md-body-small'), 'the md-* type utility').toBe(true)
  })

  it('accepts a name declared in @theme', async () => {
    const resolver = await createResolver(SRC)

    expect(resolver.resolves('rounded-corner-large'), 'the MD3 corner from the theme').toBe(true)
  })

  it('accepts the container utility of the window margins', async () => {
    const resolver = await createResolver(SRC)

    expect(resolver.resolves('container'), 'the page gutter utility').toBe(true)
  })

  it('rejects a name that no stylesheet defines', async () => {
    const resolver = await createResolver(SRC)

    expect(resolver.resolves('card-editor'), 'the dangling hook').toBe(false)
  })
})
