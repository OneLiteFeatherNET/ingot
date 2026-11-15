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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { createWebHistory } from 'vue-router'
import { resolveMountPath } from '../store/placeholders'

// The dashboard image takes its base path at start, from the base element of the document, so the
// router is only right below a sub-path when vue-router reads that element. The jsdom location is
// reset after each test so no test depends on another.
describe('base path from the document', () => {
  beforeEach(() => {
    document.head.innerHTML = '<base href="/ingot/" />'
    window.history.replaceState({}, '', '/ingot/')
  })

  afterEach(() => {
    document.head.innerHTML = ''
    window.history.replaceState({}, '', '/')
  })

  it('reads a deep link below the base element as the route without the base', () => {
    window.history.replaceState({}, '', '/ingot/settings/x')
    const history = createWebHistory()

    expect(history.location, 'the base element of the document is the mount point').toBe('/settings/x')
  })

  it('reads the mount point of a detached build from the base element when no placeholder was substituted', () => {
    expect(resolveMountPath('{{REPOSILITE.BASE_PATH}}', './')).toBe('/ingot/')
  })

  it('keeps the substituted placeholder value when the server built the page', () => {
    expect(resolveMountPath('/mounted/', './')).toBe('/mounted/')
  })
})
