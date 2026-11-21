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
import { createRouter, createMemoryHistory, type RouteRecordRaw } from 'vue-router'
import { ROUTES } from './routes'

// The table as the app builds it, on memory history, so no browser is read.
const router = createRouter({
  history: createMemoryHistory(),
  routes: ROUTES.map((route): RouteRecordRaw => (route.redirect ? route as RouteRecordRaw : { ...route, component: {} } as RouteRecordRaw)),
})

const nameOf = (path: string) => router.resolve(path).name

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

describe('reader page address', () => {
  it('resolves a reader page to the plugin-reader route, with the plugin and the page id as params', () => {
    const resolved = router.resolve('/plugins/docs/guides')

    expect(resolved.name, 'the reader route').toBe('plugin-reader')
    expect(resolved.params, 'the plugin and the page id').toEqual({ plugin: 'docs', page: 'guides' })
  })

  it('resolves a reader address without its page id to the plugin-reader route too', () => {
    expect(nameOf('/plugins/docs')).toBe('plugin-reader')
  })

  it('needs no sign in and no permission node for a reader page', () => {
    const meta = router.resolve('/plugins/docs/guides').meta

    expect(meta.manager, 'a reader page is not a manager page').toBeFalsy()
    expect(meta.node, 'a reader page names no node').toBeUndefined()
    expect(meta.signedIn, 'an anonymous visitor may open a reader page').toBeFalsy()
  })
})
