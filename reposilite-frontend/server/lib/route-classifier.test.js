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
import { classify } from './route-classifier.js'

const segments = ['browse', 'search', 'account', 'dashboard', 'analytics', 'access', 'tokens', 'console', 'maintenance', 'audit', 'settings']
const root = { segments, basePath: '/' }

describe('route classifier', () => {
  it('answers the root with the dashboard document', () => {
    expect(classify('/', root)).toEqual({ kind: 'document', path: '/' })
  })

  it('answers the index file with the dashboard document', () => {
    expect(classify('/index.html', root).kind, 'index.html is the document').toBe('document')
  })

  it.each(segments)('answers the dashboard segment /%s with the document', (segment) => {
    expect(classify(`/${segment}`, root).kind, `/${segment} must be a dashboard route`).toBe('document')
  })

  it('answers a nested dashboard address with the document', () => {
    expect(classify('/settings/maven/repositories/releases', root).kind).toBe('document')
  })

  it('answers the analytics address with the document', () => {
    expect(classify('/analytics', root).kind).toBe('document')
  })

  it('does not treat a segment that only starts like a dashboard word as a dashboard route', () => {
    expect(classify('/settingsfoo/1.0', root).kind, 'a repository named settingsfoo is proxied').toBe('proxy')
  })

  it('answers hashed assets with the asset kind and keeps the file path', () => {
    expect(classify('/assets/index-abc123.js', root)).toEqual({ kind: 'asset', path: '/assets/index-abc123.js' })
  })

  it('answers the favicon with its own kind', () => {
    expect(classify('/favicon.png', root).kind).toBe('favicon')
  })

  it('answers the health endpoint without a backend decision', () => {
    expect(classify('/healthz', root).kind).toBe('health')
  })

  it('proxies the API', () => {
    expect(classify('/api/status/health', root)).toEqual({ kind: 'proxy', path: '/api/status/health' })
  })

  it('proxies a repository path that is not a dashboard segment', () => {
    expect(classify('/releases/com/example/app/1.0/app-1.0.jar', root).kind).toBe('proxy')
  })

  it('proxies javadoc and badge paths', () => {
    expect(classify('/releases/com/example/app/1.0/app-1.0-javadoc.jar', root).kind).toBe('proxy')
    expect(classify('/api/badge/latest/releases', root).kind).toBe('proxy')
  })

  it('proxies the console socket path', () => {
    expect(classify('/api/console/socket', root).kind).toBe('proxy')
  })

  it('answers a plugin page address with the dashboard document', () => {
    expect(classify('/settings/plugins/example/status', root)).toEqual({ kind: 'document', path: '/settings/plugins/example/status' })
  })

  it('proxies the plugin manifest to the server', () => {
    expect(classify('/api/frontend/plugin-ui', root)).toEqual({ kind: 'proxy', path: '/api/frontend/plugin-ui' })
  })

  it('proxies the stylesheet of a plugin to the server', () => {
    const path = '/api/frontend/plugin-styles/example/3f9c1a2b7d4e6f80.css'
    expect(classify(path, root)).toEqual({ kind: 'proxy', path })
  })

  describe('below a base path', () => {
    const sub = { segments, basePath: '/ingot/' }

    it('answers the document at the base path', () => {
      expect(classify('/ingot/', sub)).toEqual({ kind: 'document', path: '/' })
    })

    it('answers a nested dashboard address below the base path with the document', () => {
      expect(classify('/ingot/settings/x', sub).kind).toBe('document')
    })

    it('strips the base path before proxying, so the server sees its own paths', () => {
      expect(classify('/ingot/api/status/health', sub)).toEqual({ kind: 'proxy', path: '/api/status/health' })
    })

    it('answers the health endpoint at the root even below a base path', () => {
      expect(classify('/healthz', sub).kind).toBe('health')
    })

    it('does not answer paths outside the base path', () => {
      expect(classify('/settings/x', sub).kind, 'the dashboard lives below /ingot/ only').toBe('outside')
    })
  })
})
