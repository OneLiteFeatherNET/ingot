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

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

// Both modules keep state at module level, so every test loads a fresh copy.
let placeholders
let appearance

beforeEach(async () => {
  vi.resetModules()
  placeholders = await import('./placeholders')
  appearance = await import('./appearance')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
  vi.useRealTimers()
})

const settingsResponse = (overrides = {}) => ({
  placeholders: {
    '{{REPOSILITE.BASE_PATH}}': '/ingot/',
    '{{REPOSILITE.VITE_BASE_PATH}}': 'ingot',
    '{{REPOSILITE.TITLE}}': 'Served Title'
  },
  id: 'served-id',
  title: 'Served Title',
  description: 'Served description',
  organizationWebsite: '',
  organizationLogo: 'https://example.org/logo.png',
  privacyPolicy: '',
  icpLicense: '',
  appearance: { seedColor: '#006a6a' },
  ...overrides
})

const respondWith = (body, init = {}) => {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    statusText: init.statusText ?? 'OK',
    headers: { 'Content-Type': 'application/json' }
  }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('loadFrontendSettings in a bundled dashboard', () => {
  // A bundle is built for production, so the settings URL is the production one.
  beforeEach(() => {
    vi.stubEnv('NODE_ENV', 'production')
  })

  it('fetches the settings from under the mount path the server substituted', async () => {
    const fetchMock = respondWith(settingsResponse())

    await placeholders.loadFrontendSettings({ mountPath: '/ingot/' })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe(`${location.origin}/ingot/api/frontend/settings`)
  })

  it('applies the appearance of the settings even though the placeholders were substituted', async () => {
    respondWith(settingsResponse({ appearance: { seedColor: '#006a6a' } }))

    await placeholders.loadFrontendSettings({ mountPath: '/' })

    expect(appearance.useAppearance().seedColor, 'the bundled dashboard must not skip the appearance').toBe('#006a6a')
  })

})

describe('loadFrontendSettings in a detached dashboard', () => {
  it('reads the base path, title and other fields from the settings it fetched', async () => {
    respondWith(settingsResponse({
      placeholders: { '{{REPOSILITE.BASE_PATH}}': '/ingot/' },
      title: 'Detached Title'
    }))

    await placeholders.loadFrontendSettings({ mountPath: '/ingot/' })
    const values = placeholders.default()

    expect(values.basePath).toBe('/ingot/')
    expect(values.title, 'the typed field wins over the fallback').toBe('Detached Title')
  })

  it('fetches from the mount path of the build in production', async () => {
    vi.stubEnv('NODE_ENV', 'production')
    const fetchMock = respondWith(settingsResponse())

    await placeholders.loadFrontendSettings({ mountPath: '/ingot' })

    expect(fetchMock.mock.calls[0][0]).toBe(`${location.origin}/ingot/api/frontend/settings`)
  })
})

describe('loadFrontendSettings when the settings cannot be had', () => {
  let consoleError

  beforeEach(() => {
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleError.mockRestore()
  })

  it('keeps the default appearance when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))

    await expect(placeholders.loadFrontendSettings({ mountPath: '/' })).resolves.toBeUndefined()

    expect(appearance.useAppearance().seedColor, 'no seed means the static theme').toBe('')
  })

  it('keeps the default appearance and the fallback title when the server answers with an error', async () => {
    respondWith({ status: 500 }, { status: 500, statusText: 'Internal Server Error' })

    await placeholders.loadFrontendSettings({ mountPath: '/' })

    expect(appearance.useAppearance().seedColor).toBe('')
    expect(placeholders.default().title).toBe('Ingot Repository')
  })

  it('reports the failure in the console, not with a user facing error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))

    await placeholders.loadFrontendSettings({ mountPath: '/' })

    expect(consoleError).toHaveBeenCalledTimes(1)
  })

  it('stops waiting after the timeout and keeps the defaults', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((url, { signal }) => new Promise((_, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    })))

    const pending = placeholders.loadFrontendSettings({ mountPath: '/', timeoutMs: 2000 })
    await vi.advanceTimersByTimeAsync(2000)

    await expect(pending).resolves.toBeUndefined()
    expect(appearance.useAppearance().seedColor).toBe('')
  })
})

describe('settings URL', () => {
  it('is a path under the mount point in production, whatever the current page is', () => {
    expect(placeholders.settingsUrlFor({ mountPath: '/ingot/', origin: 'https://repo.example', production: true }))
      .toBe('https://repo.example/ingot/api/frontend/settings')
  })

  it('adds the missing trailing slash to the mount path', () => {
    expect(placeholders.settingsUrlFor({ mountPath: '/ingot', origin: 'https://repo.example', production: true }))
      .toBe('https://repo.example/ingot/api/frontend/settings')
  })

  it('uses the root when the dashboard is mounted at the root', () => {
    expect(placeholders.settingsUrlFor({ mountPath: '/', origin: 'https://repo.example', production: true }))
      .toBe('https://repo.example/api/frontend/settings')
  })

  it('uses the local API while developing', () => {
    expect(placeholders.settingsUrlFor({ mountPath: '/', origin: 'https://repo.example', production: false }))
      .toMatch(/^http:\/\/localhost:\d+\/api\/frontend\/settings$/)
  })
})

describe('resolveMountPath', () => {
  it('takes the base path the server substituted into the bundle', () => {
    expect(placeholders.resolveMountPath('/ingot/', '.')).toBe('/ingot/')
  })

  it('takes the Vite base of a detached build when the placeholder was not substituted', () => {
    expect(placeholders.resolveMountPath('{{REPOSILITE.BASE_PATH}}', '/ingot/')).toBe('/ingot/')
  })
})
