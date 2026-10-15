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
 * Reading the health endpoint and the optional integrations of the instance.
 *
 * An answer is `{ status, data }`, or `null` when no answer arrived at all. The health endpoint
 * answers 503 with a body when the instance is down, and that body is an answer like any other.
 */

/** Turns a request that may have been rejected with a status into an answer, or null without one. */
export const toAnswer = (request) =>
  request.then(
    response => ({ status: response.status, data: response.data }),
    error => error.response
      ? { status: error.response.status, data: error.response.data }
      : null
  )

const detailLines = (details) => {
  if (typeof details === 'string') {
    return [details]
  }
  if (details && typeof details === 'object') {
    return Object.entries(details).map(([key, value]) => `${key}: ${value}`)
  }
  return []
}

/** What the health indicator shows for an answer. */
export const describeHealth = (answer) => {
  if (!answer) {
    return { state: 'unknown', label: 'Health unavailable', details: [] }
  }

  const up = answer.data?.status ? answer.data.status === 'UP' : answer.status === 200
  return up
    ? { state: 'up', label: 'Healthy', details: [] }
    : { state: 'down', label: 'Unhealthy', details: detailLines(answer.data?.details) }
}

const isHtml = (contentType) =>
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
 * it, and the dashboard cannot know that). The endpoint is protected, so an anonymous probe
 * answers 401, which still means the endpoint is there.
 */
export const INTEGRATIONS = [
  {
    label: 'API docs',
    endpoint: '/swagger',
    responds: (answer) => answer.status === 200 && isHtml(answer.contentType) && answer.body.includes('swagger-ui')
  },
  {
    label: 'Metrics',
    endpoint: '/metrics',
    responds: (answer) => (answer.status === 200 || answer.status === 401) && !isHtml(answer.contentType)
  }
]

/**
 * The integrations that respond, with their absolute links. A probe that throws counts as
 * "not there": the integrations are optional and their absence is nothing to report.
 */
export const findIntegrations = async (probe, url) => {
  const found = await Promise.all(INTEGRATIONS.map(async ({ label, endpoint, responds }) => {
    try {
      const answer = await probe(endpoint)
      return responds({ body: '', ...answer }) ? { label, href: url(endpoint) } : null
    } catch {
      return null
    }
  }))
  return found.filter(Boolean)
}
