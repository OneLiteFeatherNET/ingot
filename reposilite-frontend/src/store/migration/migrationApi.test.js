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

import { describe, it, expect, vi } from 'vitest'
import { createMigrationApi, ApiProblem, MIGRATION_BASE } from './migrationApi'

// A transport that records each call and answers with the given reply. A reply that is an Error is thrown,
// as an axios error carries its response on `response`.
const recordingHttp = (reply = { status: 200, data: {} }) => {
  const request = vi.fn(async () => {
    if (reply instanceof Error) throw reply
    return reply
  })
  return { request, calls: () => request.mock.calls.map(([call]) => call) }
}

const refusal = (status, data) => Object.assign(new Error(`status ${status}`), { response: { status, data } })

const connection = { url: 'https://nexus.example.test', user: 'importer', password: 'literal-secret' }

describe('the migration API module', () => {
  it('posts the connection test with the password in the body only', async () => {
    const http = recordingHttp({ status: 200, data: { reachable: true } })
    const api = createMigrationApi(http)

    await api.testConnection(connection)

    const [call] = http.calls()
    expect(call.method).toBe('POST')
    expect(call.url).toBe(`${MIGRATION_BASE}/connection/test`)
    expect(call.data).toEqual(connection)
    expect(call.params).toBeUndefined()
  })

  it('never puts a password into an address, for any route', async () => {
    const http = recordingHttp({ status: 200, data: [] })
    const api = createMigrationApi(http)

    await api.discover(connection)
    await api.createJob({ kind: 'DRY_RUN', ...connection })
    await api.getJob(3)
    await api.supplyCredentials(3, 'literal-secret')

    const urls = http.calls().map(call => call.url).join(' ')
    expect(urls, 'the password appears in no address').not.toContain('literal-secret')
  })

  it('names one route per operation of the job API', async () => {
    const http = recordingHttp({ status: 204, data: undefined })
    const api = createMigrationApi(http)

    await api.discover(connection)
    await api.listJobs()
    await api.getJob(5)
    await api.pauseJob(5)
    await api.resumeJob(5)
    await api.cancelJob(5)
    await api.conflicts(5)
    await api.deleteJob(5)

    expect(http.calls().map(call => `${call.method} ${call.url}`)).toEqual([
      `POST ${MIGRATION_BASE}/discover`,
      `GET ${MIGRATION_BASE}/jobs`,
      `GET ${MIGRATION_BASE}/jobs/5`,
      `POST ${MIGRATION_BASE}/jobs/5/pause`,
      `POST ${MIGRATION_BASE}/jobs/5/resume`,
      `POST ${MIGRATION_BASE}/jobs/5/cancel`,
      `GET ${MIGRATION_BASE}/jobs/5/conflicts`,
      `DELETE ${MIGRATION_BASE}/jobs/5`,
    ])
  })

  it('sends the password of a paused job only to the credentials route', async () => {
    const http = recordingHttp({ status: 204, data: undefined })
    const api = createMigrationApi(http)

    await api.supplyCredentials(9, 'literal-secret')

    const [call] = http.calls()
    expect(call.method).toBe('PUT')
    expect(call.url).toBe(`${MIGRATION_BASE}/jobs/9/credentials`)
    expect(call.data).toEqual({ password: 'literal-secret' })
  })

  it('asks for the loss report in the format it was given', async () => {
    const http = recordingHttp({ status: 200, data: { rows: [] } })
    const api = createMigrationApi(http)

    await api.report(4, 'csv')

    expect(http.calls()[0].params).toEqual({ format: 'csv' })
    expect(http.calls()[0].url).toBe(`${MIGRATION_BASE}/jobs/4/report`)
  })

  it('turns a problem body into an error with its title and detail', async () => {
    const http = recordingHttp(refusal(400, {
      status: 400,
      title: 'Bad Request',
      detail: 'Repository names may use letters, digits, dots, dashes and underscores',
    }))
    const api = createMigrationApi(http)

    const error = await api.createJob({ kind: 'DRY_RUN' }).catch(reason => reason)

    expect(error).toBeInstanceOf(ApiProblem)
    expect(error.status).toBe(400)
    expect(error.title).toBe('Bad Request')
    expect(error.detail).toBe('Repository names may use letters, digits, dots, dashes and underscores')
  })

  it('reads the message of a body that is not a problem document as the detail', async () => {
    const http = recordingHttp(refusal(403, { status: 403, message: 'The caller is not an instance admin' }))
    const api = createMigrationApi(http)

    const error = await api.listJobs().catch(reason => reason)

    expect(error.status).toBe(403)
    expect(error.detail).toBe('The caller is not an instance admin')
  })

  it('reports a lost connection as status zero', async () => {
    const http = recordingHttp(new Error('socket hang up'))
    const api = createMigrationApi(http)

    const error = await api.getJob(1).catch(reason => reason)

    expect(error.status).toBe(0)
    expect(error.title).toBe('Network error')
  })

  it('hands a 401 to the unauthorized hook before the caller sees it', async () => {
    const onUnauthorized = vi.fn()
    const http = recordingHttp(refusal(401, { status: 401, title: 'Unauthorized' }))
    const api = createMigrationApi(http, { onUnauthorized })

    const error = await api.getJob(2).catch(reason => reason)

    expect(onUnauthorized, 'the sign in flow is started once').toHaveBeenCalledTimes(1)
    expect(error.status).toBe(401)
  })
})
