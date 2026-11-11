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
import { createRouter, createMemoryHistory } from 'vue-router'
import { ROUTES } from './routes'

// The table as the app builds it, on memory history, so no browser is read.
const router = createRouter({
  history: createMemoryHistory(),
  routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: {} })),
})

const nameOf = (path) => router.resolve(path).name

describe('plugin page address', () => {
  it('resolves a plugin page to the settings-plugin route', () => {
    expect(nameOf('/settings/plugins/example/status')).toBe('settings-plugin')
  })

  it('resolves a plugin page without its page id to the settings-plugin route too', () => {
    expect(nameOf('/settings/plugins/example')).toBe('settings-plugin')
  })

  it('passes the plugin and the page id as params', () => {
    const resolved = router.resolve('/settings/plugins/example/status')

    expect(resolved.params).toEqual({ plugin: 'example', page: 'status' })
  })

  it('keeps a settings entry on its own route', () => {
    expect(nameOf('/settings/maven/repositories/releases')).toBe('settings-entry')
  })

  it('keeps a settings domain on its own route', () => {
    expect(nameOf('/settings/web')).toBe('settings')
  })
})
