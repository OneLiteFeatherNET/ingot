/*
 * Copyright (c) 2023 dzikoysk
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

import axios from "axios"
import usePlaceholders from "./placeholders"

// Told once that the server rejected the credentials of a request. The session registers
// itself here, because this module cannot import the session without a cycle.
let onUnauthorized = () => {}

const setUnauthorizedHandler = (handler) => {
  onUnauthorized = handler
}

// Cookie mode: the browser holds the session in HttpOnly cookies, so requests carry them
// (withCredentials) and unsafe ones echo the readable CSRF cookie in a header. The session
// switches this on after a cookie sign in and off when the session ends. Nothing here is
// ever stored: the CSRF value is read from document.cookie on every request.
let cookieMode = false
const CSRF_COOKIE = "ingot_csrf"
const CSRF_HEADER = "X-CSRF-Token"
const UNSAFE_METHODS = ["POST", "PUT", "PATCH", "DELETE"]

const setCookieMode = (enabled) => {
  cookieMode = enabled === true
}

const readCsrfToken = (cookies = typeof document === "undefined" ? "" : document.cookie) => {
  for (const part of String(cookies || "").split(";")) {
    const separator = part.indexOf("=")
    if (separator !== -1 && part.slice(0, separator).trim() === CSRF_COOKIE) {
      return decodeURIComponent(part.slice(separator + 1).trim())
    }
  }
  return null
}

// A request that asked for credentials explicitly (the sign in calls) gets the same treatment
// as one made in cookie mode, so the very first cookie request does not depend on the mode flag.
const handleRequest = (config) => {
  if (!cookieMode && !config.withCredentials) {
    return config
  }
  config.withCredentials = true
  const csrf = readCsrfToken()
  if (csrf && UNSAFE_METHODS.includes(String(config.method || "get").toUpperCase())) {
    if (typeof config.headers?.set === "function") {
      config.headers.set(CSRF_HEADER, csrf)
    } else {
      config.headers = { ...config.headers, [CSRF_HEADER]: csrf }
    }
  }
  return config
}

// Answers to these are answers about the sign in itself, not about a session that expired, so
// they neither trigger a refresh nor the unauthorized handler.
const AUTH_ENDPOINTS = ["/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/setup", "/api/auth/providers"]
const isAuthEndpoint = (url) =>
  AUTH_ENDPOINTS.some(endpoint => String(url || "").includes(endpoint))

// Read per call rather than once at import. This module is imported while the settings
// are still being fetched, so capturing the base url here would freeze the fallback in
// place and break any deployment mounted under a sub path.
const createURL = (endpoint) =>
  usePlaceholders().baseUrl + endpoint

// Concurrent rejections share one refresh call.
let refreshing = null
const refreshSession = () => {
  if (!refreshing) {
    refreshing = axios
      .post(createURL("/api/auth/refresh"), {}, { withCredentials: true })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

// A 401 means the credentials were rejected. In cookie mode the session is refreshed once and
// the request retried once; only when that fails does the unauthorized handler run. Every other
// failure passes through untouched, and the error is always rethrown so that each caller still
// sees its own failure.
const handleResponseError = async (error) => {
  if (error?.response?.status !== 401) {
    return Promise.reject(error)
  }
  const config = error.config
  if (isAuthEndpoint(config?.url)) {
    return Promise.reject(error)
  }
  if (cookieMode && config && !config._retried) {
    config._retried = true
    let refreshed = true
    try {
      await refreshSession()
    } catch {
      refreshed = false
    }
    if (refreshed) {
      // A second 401 comes back through this handler with _retried set and ends the session.
      return axios.request(config)
    }
  }
  onUnauthorized(error)
  return Promise.reject(error)
}

axios.interceptors.request.use(handleRequest)
axios.interceptors.response.use(response => response, handleResponseError)

/* The entity tag of a response. Null when the server sent none, and the next write is then unconditional. */
const etagOf = (response) =>
  response?.headers?.etag ?? null

/* The 412 the server answers when the stored state changed since this client read it. */
const isPreconditionFailed = (error) =>
  error?.response?.status === 412

