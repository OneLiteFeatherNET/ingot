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
import { createWizardStore } from './wizardStore'
import { createPasswordHolder } from './password'

const PASSWORD = 'literal-password-4711'

const freshStore = () => {
  const passwords = createPasswordHolder()
  const store = createWizardStore({ passwords })
  return { store, passwords }
}

describe('the password holder', () => {
  it('returns the password it was given until it is forgotten', () => {
    const passwords = createPasswordHolder()

    passwords.remember(PASSWORD)
    expect(passwords.recall()).toBe(PASSWORD)

    passwords.forget()
    expect(passwords.recall(), 'a forgotten password is gone').toBeNull()
  })

  it('answers null before anything was remembered', () => {
    expect(createPasswordHolder().recall()).toBeNull()
  })

  it('keeps no copy in any object that a store could show', () => {
    const passwords = createPasswordHolder()

    passwords.remember(PASSWORD)

    expect(Object.keys(passwords)).not.toContain(PASSWORD)
    expect(JSON.stringify(passwords)).not.toContain(PASSWORD)
  })
})

describe('the wizard store', () => {
  it('keeps the connection without the literal password', () => {
    const { store, passwords } = freshStore()
    passwords.remember(PASSWORD)

    store.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'literal', version: '3.70.1' })

    expect(JSON.stringify(store.state), 'the reactive state holds no password').not.toContain(PASSWORD)
    expect(store.state.connection.url).toBe('https://nexus.example.test')
  })

  it('keeps a secret reference as text, because it is not a secret', () => {
    const { store } = freshStore()

    store.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'reference', reference: '${env:NEXUS_PASSWORD}' })

    expect(store.state.connection.reference).toBe('${env:NEXUS_PASSWORD}')
  })

  it('drops the remembered password when the wizard is reset', () => {
    const { store, passwords } = freshStore()
    passwords.remember(PASSWORD)

    store.reset()

    expect(passwords.recall(), 'the reset is the end of the wizard visit').toBeNull()
    expect(store.state.connection).toBeNull()
  })

  it('records the acceptance of one dry run with its fingerprint', () => {
    const { store } = freshStore()

    store.accept({ dryRunJobId: 4, fingerprint: 'abc' })

    expect(store.state.acceptance).toEqual({ dryRunJobId: 4, fingerprint: 'abc' })
  })

  it('writes nothing to browser storage', () => {
    const { store, passwords } = freshStore()
    passwords.remember(PASSWORD)

    store.setConnection({ url: 'https://nexus.example.test', user: 'importer', passwordMode: 'literal' })
    store.setMapping([{ nexusName: 'releases', targetName: 'releases', included: true }], 'refuse')
    store.accept({ dryRunJobId: 4, fingerprint: 'abc' })

    expect(window.localStorage.length, 'localStorage stays empty').toBe(0)
    expect(window.sessionStorage.length, 'sessionStorage stays empty').toBe(0)
    expect(window.location.href).not.toContain(PASSWORD)
  })

  it('replaces a job with the same id and keeps the other jobs, so a job of this visit is seen by every step', () => {
    const { store } = freshStore()
    store.setJobs([{ id: 1, kind: 'DRY_RUN', status: 'COMPLETED' }, { id: 2, kind: 'IMPORT', status: 'RUNNING' }])

    store.upsertJob({ id: 2, kind: 'IMPORT', status: 'COMPLETED' })
    store.upsertJob({ id: 3, kind: 'VERIFY', status: 'QUEUED' })

    expect(store.state.jobs.map(job => job.id)).toEqual([1, 2, 3])
    expect(store.state.jobs.find(job => job.id === 2).status).toBe('COMPLETED')
  })

  it('starts with every page value empty', () => {
    const { store } = freshStore()

    expect(store.state).toEqual({
      connection: null,
      discovery: null,
      mapping: null,
      acceptance: null,
      jobs: [],
    })
  })
})
