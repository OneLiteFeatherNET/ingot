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
import type {
  AxiosError,
  AxiosHeaders,
  AxiosHeaderValue,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios"
import usePlaceholders from "./placeholders"
import type { components } from "../types/api"

// The request config with the flags this module sets on its own retried and checked requests.
type SessionRequestConfig = AxiosRequestConfig & {
  _retried?: boolean
  _sessionAlive?: boolean
  _sessionCheck?: boolean
}

// The optional query values of a list call. Empty ones are left out of the query string.
type QueryParams = Record<string, string | number | boolean | null | undefined>

// The second factor methods a sign in can be completed with, as the login answer names them on the wire.
export type SecondFactorMethod = "AUTHENTICATOR" | "RECOVERY_CODE" | "PASSKEY"

// The answer of a password sign in for a user with a second factor. It starts no session: the challenge cookie is set instead.
export interface SecondFactorChallenge {
  mfaRequired: boolean
  methods: SecondFactorMethod[]
}

// The answer of GET /api/identity/me/security. The OpenAPI document has no schema for it, so the shape follows the server's DTO.
export interface SecurityStatus {
  factorCounts: { authenticator: number; passkey: number }
  recoveryCodesLeft: number
  policy: { covered: boolean; requiredForAdmins: boolean; requiredForTeams: string[] }
  graceUntil?: string | null
  scope: "full" | "enrol"
  stepUpUntil?: string | null
}

// A passkey assertion as the sign in and the step-up read it: the challenge id the options gave, and the browser's answer in base64url.
type PasskeyAssertion = components["schemas"]["PasskeyAssertionDto"]

// The token endpoints of one principal. rotate exists only where the server has the endpoint.
export interface TokenEndpoints {
  list(): Promise<AxiosResponse>
  create(body: unknown): Promise<AxiosResponse>
  revoke(name: string): Promise<AxiosResponse>
  rotate?: (name: string) => Promise<AxiosResponse>
}

// Told once that the server rejected the credentials of a request. The session registers
// itself here, because this module cannot import the session without a cycle.
let onUnauthorized: (error: unknown) => void = () => {}

const setUnauthorizedHandler = (handler: (error: unknown) => void) => {
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

const setCookieMode = (enabled: unknown) => {
  cookieMode = enabled === true
}

const readCsrfToken = (cookies: string = typeof document === "undefined" ? "" : document.cookie): string | null => {
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
const handleRequest = (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
  if (!cookieMode && !config.withCredentials) {
    return config
  }
  config.withCredentials = true
  const csrf = readCsrfToken()
  if (csrf && UNSAFE_METHODS.includes(String(config.method || "get").toUpperCase())) {
    if (typeof config.headers?.set === "function") {
      config.headers.set(CSRF_HEADER, csrf)
    } else {
      config.headers = Object.assign({}, config.headers, { [CSRF_HEADER]: csrf })
    }
  }
  return config
}

// Answers to these are answers about the sign in itself, not about a session that expired, so
// they neither trigger a refresh nor the unauthorized handler.
// The second step and the passkey sign in are answers about a pending sign in too: a wrong code is a 401 that must not end a session.
const AUTH_ENDPOINTS = [
  "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/auth/setup", "/api/auth/providers",
  "/api/auth/mfa/", "/api/auth/passkey/",
]
const isAuthEndpoint = (url: string | undefined): boolean =>
  AUTH_ENDPOINTS.some(endpoint => String(url || "").includes(endpoint))

// Read per call rather than once at import. This module is imported while the settings
// are still being fetched, so capturing the base url here would freeze the fallback in
// place and break any deployment mounted under a sub path.
const createURL = (endpoint: string): string =>
  usePlaceholders().baseUrl + endpoint

// Concurrent rejections share one refresh call.
let refreshing: Promise<AxiosResponse> | null = null
const refreshSession = (): Promise<AxiosResponse> => {
  if (!refreshing) {
    refreshing = axios
      .post(createURL("/api/auth/refresh"), {}, { withCredentials: true })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

// The value of one request header, read from a plain object or from AxiosHeaders.
const headerOf = (config: SessionRequestConfig | undefined, name: string): AxiosHeaderValue | undefined => {
  const headers = config?.headers as AxiosHeaders | Record<string, AxiosHeaderValue> | undefined
  const plain: Record<string, AxiosHeaderValue> | undefined = headers
  return typeof (headers as AxiosHeaders | undefined)?.get === 'function'
    ? (headers as AxiosHeaders).get(name)
    : plain?.[name]
}

// A 401 on a request does not by itself end the session: the server may be refusing that one request
// (a group whose private members refuse the mirror, or a listing the user may not read). The session
// is asked about with the same credentials, and only a 401 from that check ends it. A request without
// credentials of its own has no session to check, so the unauthorized handler decides as it always did.
const sessionRejected = (config: SessionRequestConfig | undefined): Promise<boolean> => {
  const authorization = headerOf(config, 'Authorization')
  if (!authorization) {
    return Promise.resolve(true)
  }
  const check: SessionRequestConfig = {
    withCredentials: true,
    headers: { Authorization: authorization },
    _sessionCheck: true,
  }
  return axios
    .get(createURL('/api/auth/me'), check)
    .then(
      () => false,
      (error: AxiosError | undefined) => error?.response?.status === 401
    )
}

// A 401 means the credentials were rejected. In cookie mode the session is refreshed once and the
// request retried once. A refresh that is refused ends the session. A retry that is refused after a
// successful refresh says nothing about the session, because the refresh just proved it stands. In
// token mode the session check decides. Every failure is rethrown so that each caller still sees its own.
const handleResponseError = async (error: AxiosError): Promise<AxiosResponse> => {
  if (error?.response?.status !== 401) {
    return Promise.reject(error)
  }
  const config = error.config as SessionRequestConfig | undefined
  if (isAuthEndpoint(config?.url) || config?._sessionCheck) {
    return Promise.reject(error)
  }
  if (config?._sessionAlive) {
    return Promise.reject(error)
  }
  if (cookieMode && config && !config._retried) {
    config._retried = true
    try {
      await refreshSession()
    } catch {
      onUnauthorized(error)
      return Promise.reject(error)
    }
    const retry: SessionRequestConfig = { ...config, _sessionAlive: true }
    return axios.request(retry)
  }
  return sessionRejected(config).then(rejected => {
    if (rejected) {
      onUnauthorized(error)
    }
    return Promise.reject(error)
  })
}

axios.interceptors.request.use(handleRequest)
axios.interceptors.response.use(response => response, handleResponseError)

/* The entity tag of a response. Null when the server sent none, and the next write is then unconditional. */
const etagOf = (response: AxiosResponse | undefined): string | null =>
  typeof response?.headers?.etag === 'string' ? response.headers.etag : null

/* The 412 the server answers when the stored state changed since this client read it. */
const isPreconditionFailed = (error: AxiosError | undefined): boolean =>
  error?.response?.status === 412

// The second factor problems the dashboard explains with a next step. The server names each one in the `message`
// of its error answer: step_up_required and mfa_enrolment_required as a 403, and the authenticator start as a 409
// whose message is the reason the server gives (for example a missing settings key).
export type SecondFactorProblem = "step_up_required" | "mfa_enrolment_required" | "authenticator_enrolment_unavailable"

export interface SecondFactorProblemInfo {
  problem: SecondFactorProblem
  message: string
  // The methods a step-up can be confirmed with, as the server listed them. Empty for the other problems.
  methods: SecondFactorMethod[]
}

const SECOND_FACTOR_METHOD_NAMES: readonly string[] = ["AUTHENTICATOR", "RECOVERY_CODE", "PASSKEY"]
const AUTHENTICATOR_START_PATH = "/api/identity/me/factors/totp"
const STEP_UP_MESSAGE = "Confirm with a second factor first, then try again."
const ENROLMENT_REQUIRED_MESSAGE = "Your account needs a second factor before it can do this. Add one on the account page first."
const AUTHENTICATOR_UNAVAILABLE_MESSAGE = "Authenticator apps are not available on this server yet."

// The mapped problem of a refused call, or null for every other answer. The caller shows the message and
// keeps every other error on the server's own text.
export const secondFactorProblemOf = (error: unknown): SecondFactorProblemInfo | null => {
  const refused = error as AxiosError | undefined
  const status = refused?.response?.status
  const data = refused?.response?.data as { message?: unknown; problems?: unknown } | undefined
  if (typeof status !== "number" || !data) {
    return null
  }
  const code = typeof data.message === "string" ? data.message : ""

  if (status === 403 && code === "step_up_required") {
    const problems = Array.isArray(data.problems) ? (data.problems as Array<{ node?: unknown }>) : []
    const methods = problems
      .map(problem => problem?.node)
      .filter((node): node is SecondFactorMethod => typeof node === "string" && SECOND_FACTOR_METHOD_NAMES.includes(node))
    return { problem: "step_up_required", message: STEP_UP_MESSAGE, methods }
  }
  if (status === 403 && code === "mfa_enrolment_required") {
    return { problem: "mfa_enrolment_required", message: ENROLMENT_REQUIRED_MESSAGE, methods: [] }
  }
  if (status === 409 && String(refused?.config?.url || "").endsWith(AUTHENTICATOR_START_PATH)) {
    return {
      problem: "authenticator_enrolment_unavailable",
      message: code || AUTHENTICATOR_UNAVAILABLE_MESSAGE,
      methods: [],
    }
  }
  return null
}

// The request config that carries an access token, or nothing for a cookie session. Requests made outside
// createClient (the plugin manifest, the support availability) use it too, so a token session sends its token.
const tokenCredentials = (
  name: string | null | undefined,
  secret: string | null | undefined
): { headers?: { Authorization: string } } =>
  name && secret
    ? { headers: { Authorization: `xBasic ${btoa(`${name}:${secret}`)}` } }
    : {}

const createClient = (defaultName: string | null | undefined, defaultSecret: string | null | undefined) => {
  const defaultAuthorization = () => tokenCredentials(defaultName, defaultSecret)

  // The precondition of an optimistic write. Without a known tag no header is sent, which is what an older server expects.
  const ifMatch = (etag: string | null | undefined): Record<string, string> =>
    etag ? { "If-Match": etag } : {}

  // The request options of a write that carries the entity tag it was based on.
  const conditional = (etag: string | null | undefined) => ({
    headers: {
      "Content-Type": "application/json",
      ...ifMatch(etag),
      ...defaultAuthorization().headers,
    },
  })

  const get = (endpoint: string, credentials?: AxiosRequestConfig) =>
    axios.get(createURL(endpoint), {
      ...(credentials || defaultAuthorization()),
    })

  /*
   * The token endpoints of one principal, in the shape the identity components take as their
   * `api` prop: { list, create, revoke } and, where the server has the endpoint, rotate. The
   * calls return the axios promise, so a caller reads `response.data`.
   */
  const tokenEndpoints = (base: string, { rotate = false }: { rotate?: boolean } = {}): TokenEndpoints => {
    const api: TokenEndpoints = {
      list() {
        return get(base)
      },
      create(body: unknown) {
        return axios.post(createURL(base), body, { ...defaultAuthorization() })
      },
      revoke(name: string) {
        return axios.delete(createURL(`${base}/${encodeURIComponent(name)}`), { ...defaultAuthorization() })
      },
    }
    if (rotate) {
      api.rotate = (name: string) =>
        axios.post(createURL(`${base}/${encodeURIComponent(name)}/rotate`), {}, { ...defaultAuthorization() })
    }
    return api
  }

  return {
    auth: {
      me() {
        return axios.get<components["schemas"]["SessionDetails"]>(createURL("/api/auth/me"), { ...defaultAuthorization() })
      },
      // The same question asked with the browser's cookies, for a session that is not known yet.
      meWithCookie() {
        return axios.get<components["schemas"]["SessionDetails"]>(createURL("/api/auth/me"), { withCredentials: true })
      },
      providers() {
        return axios.get<components["schemas"]["LoginProvidersResponse"]>(createURL("/api/auth/providers"), { withCredentials: true })
      },
      // The provider is the id from GET /api/auth/providers: "local" for the Ingot account, "ldap" for
      // the directory. The server rejects a provider it does not know, so the default is the local one.
      login({ provider = "local", username, password }: { provider?: string; username: string; password: string }) {
        return axios.post<components["schemas"]["LoginResponse"] | SecondFactorChallenge>(createURL("/api/auth/login"), { provider, username, password }, { withCredentials: true })
      },
      setup({ setupCode, username, password, displayName }: {
        setupCode: string
        username: string
        password: string
        displayName?: string
      }) {
        const body: { setupCode: string; username: string; password: string; displayName?: string } = { setupCode, username, password }
        if (displayName) body.displayName = displayName
        return axios.post<components["schemas"]["LoginResponse"]>(createURL("/api/auth/setup"), body, { withCredentials: true })
      },
      refresh() {
        return refreshSession()
      },
      logout() {
        return axios.post(createURL("/api/auth/logout"), {}, { withCredentials: true })
      },
      // Tests the saved configuration of a single sign on provider (not an unsaved draft).
      // Resolves with { ok, issuer, endpoints, error? }.
      testOidcProvider(id: string) {
        return axios.post(createURL(`/api/auth/providers/oidc/${encodeURIComponent(id)}/test`), {}, { ...defaultAuthorization() })
      },
      // Tests the saved LDAP configuration, optionally with a user to look up. Resolves with
      // { ok, userDn?, groups?, error? }.
      testLdapProvider(body: unknown = {}) {
        return axios.post(createURL("/api/auth/providers/ldap/test"), body, { ...defaultAuthorization() })
      },
      // The second step of a password sign in. Each call needs the challenge cookie that the password answer set,
      // and a successful one starts the session, as the login does.
      verifySecondFactor(code: string) {
        return axios.post<components["schemas"]["LoginResponse"]>(createURL("/api/auth/mfa/verify"), { code }, { withCredentials: true })
      },
      verifyPasskey(passkey: PasskeyAssertion) {
        return axios.post<components["schemas"]["LoginResponse"]>(createURL("/api/auth/mfa/verify"), { passkey }, { withCredentials: true })
      },
      // The assertion options of the passkeys that may answer the pending sign in. 409 when the instance has no public address.
      passkeyChallenge() {
        return axios.post<components["schemas"]["PasskeyOptionsDto"]>(createURL("/api/auth/mfa/passkey/options"), {}, { withCredentials: true })
      },
      // A passkey as the first factor, only while login.mfa.passkeyFirstFactor is on (404 otherwise).
      firstFactorPasskeyOptions(username: string) {
        return axios.post<components["schemas"]["PasskeyOptionsDto"]>(createURL("/api/auth/passkey/options"), { username }, { withCredentials: true })
      },
      firstFactorPasskeyLogin(assertion: PasskeyAssertion) {
        return axios.post<components["schemas"]["LoginResponse"]>(createURL("/api/auth/passkey/login"), assertion, { withCredentials: true })
      },
    },
    identity: {
      // The signed in principal looking after itself.
      me: {
        changePassword({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) {
          return axios.post(
            createURL("/api/identity/me/password"),
            { currentPassword, newPassword },
            { ...defaultAuthorization() }
          )
        },
        // The caller's own access summary. It takes no principal: the server answers for the caller only.
        access() {
          return axios.get<components["schemas"]["OwnAccessSummaryDto"]>(createURL("/api/identity/me/access"), { ...defaultAuthorization() })
        },
        tokens: tokenEndpoints("/api/identity/me/tokens", { rotate: true }),
        // The second factor status of the caller: factor counts, recovery codes left, the policy, the grace end and the scope.
        security() {
          return axios.get<SecurityStatus>(createURL("/api/identity/me/security"), { ...defaultAuthorization() })
        },
        // The caller's own second factors. The seed and the codes are answered once, by the enrolment calls only.
        factors: {
          list() {
            return axios.get<components["schemas"]["SecondFactorDto"][]>(createURL("/api/identity/me/factors"), { ...defaultAuthorization() })
          },
          // Without a code the server starts the first factor. With one, it is a current code of a factor the user already has.
          startAuthenticator(code?: string) {
            return axios.post<components["schemas"]["AuthenticatorEnrolmentDto"]>(
              createURL("/api/identity/me/factors/totp"),
              code ? { code } : {},
              { ...defaultAuthorization() }
            )
          },
          confirmAuthenticator(code: string) {
            return axios.post<components["schemas"]["ConfirmedEnrolmentDto"]>(
              createURL("/api/identity/me/factors/totp/confirm"),
              { code },
              { ...defaultAuthorization() }
            )
          },
          startPasskey() {
            return axios.post<components["schemas"]["PasskeyOptionsDto"]>(createURL("/api/identity/me/factors/passkey/options"), {}, { ...defaultAuthorization() })
          },
          registerPasskey(attestation: components["schemas"]["PasskeyRegistrationDto"]) {
            return axios.post<components["schemas"]["PasskeyRegisteredDto"]>(createURL("/api/identity/me/factors/passkey"), attestation, { ...defaultAuthorization() })
          },
          rename(id: number, name: string) {
            return axios.patch<components["schemas"]["SecondFactorDto"]>(createURL(`/api/identity/me/factors/${id}`), { name }, { ...defaultAuthorization() })
          },
          // The removal takes a current code, and a DELETE carries it in the body.
          remove(id: number, code: string) {
            return axios.delete(createURL(`/api/identity/me/factors/${id}`), { ...defaultAuthorization(), data: { code } })
          },
        },
        recoveryCodes: {
          // Returns the new set once, so the caller must show it before the dialog closes.
          regenerate(code: string) {
            return axios.post<components["schemas"]["RecoveryCodesDto"]>(createURL("/api/identity/me/recovery-codes"), { code }, { ...defaultAuthorization() })
          },
        },
        // A fresh code of a second factor for the browser session, which renews the step-up window.
        stepUp(code: string) {
          return axios.post<components["schemas"]["StepUpDto"]>(createURL("/api/identity/me/step-up"), { code }, { ...defaultAuthorization() })
        },
      },
      // The tokens of any principal, for an instance admin. The server has no rotate endpoint here.
      principalTokens(principalName: string) {
        return tokenEndpoints(`/api/identity/principals/${encodeURIComponent(principalName)}/tokens`)
      },
      // The principals, for an instance admin. get() resolves with the ETag header, which a later
      // update() sends back as If-Match; a missing tag means an unconditional write.
      principals: {
        list(kind?: string) {
          return get(`/api/identity/principals${kind ? `?kind=${encodeURIComponent(kind)}` : ""}`)
        },
        get(name: string) {
          return get(`/api/identity/principals/${encodeURIComponent(name)}`)
        },
        // The second factors of one principal, for an instance administrator. The answers carry names and times, never secrets.
        factors(name: string) {
          const base = `/api/identity/principals/${encodeURIComponent(name)}/factors`
          return {
            list() {
              return axios.get<components["schemas"]["SecondFactorDto"][]>(createURL(base), { ...defaultAuthorization() })
            },
            // Removes every factor and recovery code of the principal, and ends its sessions.
            resetAll() {
              return axios.delete<components["schemas"]["FactorResetDto"]>(createURL(base), { ...defaultAuthorization() })
            },
            remove(id: number) {
              return axios.delete<components["schemas"]["FactorResetDto"]>(createURL(`${base}/${id}`), { ...defaultAuthorization() })
            },
          }
        },
        create(body: unknown) {
          return axios.post(createURL("/api/identity/principals"), body, { ...defaultAuthorization() })
        },
        update(name: string, body: unknown, etag?: string | null) {
          return axios.patch(createURL(`/api/identity/principals/${encodeURIComponent(name)}`), body, {
            headers: {
              "Content-Type": "application/json",
              ...ifMatch(etag),
              ...defaultAuthorization().headers,
            },
          })
        },
        remove(name: string) {
          return axios.delete(createURL(`/api/identity/principals/${encodeURIComponent(name)}`), {
            ...defaultAuthorization(),
          })
        },
        // 204. The principal must choose a new password at the next sign in.
        setPassword(name: string, password: string) {
          return axios.post(
            createURL(`/api/identity/principals/${encodeURIComponent(name)}/password`),
            { password },
            { ...defaultAuthorization() }
          )
        },
        grantInstanceAdmin(name: string) {
          return axios.put(
            createURL(`/api/identity/principals/${encodeURIComponent(name)}/instance-admin`),
            {},
            { ...defaultAuthorization() }
          )
        },
        revokeInstanceAdmin(name: string) {
          return axios.delete(createURL(`/api/identity/principals/${encodeURIComponent(name)}/instance-admin`), {
            ...defaultAuthorization(),
          })
        },
      },
      // The teams, for an instance admin. get() resolves with the ETag header; every write below
      // takes it back as `etag` and sends it as If-Match (no tag, no header: an unconditional write).
      teams: {
        list() {
          return get("/api/identity/teams")
        },
        get(name: string) {
          return get(`/api/identity/teams/${encodeURIComponent(name)}`)
        },
        create(body: unknown) {
          return axios.post(createURL("/api/identity/teams"), body, { ...defaultAuthorization() })
        },
        update(name: string, body: unknown, etag?: string | null) {
          return axios.patch(createURL(`/api/identity/teams/${encodeURIComponent(name)}`), body, conditional(etag))
        },
        remove(name: string) {
          return axios.delete(createURL(`/api/identity/teams/${encodeURIComponent(name)}`), {
            ...defaultAuthorization(),
          })
        },
        // Replaces the MANUAL memberships; the server keeps the SYNC rows.
        setMembers(name: string, members: unknown, etag?: string | null) {
          return axios.put(createURL(`/api/identity/teams/${encodeURIComponent(name)}/members`), { members }, conditional(etag))
        },
        // Replaces all grants: [{ repository, pathPrefix, role }].
        setGrants(name: string, grants: unknown, etag?: string | null) {
          return axios.put(createURL(`/api/identity/teams/${encodeURIComponent(name)}/grants`), { grants }, conditional(etag))
        },
        grantInstanceAdmin(name: string, etag?: string | null) {
          return axios.put(createURL(`/api/identity/teams/${encodeURIComponent(name)}/instance-admin`), {}, conditional(etag))
        },
        revokeInstanceAdmin(name: string, etag?: string | null) {
          return axios.delete(createURL(`/api/identity/teams/${encodeURIComponent(name)}/instance-admin`), conditional(etag))
        },
        // The parent teams a team inherits from. parents() resolves with the ETag header; setParents() sends it back as If-Match.
        parents(name: string) {
          return get(`/api/identity/teams/${encodeURIComponent(name)}/parents`)
        },
        setParents(name: string, parents: unknown, etag?: string | null) {
          return axios.put(createURL(`/api/identity/teams/${encodeURIComponent(name)}/parents`), { parents }, conditional(etag))
        },
      },
      // The permission nodes of a principal or a team (kind is "principals" or "teams"). get() resolves
      // with the ETag header; set() sends it back as If-Match. The catalog and the templates feed the editor.
      nodes: {
        get(kind: string, name: string) {
          return get(`/api/identity/${kind}/${encodeURIComponent(name)}/nodes`)
        },
        set(kind: string, name: string, nodes: unknown, etag?: string | null) {
          return axios.put(createURL(`/api/identity/${kind}/${encodeURIComponent(name)}/nodes`), { nodes }, conditional(etag))
        },
        catalog(prefix?: string) {
          return get(`/api/identity/nodes/catalog${prefix ? `?prefix=${encodeURIComponent(prefix)}` : ""}`)
        },
        templates() {
          return get("/api/identity/nodes/templates")
        },
      },
      // Asks whether a principal may use a node, optionally narrowed by one of its tokens. The answer
      // is { allowed, decision, trace }: the decision and every step that led to it. With repository,
      // path and action instead of a node, asks whether the principal may do the action on that path
      // of the repository; the path is sent as it was typed, and the server reads its directories.
      check({ principal, node, token, repository, path, action }: {
        principal: string
        node?: string
        token?: string
        repository?: string
        path?: string
        action?: string
      }) {
        const query = [`principal=${encodeURIComponent(principal)}`]
        if (repository !== undefined || path !== undefined) {
          query.push(`repository=${encodeURIComponent(String(repository))}`, `path=${encodeURIComponent(String(path))}`, `action=${encodeURIComponent(String(action))}`)
        } else {
          query.push(`node=${encodeURIComponent(String(node))}`)
        }
        if (token) query.push(`token=${encodeURIComponent(token)}`)
        return get(`/api/identity/check?${query.join("&")}`)
      },
      // Who can reach a repository and through which team: { repository, visibility, entries }.
      repositoryAccess(repository: string) {
        return get(`/api/identity/repositories/${encodeURIComponent(repository)}/access`)
      },
    },
    console: {
      execute(command: unknown) {
        return axios.post(
            createURL(`/api/console/execute`), command, {
              headers: {
                ...defaultAuthorization().headers,
              }
            }
        )
      }
    },
    // The support pages. The bundle is a zip, so it is read as a blob; the other two answer JSON.
    support: {
      availability() {
        return get("/api/admin/support/availability")
      },
      systemInfo() {
        return get("/api/admin/support/system-info")
      },
      bundle() {
        return axios.get(createURL("/api/admin/support/bundle"), {
          ...defaultAuthorization(),
          responseType: "blob",
        })
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
      probe(endpoint: string) {
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
      url(endpoint: string) {
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
      repositories({ q, tags = [] }: { q?: string; tags?: string[] } = {}) {
        const params = new URLSearchParams()
        if (q) params.set("q", q)
        for (const tag of tags) params.append("tag", tag)
        const query = params.toString()
        return get(`/api/maven/repositories${query ? `?${query}` : ""}`)
      },
      /*
       * One page of the artifact search. `query` is the query string of the page, already built from
       * its address (see components/search/searchQuery.js), without the leading question mark.
       */
      search(query?: string) {
        return get(`/api/maven/search${query ? `?${query}` : ""}`)
      },
      /*
       * The attribute keys a search can count, each with its label, ordered by key: { attributes: [{ key, label }] }.
       * 404 when search is turned off on this instance.
       */
      facetableAttributes() {
        return get("/api/maven/search/attributes/facetable")
      },
      /*
       * The files of one version found by the search. The coordinates are required; the repository and
       * origin filters of the search narrow the files the same way they narrowed the version.
       */
      searchFiles({ groupId, artifactId, version, origin = "all", repository = [], page = 0, size = 50 }: {
        groupId: string
        artifactId: string
        version: string
        origin?: string
        repository?: string[]
        page?: number
        size?: number
      }) {
        const params = new URLSearchParams({ groupId, artifactId, version })
        if (origin !== "all") params.set("origin", origin)
        for (const name of repository) params.append("repository", name)
        if (page > 0) params.set("page", String(page))
        if (size !== 50) params.set("size", String(size))
        return get(`/api/maven/search/files?${params.toString()}`)
      },
      /*
       * The repositories a group references through loopback mirrors, in configuration order.
       */
      members(repository: string) {
        return get(`/api/maven/repositories/${encodeURIComponent(repository)}/members`)
      },
      content(gav: string) {
        return get(`/${gav}`)
      },
      details(gav?: string) {
        return get(`/api/maven/details/${gav || ""}`)
      },
      download(gav: string) {
        return get(`/${gav}`, {
          responseType: "blob",
          ...defaultAuthorization(),
        })
      },
      deploy(gav: string, file: unknown, generateChecksums: boolean) {
        return axios.put(createURL(`/${gav}`), file, {
          headers: {
            "Content-Type": "application/octet-stream",
            "X-Generate-Checksums": generateChecksums,
            ...defaultAuthorization().headers,
          }
        })
      },
      generatePom(gav: string, groupId: string, artifactId: string, version: string) {
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
      delete(gav: string) {
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
      },
      /*
       * The status of the artifact search index, for an instance admin: state, lag, the active run, the
       * last 20 runs and the last verify report. 404 when search is turned off on this instance.
       */
      searchIndex() {
        return get("/api/maven/search/index")
      },
      /*
       * Starts a rebuild. The body names repositories to rebuild, or is empty for all of them. The answer
       * is 202 with { runId }; 409 while a rebuild or a verify run is live.
       */
      startIndexRebuild(body: unknown = {}) {
        return axios.post(createURL("/api/maven/search/index/rebuild"), body, {
          ...defaultAuthorization(),
        })
      },
      startIndexVerify() {
        return axios.post(createURL("/api/maven/search/index/verify"), {}, {
          ...defaultAuthorization(),
        })
      },
      cancelIndexRun(runId: string) {
        return axios.post(createURL(`/api/maven/search/index/runs/${encodeURIComponent(runId)}/cancel`), {}, {
          ...defaultAuthorization(),
        })
      }
    },
    cleanup: {
      dryRun(policyId: string) {
        return axios.post(createURL(`/api/cleanup/policies/${encodeURIComponent(policyId)}/dry-run`), {}, {
          ...defaultAuthorization(),
        })
      },
      run(policyId: string) {
        return axios.post(createURL(`/api/cleanup/policies/${encodeURIComponent(policyId)}/run`), {}, {
          ...defaultAuthorization(),
        })
      },
      runs(policyId?: string) {
        const query = policyId ? `?policy=${encodeURIComponent(policyId)}` : ""
        return get(`/api/cleanup/runs${query}`)
      }
    },
    jobs: {
      /*
       * One page of the background runs of every source the caller may read. The params are the filters
       * (kind, state, trigger), the page size and the cursor of the next page, as the Jobs endpoint takes them.
       */
      list(params: Record<string, string> = {}) {
        const query = new URLSearchParams(params).toString()
        return get(`/api/jobs${query ? `?${query}` : ""}`)
      },
      // One run by its id, `<source>:<local id>`. The id is encoded as one path segment.
      detail(id: string) {
        return get(`/api/jobs/${encodeURIComponent(id)}`)
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
      schema(name: string) {
        return axios.get(createURL(`/api/settings/schema/${name}`), {
          headers: {
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      // The dashboard always asks for the hidden form, so no secret value reaches the browser.
      fetch(name: string) {
        return axios.get(createURL(`/api/settings/domain/${name}?secrets=hidden`), {
          headers: {
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      encryptionState() {
        return axios.get(createURL("/api/settings/encryption"), {
          headers: {
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      exportSaved(password: string) {
        return axios.post(createURL("/api/settings/export"), { password }, {
          headers: {
            "Content-Type": "application/json",
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      openExport(password: string, exported: unknown) {
        return axios.post(createURL("/api/settings/import/open"), { password, export: exported }, {
          headers: {
            "Content-Type": "application/json",
            Accepts: "application/json",
            ...defaultAuthorization().headers,
          },
        })
      },
      update(name: string, content: unknown, etag?: string | null) {
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
      get(name: string) {
        return get(`/api/tokens/${encodeURIComponent(name)}`)
      },
      createOrUpdate(name: string, request: unknown, etag?: string | null) {
        return axios.put(createURL(`/api/tokens/${encodeURIComponent(name)}`), request, {
          headers: {
            "Content-Type": "application/json",
            ...ifMatch(etag),
            ...defaultAuthorization().headers,
          },
        })
      },
      update(name: string, request: unknown, etag?: string | null) {
        return axios.patch(createURL(`/api/tokens/${encodeURIComponent(name)}`), request, {
          headers: {
            "Content-Type": "application/json",
            ...ifMatch(etag),
            ...defaultAuthorization().headers,
          },
        })
      },
      delete(name: string) {
        return axios.delete(createURL(`/api/tokens/${encodeURIComponent(name)}`), {
          ...defaultAuthorization(),
        })
      },
      regenerateSecret(name: string) {
        return axios.post(createURL(`/api/tokens/${encodeURIComponent(name)}/secret`), {}, {
          ...defaultAuthorization(),
        })
      },
    },
    // Read-only mode of the instance. Only principals with the maintenance area get an answer other
    // than 403. The writes answer with the same body; 409 means the environment forces the state.
    readOnly: {
      get() {
        return get("/api/admin/read-only")
      },
      enable() {
        return axios.post(createURL("/api/admin/read-only"), {}, {
          ...defaultAuthorization(),
        })
      },
      disable() {
        return axios.delete(createURL("/api/admin/read-only"), {
          ...defaultAuthorization(),
        })
      },
    },
    audit: {
      list(params?: QueryParams) {
        return get(`/api/audit${queryString(params)}`)
      },
      exportCsv(params?: QueryParams) {
        return axios.get(createURL(`/api/audit/export${queryString(params)}`), {
          responseType: "blob",
          ...defaultAuthorization(),
        })
      },
    },
  }
}

// Empty parameters are left out, so an unfiltered call has no question mark at all.
const queryString = (params: QueryParams = {}): string => {
  const search = new URLSearchParams(
    Object.entries(params)
      .filter(([, value]) => value !== undefined && value !== null && value !== "")
      .map(([key, value]): [string, string] => [key, String(value)])
  ).toString()
  return search ? `?${search}` : ""
}

export {
  createURL,
  createClient,
  tokenCredentials,
  setUnauthorizedHandler,
  setCookieMode,
  readCsrfToken,
  handleRequest,
  handleResponseError,
  etagOf,
  isPreconditionFailed
}
