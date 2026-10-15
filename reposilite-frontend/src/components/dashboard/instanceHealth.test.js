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
import { describeHealth, findIntegrations, toAnswer } from './instanceHealth'

describe('describeHealth', () => {
  it('reports an instance that answers UP as healthy', () => {
    const health = describeHealth({ status: 200, data: { status: 'UP' } })

    expect(health.state).toBe('up')
    expect(health.label).toBe('Healthy')
  })

  it('reports an instance that answers DOWN as unhealthy, with the details it sends', () => {
    const health = describeHealth({
      status: 503,
      data: { status: 'DOWN', details: 'database unreachable' }
    })

    expect(health.state).toBe('down')
    expect(health.label).toBe('Unhealthy')
    expect(health.details).toEqual(['database unreachable'])
  })

  it('lists every entry of structured details as a line of its own', () => {
    const health = describeHealth({
      status: 503,
      data: { status: 'DOWN', details: { storage: 'offline', config: 'missing' } }
    })

    expect(health.details).toEqual(['storage: offline', 'config: missing'])
  })

  it('reports unknown health when no answer arrived at all', () => {
    const health = describeHealth(null)

    expect(health.state).toBe('unknown')
    expect(health.details).toEqual([])
  })
})

describe('toAnswer', () => {
  it('keeps the status and body of an answered request, even when it failed with a status', async () => {
    const failed = Object.assign(new Error('Request failed'), {
      response: { status: 503, data: { status: 'DOWN' } }
    })

    const answer = await toAnswer(Promise.reject(failed))

    expect(answer).toEqual({ status: 503, data: { status: 'DOWN' } })
  })

  it('returns no answer when the request never reached the server', async () => {
    const answer = await toAnswer(Promise.reject(new Error('Network Error')))

    expect(answer).toBeNull()
  })
})

describe('findIntegrations', () => {
  const url = (endpoint) => `http://test${endpoint}`
  const SWAGGER_PAGE = '<!doctype html><html><body><div id="swagger-ui"></div></body></html>'
  const DASHBOARD_PAGE = '<!doctype html><html><body><div id="app"></div></body></html>'

  it('lists the API docs and metrics that answer, with their absolute links', async () => {
    const probe = (endpoint) => Promise.resolve(
      endpoint === '/swagger'
        ? { status: 200, contentType: 'text/html; charset=utf-8', body: SWAGGER_PAGE }
        : { status: 200, contentType: 'text/plain', body: '# TYPE jvm_threads gauge' }
    )

    const integrations = await findIntegrations(probe, url)

    expect(integrations).toEqual([
      { label: 'API docs', href: 'http://test/swagger' },
      { label: 'Metrics', href: 'http://test/metrics' }
    ])
  })

  it('leaves out an integration that is not there', async () => {
    const probe = (endpoint) => Promise.resolve(
      endpoint === '/swagger'
        ? { status: 404, contentType: 'application/json', body: '{}' }
        : { status: 200, contentType: 'text/plain', body: '# TYPE jvm_threads gauge' }
    )

    const integrations = await findIntegrations(probe, url)

    expect(integrations.map(it => it.label)).toEqual(['Metrics'])
  })

  it('treats the metrics endpoint as present when it asks for credentials', async () => {
    const probe = (endpoint) => Promise.resolve(
      endpoint === '/metrics'
        ? { status: 401, contentType: 'application/json', body: '{}' }
        : { status: 404, contentType: 'application/json', body: '{}' }
    )

    const integrations = await findIntegrations(probe, url)

    expect(integrations.map(it => it.label)).toEqual(['Metrics'])
  })

  it('does not take the dashboard served for an unknown path as the API docs', async () => {
    // A server that answers unknown paths with its own page must not earn an API docs link
    // for a plugin it does not have. The docs are only counted when the Swagger UI markup is there.
    const probe = () => Promise.resolve({ status: 200, contentType: 'text/html', body: DASHBOARD_PAGE })

    const integrations = await findIntegrations(probe, url)

    expect(integrations.map(it => it.label)).not.toContain('API docs')
  })

  it('does not take an HTML page as metrics', async () => {
    const probe = () => Promise.resolve({ status: 200, contentType: 'text/html', body: DASHBOARD_PAGE })

    const integrations = await findIntegrations(probe, url)

    expect(integrations.map(it => it.label)).not.toContain('Metrics')
  })

  it('reports nothing instead of failing when a probe cannot reach the server', async () => {
    const probe = () => Promise.reject(new Error('Network Error'))

    const integrations = await findIntegrations(probe, url)

    expect(integrations).toEqual([])
  })
})
