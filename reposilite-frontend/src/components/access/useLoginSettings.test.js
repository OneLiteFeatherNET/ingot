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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useLoginSettings } from './useLoginSettings'
import { LDAP_DEFAULTS } from './loginSettings'

const serverAnswer = (data, etag = '"v1"') => ({ data, headers: { etag } })

const rejection = (status) =>
  Object.assign(new Error(`Request failed with status code ${status}`), { response: { status, data: {} } })

describe('useLoginSettings', () => {
  let settings
  let state

  beforeEach(() => {
    settings = {
      fetch: vi.fn().mockResolvedValue(serverAnswer({ oidcProviders: [], ldap: { enabled: true, url: 'ldaps://ldap.example.org' } })),
      update: vi.fn().mockResolvedValue({})
    }
    state = useLoginSettings(() => settings)
  })

  it('reads the login domain and fills the parts the server left out', async () => {
    await state.load()

    expect(settings.fetch).toHaveBeenCalledWith('login')
    expect(state.settings.value.oidcProviders).toEqual([])
    expect(state.settings.value.ldap).toEqual({ ...LDAP_DEFAULTS, enabled: true, url: 'ldaps://ldap.example.org' })
    expect(state.loaded.value).toBe(true)
  })

  it('shows what went wrong when the domain cannot be read', async () => {
    settings.fetch.mockRejectedValue(rejection(500))

    await state.load()

    expect(state.loadError.value).toBe('Request failed with status 500')
    expect(state.loaded.value).toBe(true)
  })

  it('writes the domain with the tag it was read with', async () => {
    await state.load()
    const next = { ...state.settings.value, oidcProviders: [{ id: 'sso' }] }

    await state.save(next)

    expect(settings.update).toHaveBeenCalledWith('login', next, '"v1"')
  })

  it('reads the domain again after a write, so the next write carries the new tag', async () => {
    await state.load()
    settings.fetch.mockResolvedValue(serverAnswer({ oidcProviders: [{ id: 'sso' }] }, '"v2"'))

    const saved = await state.save({ ...state.settings.value, oidcProviders: [{ id: 'sso' }] })
    await state.save(state.settings.value)

    expect(saved).toBe(true)
    expect(settings.update.mock.calls[1][2]).toBe('"v2"')
  })

  it('reports a write that the server refused because the domain changed elsewhere', async () => {
    await state.load()
    settings.update.mockRejectedValue(rejection(412))

    const saved = await state.save(state.settings.value)

    expect(saved).toBe(false)
    expect(state.conflict.value).toBe(true)
    expect(state.saveError.value).toMatch(/changed elsewhere/)
  })

  it('reports any other refusal with the server message and keeps the saved state', async () => {
    await state.load()
    const before = state.settings.value
    settings.update.mockRejectedValue(rejection(400))

    const saved = await state.save({ ...before, oidcProviders: [{ id: 'x' }] })

    expect(saved).toBe(false)
    expect(state.conflict.value).toBe(false)
    expect(state.saveError.value).toBe('Not saved: Request failed with status 400')
    expect(state.settings.value).toBe(before)
  })

  it('is busy while a write runs', async () => {
    await state.load()
    let finish
    settings.update.mockReturnValue(new Promise(resolve => { finish = resolve }))

    const pending = state.save(state.settings.value)
    expect(state.saving.value).toBe(true)

    finish({})
    await pending
    expect(state.saving.value).toBe(false)
  })
})
