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

// The settings of the dashboard server. They are read once, when the process starts, and a value the
// server cannot use stops the start with a message that names the variable.

const DEFAULT_BACKEND = 'http://ingot-backend:8080'
const DEFAULT_BASE_PATH = '/'
const DEFAULT_MAX_UPLOAD_SIZE = '1024m'

const UNITS = { '': 1, k: 1024, m: 1024 ** 2, g: 1024 ** 3 }

export class DashboardConfigError extends Error {
  constructor(variable, reason) {
    super(`${variable}: ${reason}`)
    this.name = 'DashboardConfigError'
    this.variable = variable
  }
}

// A size such as `1024m`, `512k` or a plain byte count. Zero means no limit, as it does for nginx.
// Returns null for anything else.
export function parseByteSize(value) {
  const match = /^(\d+)([kmg]?)b?$/i.exec(String(value).trim())
  if (!match) {
    return null
  }
  const count = Number(match[1])
  if (count === 0) {
    return Infinity
  }
  return count * UNITS[match[2].toLowerCase()]
}

// `env` is a plain map, so the caller decides where the values come from. An empty value counts as not set.
export function readDashboardConfig(env = {}) {
  const warnings = []

  const backend = normalizeBackend(env.INGOT_BACKEND || DEFAULT_BACKEND)

  const basePath = env.INGOT_BASE_PATH || DEFAULT_BASE_PATH
  if (!basePath.startsWith('/')) {
    throw new DashboardConfigError('INGOT_BASE_PATH', `must start with '/', got '${basePath}'`)
  }

  const uploadText = env.INGOT_MAX_UPLOAD_SIZE || DEFAULT_MAX_UPLOAD_SIZE
  const maxUploadBytes = parseByteSize(uploadText)
  if (maxUploadBytes === null) {
    throw new DashboardConfigError('INGOT_MAX_UPLOAD_SIZE', `cannot be read as a size, got '${uploadText}' (use bytes, or a suffix k, m or g)`)
  }

  if (env.INGOT_RESOLVER) {
    warnings.push(
      'INGOT_RESOLVER is set and ignored: the dashboard server resolves the backend with the resolver of the container or cluster'
    )
  }

  return {
    backend,
    basePath: basePath.endsWith('/') ? basePath : `${basePath}/`,
    maxUploadBytes,
    warnings,
  }
}

function normalizeBackend(value) {
  let url
  try {
    url = new URL(value)
  } catch {
    throw new DashboardConfigError('INGOT_BACKEND', `must be an absolute http or https URL, got '${value}'`)
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new DashboardConfigError('INGOT_BACKEND', `must be an absolute http or https URL, got '${value}'`)
  }
  return url.href.replace(/\/$/, '')
}
