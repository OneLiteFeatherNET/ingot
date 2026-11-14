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

/*
 * The one module that talks to the nexus import routes of the server (design D3). Every step reads the
 * API through it, and it imports no component. Each function answers with the parsed body. A failed call
 * rejects with an ApiProblem, which keeps the title and detail of the RFC 9457 body, or the message of a
 * body that is not a problem document. A lost connection has status 0.
 *
 * A password is only ever sent in the body of a request, never in the address. The module keeps none.
 */

import axios from 'axios'
import type { AxiosRequestConfig, AxiosRequestHeaders } from 'axios'
import type { NexusConnection } from './types'
import { createURL, handleRequest } from '../client'

// What the module reads from a failed call: the status, and the body when the server sent one.
interface FailedResponse {
  status: number
  statusText?: string
  data?: unknown
}

interface ProblemBody {
  title?: string
  detail?: string
  message?: string
  type?: string
}

interface Http {
  request(config: AxiosRequestConfig): Promise<{ data: unknown }>
}

export interface MigrationApiOptions {
  onUnauthorized?: (problem: ApiProblem) => void
}

type JobRequest = Record<string, unknown>

const MIGRATION_BASE = '/api/migration/nexus'

// The address of a report download. A plain link, so the browser fetches it with the session and no credential is in it.
const reportHref = (id: number | string, format: string) => createURL(`${MIGRATION_BASE}/jobs/${id}/report?format=${format}`)

class ApiProblem extends Error {
  status: number
  title?: string
  detail: string
  type?: string

  constructor({ status, title, detail, type }: { status: number; title?: string; detail?: string; type?: string }) {
    super(detail || title || `Request failed with status ${status}`)
    this.name = 'ApiProblem'
    this.status = status
    this.title = title
    this.detail = detail || ''
    this.type = type
  }
}

const problemOf = (error: unknown): ApiProblem => {
  const response = (error as { response?: FailedResponse } | null | undefined)?.response
  if (!response) {
    return new ApiProblem({ status: 0, title: 'Network error', detail: 'The server could not be reached.' })
  }
  const data = (response.data && typeof response.data === 'object' ? response.data : {}) as ProblemBody
  return new ApiProblem({
    status: response.status,
    title: data.title ?? response.statusText ?? `Status ${response.status}`,
    detail: data.detail ?? data.message ?? '',
    type: data.type,
  })
}

/*
 * `http.request({ method, url, data, params })` resolves to `{ data }` and rejects with an axios-like error
 * (`response.status`, `response.data`). `onUnauthorized` is told once about a 401, before the caller sees it.
 */
const createMigrationApi = (http: Http, { onUnauthorized = () => {} }: MigrationApiOptions = {}) => {
  const call = async <T = unknown>(request: AxiosRequestConfig): Promise<T> => {
    try {
      const response = await http.request(request)
      return response.data as T
    } catch (error) {
      const problem = problemOf(error)
      if (problem.status === 401) onUnauthorized(problem)
      throw problem
    }
  }

  return {
    testConnection: (connection: NexusConnection) => call({ method: 'POST', url: `${MIGRATION_BASE}/connection/test`, data: connection }),
    discover: (connection: NexusConnection) => call({ method: 'POST', url: `${MIGRATION_BASE}/discover`, data: connection }),
    createJob: (job: JobRequest) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs`, data: job }),
    listJobs: () => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs` }),
    getJob: (id: number) => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs/${id}` }),
    pauseJob: (id: number) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs/${id}/pause` }),
    resumeJob: (id: number) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs/${id}/resume` }),
    cancelJob: (id: number) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs/${id}/cancel` }),
    supplyCredentials: (id: number, password: string) => call({ method: 'PUT', url: `${MIGRATION_BASE}/jobs/${id}/credentials`, data: { password } }),
    report: (id: number, format = 'json') => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs/${id}/report`, params: { format } }),
    conflicts: (id: number) => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs/${id}/conflicts` }),
    deleteJob: (id: number) => call({ method: 'DELETE', url: `${MIGRATION_BASE}/jobs/${id}` }),
  }
}

// The transport of the dashboard: the same cookie and CSRF handling as every other call of the dashboard.
const dashboardHttp = {
  request: (config: AxiosRequestConfig) => axios.request(handleRequest({
    ...config,
    url: createURL(config.url as string),
    withCredentials: true,
    headers: {} as AxiosRequestHeaders,
  }) as AxiosRequestConfig),
}

let unauthorizedListener: (problem: ApiProblem) => void = () => {}

// The page tells the module what to do on a 401 (drop the password, open the sign in dialog).
const onMigrationUnauthorized = (listener: (problem: ApiProblem) => void) => {
  unauthorizedListener = listener
}

const migrationApi = createMigrationApi(dashboardHttp, {
  onUnauthorized: (problem) => unauthorizedListener(problem),
})

export { createMigrationApi, migrationApi, onMigrationUnauthorized, reportHref, ApiProblem, MIGRATION_BASE }
