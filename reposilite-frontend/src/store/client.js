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

// A 401 means the credentials were rejected. Every other failure passes through untouched,
// and the error is always rethrown so that each caller still sees its own failure.
const handleResponseError = (error) => {
  if (error?.response?.status === 401) {
    onUnauthorized(error)
  }
  return Promise.reject(error)
}

axios.interceptors.response.use(response => response, handleResponseError)

// Read per call rather than once at import. This module is imported while the settings
// are still being fetched, so capturing the base url here would freeze the fallback in
// place and break any deployment mounted under a sub path.
const createURL = (endpoint) =>
  usePlaceholders().baseUrl + endpoint

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

  return {
    auth: {
      me() {
        return get("/api/auth/me")
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
      // Anonymous on purpose: an integration is probed the way a stranger would reach it, and a
      // 401 or 404 is an answer rather than an error. The reply is cut down to what the probe reads.
      probe(endpoint) {
        return axios.get(createURL(endpoint), { validateStatus: () => true, responseType: "text" })
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
  }
}

export {
  createURL,
  createClient,
  setUnauthorizedHandler,
  handleResponseError,
  etagOf,
  isPreconditionFailed
}
