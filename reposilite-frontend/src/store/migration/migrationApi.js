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
import { createURL, handleRequest } from '../client'

const MIGRATION_BASE = '/api/migration/nexus'

// The address of a report download. A plain link, so the browser fetches it with the session and no credential is in it.
const reportHref = (id, format) => createURL(`${MIGRATION_BASE}/jobs/${id}/report?format=${format}`)

class ApiProblem extends Error {
  constructor({ status, title, detail, type }) {
    super(detail || title || `Request failed with status ${status}`)
    this.name = 'ApiProblem'
    this.status = status
    this.title = title
    this.detail = detail || ''
    this.type = type
  }
}

const problemOf = (error) => {
  const response = error?.response
  if (!response) {
    return new ApiProblem({ status: 0, title: 'Network error', detail: 'The server could not be reached.' })
  }
  const data = response.data && typeof response.data === 'object' ? response.data : {}
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
const createMigrationApi = (http, { onUnauthorized = () => {} } = {}) => {
  const call = async (request) => {
    try {
      const response = await http.request(request)
      return response.data
    } catch (error) {
      const problem = problemOf(error)
      if (problem.status === 401) onUnauthorized(problem)
      throw problem
    }
  }

  return {
    testConnection: (connection) => call({ method: 'POST', url: `${MIGRATION_BASE}/connection/test`, data: connection }),
    discover: (connection) => call({ method: 'POST', url: `${MIGRATION_BASE}/discover`, data: connection }),
    createJob: (job) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs`, data: job }),
    listJobs: () => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs` }),
    getJob: (id) => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs/${id}` }),
    pauseJob: (id) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs/${id}/pause` }),
    resumeJob: (id) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs/${id}/resume` }),
    cancelJob: (id) => call({ method: 'POST', url: `${MIGRATION_BASE}/jobs/${id}/cancel` }),
    supplyCredentials: (id, password) => call({ method: 'PUT', url: `${MIGRATION_BASE}/jobs/${id}/credentials`, data: { password } }),
    report: (id, format = 'json') => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs/${id}/report`, params: { format } }),
    conflicts: (id) => call({ method: 'GET', url: `${MIGRATION_BASE}/jobs/${id}/conflicts` }),
    deleteJob: (id) => call({ method: 'DELETE', url: `${MIGRATION_BASE}/jobs/${id}` }),
  }
}

// The transport of the dashboard: the same cookie and CSRF handling as every other call of the dashboard.
const dashboardHttp = {
  request: (config) => axios.request(handleRequest({
    ...config,
    url: createURL(config.url),
    withCredentials: true,
    headers: {},
  })),
}

let unauthorizedListener = () => {}

// The page tells the module what to do on a 401 (drop the password, open the sign in dialog).
const onMigrationUnauthorized = (listener) => {
  unauthorizedListener = listener
}

const migrationApi = createMigrationApi(dashboardHttp, {
  onUnauthorized: (problem) => unauthorizedListener(problem),
})

export { createMigrationApi, migrationApi, onMigrationUnauthorized, reportHref, ApiProblem, MIGRATION_BASE }
