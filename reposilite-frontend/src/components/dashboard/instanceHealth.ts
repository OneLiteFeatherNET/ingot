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

import type { AxiosError, AxiosResponse } from 'axios'
import type { components } from '../../types/api'

// The health answer. `details` is sent on a failing check but is not declared by the OpenAPI description.
export type HealthBody = components['schemas']['HealthResponse'] & { details?: unknown }

// An answer of the health endpoint: its status and body, or null when no answer arrived at all.
export interface Answer {
  status: number
  data?: HealthBody
}

// What a probe of an integration returns: the status, the content type and the start of the body.
export interface ProbeAnswer {
  status: number
  contentType: unknown
  body: string
}

export interface Health {
  state: 'up' | 'down' | 'unknown'
  label: string
  details: string[]
}

/*
 * Reading the health endpoint and the optional integrations of the instance.
 *
 * An answer is `{ status, data }`, or `null` when no answer arrived at all. The health endpoint
 * answers 503 with a body when the instance is down, and that body is an answer like any other.
 */

/** Turns a request that may have been rejected with a status into an answer, or null without one. */
export const toAnswer = (request: Promise<AxiosResponse<HealthBody>>): Promise<Answer | null> =>
  request.then(
    response => ({ status: response.status, data: response.data }),
    (error: AxiosError<HealthBody>) => error.response
      ? { status: error.response.status, data: error.response.data }
      : null
  )

const detailLines = (details: unknown): string[] => {
  if (typeof details === 'string') {
    return [details]
  }
  if (details && typeof details === 'object') {
    return Object.entries(details as Record<string, unknown>).map(([key, value]) => `${key}: ${String(value)}`)
  }
  return []
}

/** What the health indicator shows for an answer. */
export const describeHealth = (answer: Answer | null): Health => {
  if (!answer) {
    return { state: 'unknown', label: 'Health unavailable', details: [] }
  }

  const up = answer.data?.status ? answer.data.status === 'UP' : answer.status === 200
  return up
    ? { state: 'up', label: 'Healthy', details: [] }
    : { state: 'down', label: 'Unhealthy', details: detailLines(answer.data?.details) }
}

const isHtml = (contentType: unknown): boolean =>
  typeof contentType === 'string' && contentType.includes('text/html')

/*
 * The integrations the dashboard links to, and what an answer must look like for the link to be
 * shown. A probe that finds nothing, or a fallback page that answers every path, shows no link.
 *
 * API docs: the Swagger UI plugin serves its page at /swagger (javalin-swagger-plugin default).
 * The page is only taken as Swagger UI when its markup is there, since a dashboard served for an
 * unknown path is HTML too.
 *
 * Metrics: the Prometheus plugin serves /metrics by default (reposilite.prometheus.path can move
 * it, and the dashboard cannot know that). The probe carries the session's credentials; a 401 or
 * 403 still means the endpoint is there, only refusing this caller.
 */
export interface Integration {
  label: string
  href: string
}

interface IntegrationProbe {
  label: string
  endpoint: string
  responds: (answer: ProbeAnswer) => boolean
}

export const INTEGRATIONS: IntegrationProbe[] = [
  {
    label: 'API docs',
    endpoint: '/swagger',
    responds: (answer) => answer.status === 200 && isHtml(answer.contentType) && answer.body.includes('swagger-ui')
  },
  {
    label: 'Metrics',
    endpoint: '/metrics',
    responds: (answer) => [200, 401, 403].includes(answer.status) && !isHtml(answer.contentType)
  }
]

/**
 * The integrations that respond, with their absolute links. A probe that throws counts as
 * "not there": the integrations are optional and their absence is nothing to report.
 */
export const findIntegrations = async (
  probe: (endpoint: string) => Promise<ProbeAnswer>,
  url: (endpoint: string) => string
): Promise<Integration[]> => {
  const found = await Promise.all(INTEGRATIONS.map(async ({ label, endpoint, responds }) => {
    try {
      const answer = await probe(endpoint)
      return responds({ ...answer, body: answer.body ?? '' }) ? { label, href: url(endpoint) } : null
    } catch {
      return null
    }
  }))
  return found.filter((integration): integration is Integration => integration !== null)
}

// The probes of each client, remembered at module level. A client is rebuilt when the token changes,
// so a new sign in probes again, while a dashboard that is left and opened again does not repeat them.
// Keyed by the client object, so an entry never outlives the client it belongs to.
const probedClients = new WeakMap<object, Promise<Integration[]>>()

/** The integrations of a client, probed at most once per client object. */
export const integrationsOf = (client: { status: { probe: (endpoint: string) => Promise<ProbeAnswer>, url: (endpoint: string) => string } }): Promise<Integration[]> => {
  const cached = probedClients.get(client)
  if (cached) return cached
  const probed = findIntegrations(
    (endpoint) => client.status.probe(endpoint),
    (endpoint) => client.status.url(endpoint)
  )
  probedClients.set(client, probed)
  return probed
}