const createClient = (defaultName, defaultSecret) => {
  const defaultAuthorization = () =>
    defaultName && defaultSecret
      ? authorization(defaultName, defaultSecret)
      : {}

  const authorization = (name, secret) => ({
    headers: {
      Authorization: `xBasic ${btoa(`${name}:${secret}`)}`,
    },
  })

  // The precondition of an optimistic write. Without a known tag no header is sent, which is what an older server expects.
  const ifMatch = (etag) =>
    etag ? { "If-Match": etag } : {}

  const get = (endpoint, credentials) =>
    axios.get(createURL(endpoint), {
      ...(credentials || defaultAuthorization()),
    })

  /*
   * The token endpoints of one principal, in the shape the identity components take as their
   * `api` prop: { list, create, revoke } and, where the server has the endpoint, rotate. The
   * calls return the axios promise, so a caller reads `response.data`.
   */
  const tokenEndpoints = (base, { rotate = false } = {}) => {
    const api = {
      list() {
        return get(base)
      },
      create(body) {
        return axios.post(createURL(base), body, { ...defaultAuthorization() })
      },
      revoke(name) {
        return axios.delete(createURL(`${base}/${encodeURIComponent(name)}`), { ...defaultAuthorization() })
      },
    }
    if (rotate) {
      api.rotate = (name) =>
        axios.post(createURL(`${base}/${encodeURIComponent(name)}/rotate`), {}, { ...defaultAuthorization() })
    }
    return api
  }

  return {
    auth: {
      me() {
        return get("/api/auth/me")
      },
      // The same question asked with the browser's cookies, for a session that is not known yet.
      meWithCookie() {
        return axios.get(createURL("/api/auth/me"), { withCredentials: true })
      },
      providers() {
        return axios.get(createURL("/api/auth/providers"), { withCredentials: true })
      },
      login({ username, password }) {
        return axios.post(createURL("/api/auth/login"), { provider: "local", username, password }, { withCredentials: true })
      },
      setup({ setupCode, username, password, displayName }) {
        const body = { setupCode, username, password }
        if (displayName) body.displayName = displayName
        return axios.post(createURL("/api/auth/setup"), body, { withCredentials: true })
      },
      refresh() {
        return refreshSession()
      },
      logout() {
        return axios.post(createURL("/api/auth/logout"), {}, { withCredentials: true })
      },
    },
    identity: {
      // The signed in principal looking after itself.
      me: {
        changePassword({ currentPassword, newPassword }) {
          return axios.post(
            createURL("/api/identity/me/password"),
            { currentPassword, newPassword },
            { ...defaultAuthorization() }
          )
        },
        tokens: tokenEndpoints("/api/identity/me/tokens", { rotate: true }),
      },
      // The tokens of any principal, for an instance admin. The server has no rotate endpoint here.
      principalTokens(principalName) {
        return tokenEndpoints(`/api/identity/principals/${encodeURIComponent(principalName)}/tokens`)
      },
      // The principals, for an instance admin. get() resolves with the ETag header, which a later
      // update() sends back as If-Match; a missing tag means an unconditional write.
      principals: {
        list(kind) {
          return get(`/api/identity/principals${kind ? `?kind=${encodeURIComponent(kind)}` : ""}`)
        },
        get(name) {
          return get(`/api/identity/principals/${encodeURIComponent(name)}`)
        },
        create(body) {
          return axios.post(createURL("/api/identity/principals"), body, { ...defaultAuthorization() })
        },
        update(name, body, etag) {
          return axios.patch(createURL(`/api/identity/principals/${encodeURIComponent(name)}`), body, {
            headers: {
              "Content-Type": "application/json",
              ...ifMatch(etag),
              ...defaultAuthorization().headers,
            },
          })
        },
        remove(name) {
          return axios.delete(createURL(`/api/identity/principals/${encodeURIComponent(name)}`), {
            ...defaultAuthorization(),
          })
        },
        // 204. The principal must choose a new password at the next sign in.
        setPassword(name, password) {
          return axios.post(
            createURL(`/api/identity/principals/${encodeURIComponent(name)}/password`),
            { password },
            { ...defaultAuthorization() }
          )
        },
        grantInstanceAdmin(name) {
          return axios.put(
            createURL(`/api/identity/principals/${encodeURIComponent(name)}/instance-admin`),
            {},
            { ...defaultAuthorization() }
          )
        },
        revokeInstanceAdmin(name) {
          return axios.delete(createURL(`/api/identity/principals/${encodeURIComponent(name)}/instance-admin`), {
            ...defaultAuthorization(),
          })
        },
      },
    },
    console: {
      execute(command) {
        return axios.post(
            createURL(`/api/console/execute`), command, {
              headers: {
                ...defaultAuthorization().headers,
              }
            }
        )
      }
    },
    status: {
      instance() {
        return get("/api/status/instance")
      },
      snapshots() {
        return get("/api/status/snapshots")
      },
      failures() {
        return get("/api/status/failures")
      },
      clearFailures() {
        return axios.delete(createURL("/api/status/failures"), {
          ...defaultAuthorization(),
        })
      },
      health() {
        return get("/api/status/health")
      },
      // Sent with the session's credentials, like every other call of this client, so a protected
      // integration answers 200 instead of a 401 the browser logs on every visit. A 401, 403 or 404
      // is still an answer rather than an error. The reply is cut down to what the probe reads.
      probe(endpoint) {
        return axios.get(createURL(endpoint), {
          ...defaultAuthorization(),
          validateStatus: () => true,
          responseType: "text",
        })
          .then(response => ({
            status: response.status,
            contentType: response.headers?.["content-type"] ?? "",
            body: typeof response.data === "string" ? response.data.slice(0, 16384) : "",
          }))
      },
      url(endpoint) {
        return createURL(endpoint)
      },
    },
    statistics: {
      allResolved() {
        return get("/api/statistics/resolved/all")
      }
    },
    maven: {
      /*
       * The repositories the caller may see. `q` searches id, description and tags; each tag is a
       * repeated parameter and all of them must match. Both are optional.
       */
      repositories({ q, tags = [] } = {}) {
        const params = new URLSearchParams()
        if (q) params.set("q", q)
        for (const tag of tags) params.append("tag", tag)
        const query = params.toString()
        return get(`/api/maven/repositories${query ? `?${query}` : ""}`)
      },
      content(gav) {
        return get(`/${gav}`)
      },
      details(gav) {
        return get(`/api/maven/details/${gav || ""}`)
      },
      download(gav) {
        return get(`/${gav}`, {
          responseType: "blob",
          ...defaultAuthorization(),
        })
      },
      deploy(gav, file, generateChecksums) {
        return axios.put(createURL(`/${gav}`), file, {
          headers: {
            "Content-Type": "application/octet-stream",
            "X-Generate-Checksums": generateChecksums,
            ...defaultAuthorization().headers,
          }
        })
      },
      generatePom(gav, groupId, artifactId, version) {
        return axios.post(
          createURL(`/api/maven/generate/pom/${gav}/${artifactId}-${version}.pom`),
          {
            groupId,
            artifactId,
            version
          }, {
            headers: {
              "Content-Type": "application/json",
              ...defaultAuthorization().headers,
            }
          }
        )
      },
      delete(gav) {
        return axios.delete(createURL(`/${gav}`), {
          ...defaultAuthorization(),
        })
      },
      storage() {
        return get("/api/maven/storage")
      },
      refreshStorage() {
        return axios.post(createURL("/api/maven/storage/refresh"), {}, {
          ...defaultAuthorization(),
        })
      }
    },
    cleanup: {
      dryRun(policyId) {
        return axios.post(createURL(`/api/cleanup/policies/${encodeURIComponent(policyId)}/dry-run`), {}, {
          ...defaultAuthorization(),
        })
      },
      run(policyId) {
        return axios.post(createURL(`/api/cleanup/policies/${encodeURIComponent(policyId)}/run`), {}, {
          ...defaultAuthorization(),
        })
      },
      runs(policyId) {
        const query = policyId ? `?policy=${encodeURIComponent(policyId)}` : ""
        return get(`/api/cleanup/runs${query}`)
      }
    },
    settings: {
      domains() {
        return axios.get(createURL("/api/settings/domains"), {
          headers: {
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      schema(name) {
        return axios.get(createURL(`/api/settings/schema/${name}`), {
          headers: {
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      fetch(name) {
        return axios.get(createURL(`/api/settings/domain/${name}`), {
          headers: {
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      update(name, content, etag) {
        return axios.put(createURL(`/api/settings/domain/${name}`), content, {
          headers: {
            "Content-Type": "application/json",
            Accepts: "application/json",
            ...ifMatch(etag),
            ...defaultAuthorization().headers,
          },
        })
      },
    },
    tokens: {
      list() {
        return get("/api/tokens")
      },
      get(name) {
        return get(`/api/tokens/${encodeURIComponent(name)}`)
      },
      createOrUpdate(name, request, etag) {
        return axios.put(createURL(`/api/tokens/${encodeURIComponent(name)}`), request, {
          headers: {
            "Content-Type": "application/json",
            ...ifMatch(etag),
            ...defaultAuthorization().headers,
          },
        })
      },
      update(name, request, etag) {
        return axios.patch(createURL(`/api/tokens/${encodeURIComponent(name)}`), request, {
          headers: {
            "Content-Type": "application/json",
            ...ifMatch(etag),
            ...defaultAuthorization().headers,
          },
        })
      },
      delete(name) {
        return axios.delete(createURL(`/api/tokens/${encodeURIComponent(name)}`), {
          ...defaultAuthorization(),
        })
      },
      regenerateSecret(name) {
        return axios.post(createURL(`/api/tokens/${encodeURIComponent(name)}/secret`), {}, {
          ...defaultAuthorization(),
        })
      },
    },
    audit: {
      list(params) {
        return get(`/api/audit${queryString(params)}`)
      },
      exportCsv(params) {
        return axios.get(createURL(`/api/audit/export${queryString(params)}`), {
          responseType: "blob",
          ...defaultAuthorization(),
        })
      },
    },
  }
}

// Empty parameters are left out, so an unfiltered call has no question mark at all.
const queryString = (params = {}) => {
  const search = new URLSearchParams(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== "")
  ).toString()
  return search ? `?${search}` : ""
}

export {
  createURL,
  createClient,
  setUnauthorizedHandler,
  setCookieMode,
  readCsrfToken,
  handleRequest,
  handleResponseError,
  etagOf,
  isPreconditionFailed
}
