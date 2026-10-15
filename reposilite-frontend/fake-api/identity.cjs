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
 * The identity and auth half of the fake backend: principals, teams, grants, cookie sessions with
 * CSRF, the first-run setup and the self-service endpoints. Everything lives in memory and is
 * deterministic: ids and secrets come from counters, nothing runs on a timer. Sessions expire by
 * comparing the clock when a request arrives.
 *
 * Mirrors the contracts of the identity backend (spec section 7). The state is rebuilt when the
 * process starts; INGOT_FAKE_SETUP=1 starts it empty and waiting for the first admin.
 */

const crypto = require("crypto")
const express = require("express")
const { basicAuth } = require("./extensions.cjs")
const audit = require("./audit.cjs")
const loginProviders = require("./login-providers.cjs")

const SETUP_MODE = process.env.INGOT_FAKE_SETUP === "1"
const SETUP_CODE = "fake-setup-123456"

// The access session lives 15 minutes, the refresh session 8 hours. INGOT_FAKE_SESSION_SECONDS
// shortens the first one, which makes the expiry reachable by hand.
const SESSION_TTL_MS = (Number(process.env.INGOT_FAKE_SESSION_SECONDS) || 900) * 1000
const REFRESH_TTL_MS = 8 * 60 * 60 * 1000

const MIN_PASSWORD_LENGTH = 12
const MAX_NAME_LENGTH = 255
const MAX_TEAM_NAME_LENGTH = 320
const LAST_ADMIN_MESSAGE = "The last instance admin cannot be disabled, demoted or deleted."
const PRIVATE_TEAM_PREFIX = "machine:"

const ROLES = ["READ", "DEPLOY", "MAINTAIN", "ADMIN"]
const ROLE_ACTIONS = {
  READ: ["READ"],
  DEPLOY: ["READ", "WRITE"],
  MAINTAIN: ["READ", "WRITE", "DELETE"],
  ADMIN: ["READ", "WRITE", "DELETE", "MANAGE_REPOSITORY"],
  LEGACY_READ: ["READ"],
  LEGACY_WRITE: ["WRITE", "DELETE"],
}
const ALL_ACTIONS = ["READ", "WRITE", "DELETE", "MANAGE_REPOSITORY", "INSTANCE_ADMIN"]
// Token scopes are actions, as in the backend (spec 2.1 and O-8). Roles such as DEPLOY are not scopes.
const SCOPE_NAMES = ALL_ACTIONS

// ---------------------------------------------------------------- state

let sequence = 0
const nextId = () => ++sequence
// Secrets and session ids are counters, so a run is reproducible. Unguessable they are not.
let secretCounter = 0
const nextSecret = (prefix) => `${prefix}-${String(++secretCounter).padStart(6, "0")}`

// Token secrets look like ingot_<token id>_<24 letters and digits>. The characters come from a
// hash of a counter (no underscore, so the secret splits cleanly), so a run always hands out the same secrets in the same order.
let bearerCounter = 0
const nextBearerSecret = (tokenId) => {
  const random = crypto.createHash("sha256").update(`ingot-fake-bearer-${++bearerCounter}`).digest("base64url").replace(/-/g, "x").replace(/_/g, "y").slice(0, 24)
  return `ingot_${tokenId}_${random}`
}

const nowIso = () => new Date().toISOString()
const today = () => nowIso().slice(0, 10)

let principals = []
let teams = []
// session id -> { principal, csrf, expiresAt }; refresh id -> { principal, expiresAt }
const sessions = new Map()
const refreshSessions = new Map()
let setupRequired = SETUP_MODE
let repositoriesOf = () => []

const addPrincipal = ({ kind, name, displayName, disabled = false, createdAt = nowIso(), password = null, instanceRole = false, lastSeenAt = null }) => {
  const principal = {
    id: nextId(), kind, name, displayName: displayName || name, disabled,
    createdAt, lastSeenAt, password, mustChangePassword: false, instanceRole, tokens: [],
  }
  principals.push(principal)
  return principal
}

const addTeam = ({ name, description = "", externalRef = null, members = [], grants = [], instanceRole = false }) => {
  const team = { id: nextId(), name, description, externalRef, members, grants, instanceRole }
  teams.push(team)
  return team
}

const addToken = (principal, { name, description = "", createdAt = today(), expiresAt = null, scopes = null, secret, legacyRoutes = [], lastUsedAt = null, lastUsedIp = null }) => {
  const id = 1000 + nextId()
  const token = {
    id, name, description, createdAt, expiresAt, scopes, lastUsedAt, lastUsedIp,
    secret: secret || nextBearerSecret(id), legacyRoutes,
  }
  principal.tokens.push(token)
  return token
}

// Without INGOT_FAKE_SETUP the instance is already set up and holds this small seed:
//   admin (instance admin, the only one), alice (USER), bob (USER, disabled), ci-bot (MACHINE, token
//   "ci-bot" with legacy routes, private team machine:ci-bot), team deployers (alice, ci-bot; DEPLOY on
//   releases and READ on every repository), team docs-sync (alice via SYNC, bob manual; READ on
//   private/docs). The last-used times are fixed (the fake's "today" is 2026-10-15): admin and alice
//   were seen, bob never; token ci-bot was used two days ago from 192.0.2.10, alice-laptop yesterday
//   from 198.51.100.7, alice-cli never.
const seed = () => {
  addPrincipal({ kind: "USER", name: "admin", displayName: "Administrator", createdAt: "2026-10-01T09:00:00Z", password: "admin-password-12", instanceRole: true, lastSeenAt: "2026-10-15T08:05:00Z" })
  const alice = addPrincipal({ kind: "USER", name: "alice", displayName: "Alice Example", createdAt: "2026-10-02T09:00:00Z", password: "alice-password-12", lastSeenAt: "2026-10-14T17:30:00Z" })
  addPrincipal({ kind: "USER", name: "bob", displayName: "Bob Example", createdAt: "2026-10-03T09:00:00Z", password: "bob-password-12", disabled: true })
  const ciBot = addPrincipal({ kind: "MACHINE", name: "ci-bot", displayName: "CI bot", createdAt: "2026-10-04T09:00:00Z", lastSeenAt: "2026-10-13T08:15:00Z" })
  addToken(ciBot, {
    name: "ci-bot",
    // The legacy token keeps the secret it had before the bearer format existed.
    secret: "ingot-fake-secret-000001",
    description: "Migrated from the legacy access tokens",
    createdAt: "2026-10-04",
    lastUsedAt: "2026-10-13T08:15:00Z",
    lastUsedIp: "192.0.2.10",
    legacyRoutes: [
      { path: "/releases/com/example", shortcut: "r" },
      { path: "/snapshots", shortcut: "w" },
    ],
  })
  addToken(alice, { name: "alice-laptop", description: "Work laptop", createdAt: "2026-10-05", scopes: ["READ", "WRITE"], lastUsedAt: "2026-10-14T17:25:00Z", lastUsedIp: "198.51.100.7" })
  addToken(alice, { name: "alice-cli", description: "Never used yet", createdAt: "2026-10-06", scopes: ["READ"] })
  addTeam({
    name: PRIVATE_TEAM_PREFIX + "ci-bot",
    description: "Private team of machine principal ci-bot",
    members: [{ principal: "ci-bot", source: "MANUAL" }],
    grants: [
      { repository: "releases", pathPrefix: "com/example", role: "LEGACY_READ" },
      { repository: "snapshots", pathPrefix: null, role: "LEGACY_WRITE" },
    ],
  })
  addTeam({
    name: "deployers",
    description: "Everybody who ships releases",
    members: [{ principal: "alice", source: "MANUAL" }, { principal: "ci-bot", source: "MANUAL" }],
    grants: [
      { repository: "releases", pathPrefix: null, role: "DEPLOY" },
      { repository: null, pathPrefix: null, role: "READ" },
    ],
  })
  addTeam({
    name: "docs-sync",
    description: "Kept in step with the directory group by the synchronisation job",
    externalRef: "idp:group/docs",
    members: [{ principal: "alice", source: "SYNC" }, { principal: "bob", source: "MANUAL" }],
    grants: [{ repository: "private", pathPrefix: "docs", role: "READ" }],
  })
}

if (SETUP_MODE) {
  console.log(`Ingot setup code: ${SETUP_CODE}`)
} else {
  seed()
}

// ---------------------------------------------------------------- lookups

const same = (left, right) => String(left).toLowerCase() === String(right).toLowerCase()
const findPrincipal = (name) => principals.find(principal => same(principal.name, name))
const findTeam = (name) => teams.find(team => same(team.name, name))
const isPrivateTeam = (team) => team.name.toLowerCase().startsWith(PRIVATE_TEAM_PREFIX)
const findPublicTeam = (name) => {
  const team = findTeam(name)
  return team && !isPrivateTeam(team) ? team : undefined
}
const findToken = (name) => {
  for (const principal of principals) {
    const token = principal.tokens.find(entry => entry.name === name)
    if (token) return { principal, token }
  }
  return undefined
}

const teamsOf = (principal) => teams.filter(team => team.members.some(member => same(member.principal, principal.name)))
const publicTeamNamesOf = (principal) => teamsOf(principal).filter(team => !isPrivateTeam(team)).map(team => team.name).sort()
const isInstanceAdmin = (principal) => principal.instanceRole || teamsOf(principal).some(team => team.instanceRole)

// How many enabled principals are instance admins, as if the given change had been made already.
const activeAdminCount = ({ removed, disabled, demoted, removedTeam, demotedTeam } = {}) =>
  principals.filter(principal =>
    principal !== removed && principal !== disabled && !principal.disabled &&
    ((principal.instanceRole && principal !== demoted) ||
      teamsOf(principal).some(team => team.instanceRole && team !== removedTeam && team !== demotedTeam))).length

// True when the change takes away the last instance admin that exists now.
const leavesNoAdmin = (change) => activeAdminCount() > 0 && activeAdminCount(change) === 0

// Identity changes are audited like the other mutations of the fake, as the signed-in admin.
const record = (req, action, details) =>
  audit.recordEvent({
    action, actor: req.actor?.principal?.name ?? "admin", actorKind: req.actor?.principal?.kind ?? "USER",
    actorAddress: "10.0.0.2", details,
  })

// Session events name the principal they are about, which is not always the caller (a failed login
// has none yet), so they take the actor explicitly.
const recordSession = (action, name, details, extra = {}) =>
  audit.recordEvent({
    action, actor: name, actorKind: findPrincipal(name)?.kind ?? null, actorAddress: "10.0.0.2", details, ...extra,
  })

const principalTarget = (principal) => ({ principal: principal.name, kind: principal.kind })

const grantsOf = (principal) =>
  teamsOf(principal).flatMap(team => team.grants.map(grant => ({ team: team.name, ...grant })))

const appliesTo = (grant, repository) => grant.repository === null || same(grant.repository, repository)

// ---------------------------------------------------------------- views

const principalDto = (principal) => ({
  id: principal.id, kind: principal.kind, name: principal.name, displayName: principal.displayName,
  disabled: principal.disabled, createdAt: principal.createdAt, lastSeenAt: principal.lastSeenAt,
})

// A list row: the flat principal and the summaries a list shows, so that no detail is read per row.
// instanceAdmin is the role however it is held, instanceRole the direct role (as in the detail).
// A user can sign in with the local provider while it has a password; a machine never can.
const principalListItem = (principal) => ({
  ...principalDto(principal),
  instanceAdmin: isInstanceAdmin(principal),
  instanceRole: principal.instanceRole,
  tokenCount: principal.tokens.length,
  providers: principal.kind === "USER" && principal.password ? ["local"] : [],
})

const tokenDto = (token) => ({
  name: token.name, description: token.description, createdAt: token.createdAt,
  expiresAt: token.expiresAt, scopes: token.scopes, lastUsedAt: token.lastUsedAt, lastUsedIp: token.lastUsedIp,
})

const principalDetail = (principal) => ({
  ...principalDto(principal),
  teams: publicTeamNamesOf(principal),
  instanceRole: principal.instanceRole,
  tokens: principal.tokens.map(tokenDto),
})

const teamDto = (team) => ({
  name: team.name, description: team.description, externalRef: team.externalRef,
  memberCount: team.members.length, grantCount: team.grants.length,
  instanceAdmin: team.instanceRole,
})

const teamDetail = (team) => ({
  ...teamDto(team),
  members: [...team.members]
    .sort((left, right) => left.principal.localeCompare(right.principal))
    .map(member => ({ principal: member.principal, kind: findPrincipal(member.principal)?.kind ?? "USER", source: member.source })),
  grants: team.grants.map(grant => ({ repository: grant.repository, pathPrefix: grant.pathPrefix, role: grant.role })),
  instanceRole: team.instanceRole,
})

// What a legacy route looks like in /api/tokens, for the main file to merge into its own list.
const legacyTokenViews = () =>
  principals.flatMap(principal => principal.tokens.map(token => {
    const [year, month, day] = token.createdAt.split("-").map(Number)
    return {
      identifier: { type: "PERSISTENT", value: token.id },
      name: token.name,
      createdAt: [year, month, day],
      description: token.description,
      expiresAt: token.expiresAt ? Math.floor(Date.parse(token.expiresAt) / 1000) : null,
      permissions: [],
      routes: token.legacyRoutes.map(route => ({
        path: route.path,
        permission: route.shortcut === "w"
          ? { identifier: "route:write", shortcut: "w" }
          : { identifier: "route:read", shortcut: "r" },
      })),
    }
  }))

const accessEntries = (repository) =>
  [...principals]
    .sort((left, right) => left.name.localeCompare(right.name))
    .flatMap(principal => {
      const instanceAdmin = isInstanceAdmin(principal)
      const applying = grantsOf(principal).filter(grant => appliesTo(grant, repository))
      if (!instanceAdmin && applying.length === 0) return []
      const effective = instanceAdmin
        ? [...ALL_ACTIONS]
        : [...new Set(applying.flatMap(grant => ROLE_ACTIONS[grant.role]))]
      const via = applying.map(grant => ({
        source: grant.role.startsWith("LEGACY_") ? "LEGACY" : "TEAM",
        team: grant.team, role: grant.role, pathPrefix: grant.pathPrefix,
      }))
      if (instanceAdmin) via.push({ source: "INSTANCE_ADMIN", team: null, role: null, pathPrefix: null })
      return [{
        principal: principal.name, kind: principal.kind, disabled: principal.disabled,
        instanceAdmin, effective: effective.sort(), via,
      }]
    })

// ---------------------------------------------------------------- http helpers

const fail = (res, status, message) => res.status(status).send({ status, message })

// A hash of the body, without the times that change on a read or a sign-in, so that signing in does
// not make every open editor stale. The tag is quoted, as the header requires.
const VOLATILE_FIELDS = new Set(["lastSeenAt", "lastUsedAt", "lastUsedIp"])
const etagOf = (body) =>
  `"${crypto.createHash("sha1").update(JSON.stringify(body, (key, value) => VOLATILE_FIELDS.has(key) ? undefined : value)).digest("hex").slice(0, 20)}"`

const reply = (res, body, status = 200) => {
  res.setHeader("ETag", etagOf(body))
  return res.status(status).send(body)
}

const noContent = (res) => res.status(204).end()

// Absent header: no precondition. Otherwise one listed tag (or *) has to match the current body.
const preconditionHolds = (req, current) => {
  const header = req.get("If-Match")
  if (!header || !header.trim()) return true
  const tag = etagOf(current)
  return header.split(",").map(candidate => candidate.trim().replace(/^W\//, "")).some(candidate => candidate === "*" || candidate === tag)
}

const requirePrecondition = (req, res, current, label) => {
  if (preconditionHolds(req, current)) return true
  fail(res, 412, `${label} was changed by somebody else, reload and try again`)
  return false
}

const bodyOf = (req) =>
  req.body && typeof req.body === "object" && !Buffer.isBuffer(req.body) && !Array.isArray(req.body) ? req.body : {}

const parseCookies = (req) =>
  Object.fromEntries((req.headers.cookie || "").split(";")
    .map(part => part.trim()).filter(part => part !== "")
    .map(part => {
      const index = part.indexOf("=")
      return index < 0 ? [part, ""] : [part.slice(0, index), decodeURIComponent(part.slice(index + 1))]
    }))

const validName = (value, limit) => {
  if (typeof value !== "string") return { problem: "Name is required" }
  const name = value.trim()
  if (name === "") return { problem: "Name is required" }
  if (name.includes(":")) return { problem: "Name must not contain ':'" }
  if (name.length > limit) return { problem: `Name must be at most ${limit} characters` }
  return { name }
}

const normalizePrefix = (prefix) => {
  if (typeof prefix !== "string") return null
  const trimmed = prefix.trim().replace(/^\/+|\/+$/g, "")
  return trimmed === "" ? null : trimmed
}

const parseExpiry = (value) => {
  if (value === undefined || value === null || value === "") return { expiresAt: null }
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? { problem: "expiresAt is not a date" } : { expiresAt: new Date(parsed).toISOString() }
}

const parseScopes = (value) => {
  if (value === undefined || value === null) return { scopes: null }
  if (!Array.isArray(value) || value.length === 0) return { problem: "Scopes cannot be empty, omit them for an unrestricted token" }
  const scopes = value.map(scope => String(scope).toUpperCase())
  const unknown = scopes.find(scope => !SCOPE_NAMES.includes(scope))
  return unknown ? { problem: `Unknown scope '${unknown}', supported: ${SCOPE_NAMES.join(", ")}` } : { scopes: [...new Set(scopes)].sort() }
}

// ---------------------------------------------------------------- sessions and cookies

const COOKIE_BASE = { path: "/", sameSite: "strict" }

const principalSummary = (principal) =>
  ({ id: principal.id, kind: principal.kind, name: principal.name, displayName: principal.displayName })

const startSession = (res, principal) => {
  const now = Date.now()
  const sessionId = nextSecret("session")
  const refreshId = nextSecret("refresh")
  const csrf = nextSecret("csrf")
  sessions.set(sessionId, { principal: principal.name, csrf, expiresAt: now + SESSION_TTL_MS })
  refreshSessions.set(refreshId, { principal: principal.name, expiresAt: now + REFRESH_TTL_MS })
  res.cookie("ingot_session", sessionId, { ...COOKIE_BASE, httpOnly: true, maxAge: SESSION_TTL_MS })
  res.cookie("ingot_refresh", refreshId, { ...COOKIE_BASE, httpOnly: true, maxAge: REFRESH_TTL_MS })
  res.cookie("ingot_csrf", csrf, { ...COOKIE_BASE, httpOnly: false, maxAge: REFRESH_TTL_MS })
  const body = { principal: principalSummary(principal), expiresAt: new Date(now + SESSION_TTL_MS).toISOString() }
  if (principal.mustChangePassword) body.mustChangePassword = true
  return body
}

const clearSession = (req, res) => {
  const cookies = parseCookies(req)
  sessions.delete(cookies.ingot_session)
  refreshSessions.delete(cookies.ingot_refresh)
  for (const name of ["ingot_session", "ingot_refresh", "ingot_csrf"]) res.clearCookie(name, COOKIE_BASE)
}

const dropSessionsOf = (name) => {
  for (const [id, session] of sessions) if (same(session.principal, name)) sessions.delete(id)
  for (const [id, session] of refreshSessions) if (same(session.principal, name)) refreshSessions.delete(id)
}

// Who is calling: a live cookie session, or the fake's one fixed Basic credential, which the other
// routes of the fake accept as well and which counts as an instance admin.
const bearerSecret = (req) => {
  const match = /^Bearer\s+(\S+)\s*$/i.exec(req.headers.authorization || "")
  return match ? match[1] : undefined
}

// A bearer token acts as its principal. Its scopes narrow what the principal may do, and a token
// past its expiry is refused. Only the identity routes read this actor; the other routes of the
// fake keep to the Basic credential.
const resolveBearer = (req, secret) => {
  const found = principals.flatMap(principal => principal.tokens.map(token => ({ principal, token })))
    .find(entry => entry.token.secret === secret)
  if (!found || found.principal.disabled) return null
  const { principal, token } = found
  if (token.expiresAt && Date.parse(token.expiresAt) <= Date.now()) return null
  token.lastUsedAt = nowIso()
  token.lastUsedIp = req.ip || null
  // The server records the use on the principal as well, which is what a machine list row shows.
  principal.lastSeenAt = token.lastUsedAt
  const scoped = token.scopes === null || token.scopes.includes("INSTANCE_ADMIN")
  return { via: "bearer", principal, admin: isInstanceAdmin(principal) && scoped }
}

const resolveActor = (req) => {
  if (req.headers.authorization) {
    const secret = bearerSecret(req)
    if (secret !== undefined) return resolveBearer(req, secret)
    const [login, password] = basicAuth(req)
    return login === "name" && password === "secret"
      ? { via: "token", principal: findPrincipal("admin") ?? null, admin: true }
      : null
  }
  const cookies = parseCookies(req)
  const session = sessions.get(cookies.ingot_session)
  if (!session) return null
  if (session.expiresAt <= Date.now()) {
    sessions.delete(cookies.ingot_session)
    return null
  }
  const principal = findPrincipal(session.principal)
  if (!principal || principal.disabled) return null
  return { via: "cookie", principal, admin: isInstanceAdmin(principal) }
}

const csrfValid = (req) => {
  const header = req.get("X-CSRF-Token")
  return Boolean(header) && header === parseCookies(req).ingot_csrf
}

// ---------------------------------------------------------------- me

const routePath = (grant) =>
  "/" + [grant.repository, grant.pathPrefix].filter(part => part).join("/")

const meBody = (actor) => {
  const principal = actor.principal
  const grants = grantsOf(principal)
  const routes = grants.flatMap(grant => {
    const actions = ROLE_ACTIONS[grant.role]
    return [
      ...(actions.includes("READ") ? [{ path: routePath(grant), permission: { identifier: "route:read" } }] : []),
      ...(actions.includes("WRITE") ? [{ path: routePath(grant), permission: { identifier: "route:write" } }] : []),
    ]
  })
  const body = {
    accessToken: { id: principal.id, name: principal.name, createdAt: Date.parse(principal.createdAt), description: principal.displayName },
    permissions: actor.admin ? [{ identifier: "access-token:manager" }] : [],
    routes,
    principal: principalSummary(principal),
    teams: publicTeamNamesOf(principal),
    grants: grants.map(grant => ({ team: grant.team, repository: grant.repository, pathPrefix: grant.pathPrefix, role: grant.role })),
    instanceAdmin: actor.admin,
  }
  if (principal.mustChangePassword) body.mustChangePassword = true
  return body
}

// The existing /api/auth/me answers the fixed Basic credential; this adds the identity fields to it.
const decorateTokenMe = (body) => {
  const admin = findPrincipal("admin")
  return {
    ...body,
    principal: admin ? principalSummary(admin) : { id: 0, kind: "MACHINE", name: "name", displayName: "name" },
    teams: admin ? publicTeamNamesOf(admin) : [],
    grants: admin ? grantsOf(admin).map(grant => ({ team: grant.team, repository: grant.repository, pathPrefix: grant.pathPrefix, role: grant.role })) : [],
    instanceAdmin: true,
  }
}

// ---------------------------------------------------------------- the router

const router = express.Router()

// The dashboard in development runs on another port and sends cookies, so the wildcard of the
// outer middleware is replaced by the caller's origin, which credentials require.
router.use((req, res, next) => {
  const origin = req.get("Origin")
  if (origin) {
    res.setHeader("Access-Control-Allow-Origin", origin)
    res.setHeader("Access-Control-Allow-Credentials", "true")
    res.setHeader("Access-Control-Allow-Headers", req.get("Access-Control-Request-Headers") || "Content-Type, X-CSRF-Token, If-Match, Authorization")
    res.setHeader("Access-Control-Expose-Headers", "ETag, Retry-After")
    res.setHeader("Vary", "Origin")
  }
  if (req.method === "OPTIONS") return res.status(204).end()
  next()
})

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"])
const CSRF_EXEMPT_PATHS = new Set(["/api/auth/login", "/api/auth/setup"])

router.use((req, res, next) => {
  req.actor = resolveActor(req)
  if (req.actor?.via === "cookie") {
    if (UNSAFE_METHODS.has(req.method) && !CSRF_EXEMPT_PATHS.has(req.path) && !csrfValid(req)) {
      return fail(res, 403, "Missing or invalid CSRF token")
    }
    // The other routes of the fake only know the Basic credential. A signed-in instance admin is
    // let through them by presenting it on the admin's behalf.
    if (req.actor.admin) {
      req.headers.authorization = "Basic " + Buffer.from("name:secret").toString("base64")
    }
  }
  next()
})

// The sign in providers, their single sign on logins, LDAP, the test endpoints and the login settings.
// Mounted here so that they share the actor and CSRF handling above. See login-providers.cjs.
router.use(loginProviders.create({
  findPrincipal,
  addPrincipal,
  startSession,
  fail,
  bodyOf,
  etagOf,
  setupRequired: () => setupRequired,
}))

// ---- auth

const MAX_FAILED_LOGINS = 5
const LOGIN_RETRY_AFTER_SECONDS = 900
const failedLogins = new Map()

router.post("/api/auth/login", (req, res) => {
  const { provider, username, password } = bodyOf(req)
  if (provider !== "local") return fail(res, 400, "Unknown provider, supported: local, ldap")
  const principal = typeof username === "string" ? findPrincipal(username) : undefined
  const attempted = typeof username === "string" ? username.trim().toLowerCase() : ""
  if (!principal || principal.password === null || principal.password !== password) {
    // Throttling: a counter per username and a fixed Retry-After, no timers. The fake has no clock
    // to wait out, so a correct password is the way out of the lock; it resets the counter below.
    if ((failedLogins.get(attempted) ?? 0) >= MAX_FAILED_LOGINS) {
      res.setHeader("Retry-After", String(LOGIN_RETRY_AFTER_SECONDS))
      return fail(res, 429, "Too many failed attempts")
    }
    failedLogins.set(attempted, (failedLogins.get(attempted) ?? 0) + 1)
    recordSession("LOGIN_FAILED", String(username ?? "").trim() || "unknown", { provider: "local" }, { success: false })
    return fail(res, 401, "Invalid credentials")
  }
  failedLogins.delete(attempted)
  if (principal.disabled) return fail(res, 403, "This account is disabled")
  principal.lastSeenAt = nowIso()
  recordSession("LOGIN_SUCCEEDED", principal.name, { provider: "local" })
  res.send(startSession(res, principal))
})

router.post("/api/auth/refresh", (req, res) => {
  if (!csrfValid(req)) return fail(res, 403, "Missing or invalid CSRF token")
  const cookies = parseCookies(req)
  const refresh = refreshSessions.get(cookies.ingot_refresh)
  const principal = refresh && refresh.expiresAt > Date.now() ? findPrincipal(refresh.principal) : undefined
  if (!principal || principal.disabled) {
    clearSession(req, res)
    return fail(res, 401, "The session cannot be renewed")
  }
  // Rotation: the old session and refresh ids stop working as the new pair is issued.
  sessions.delete(cookies.ingot_session)
  refreshSessions.delete(cookies.ingot_refresh)
  res.send(startSession(res, principal))
})

router.post("/api/auth/logout", (req, res) => {
  if (req.actor?.via === "cookie") recordSession("LOGOUT", req.actor.principal.name, null)
  clearSession(req, res)
  noContent(res)
})

router.get("/api/auth/setup", (req, res) => res.send({ setupRequired }))

router.post("/api/auth/setup", (req, res) => {
  if (!setupRequired) return fail(res, 409, "Setup is already done")
  const { setupCode, username, password, displayName } = bodyOf(req)
  if (setupCode !== SETUP_CODE) return fail(res, 401, "Invalid setup code")
  const { name, problem } = validName(username, MAX_NAME_LENGTH)
  if (problem) return fail(res, 400, problem.replace("Name", "Username"))
  if (typeof password !== "string" || password.length < MIN_PASSWORD_LENGTH) {
    return fail(res, 400, `The password needs at least ${MIN_PASSWORD_LENGTH} characters`)
  }
  const admin = addPrincipal({
    kind: "USER", name, displayName: typeof displayName === "string" && displayName.trim() ? displayName.trim() : name,
    password, instanceRole: true,
  })
  admin.lastSeenAt = nowIso()
  setupRequired = false
  recordSession("SETUP_COMPLETED", admin.name, { principal: admin.name, kind: "USER" })
  res.send(startSession(res, admin))
})

// Cookie sessions answer here; the fixed Basic credential and anonymous callers fall through to the
// existing handler in the main file, which adds the identity fields through decorateTokenMe.
router.get("/api/auth/me", (req, res, next) => {
  if (req.actor?.via !== "cookie") return next()
  res.send(meBody(req.actor))
})

// ---- identity

const identity = express.Router()

identity.use((req, res, next) => {
  if (!req.actor) return fail(res, 401, "Invalid credentials")
  if (req.path.startsWith("/me/") || req.path === "/me") {
    if (!req.actor.principal) return fail(res, 401, "Invalid credentials")
    return next()
  }
  if (!req.actor.admin) return fail(res, 403, "Requires an instance admin")
  next()
})

// ---- self service

const publicTokenFields = (principal, name) => principal.tokens.find(token => token.name === name)

identity.post("/me/password", (req, res) => {
  const principal = req.actor.principal
  const { currentPassword, newPassword } = bodyOf(req)
  // 400 rather than 401: a 401 would make the dashboard treat the session as lost and sign the user out.
  if (principal.password === null) return fail(res, 400, "This account has no password")
  if (principal.password !== currentPassword) return fail(res, 400, "The current password is wrong")
  if (typeof newPassword !== "string" || newPassword.length < MIN_PASSWORD_LENGTH) {
    return fail(res, 400, `The password needs at least ${MIN_PASSWORD_LENGTH} characters`)
  }
  principal.password = newPassword
  principal.mustChangePassword = false
  record(req, "PASSWORD_CHANGED", principalTarget(principal))
  noContent(res)
})

identity.get("/me/tokens", (req, res) => reply(res, req.actor.principal.tokens.map(tokenDto)))

identity.post("/me/tokens", (req, res) => createToken(req, res, req.actor.principal, 200))

identity.delete("/me/tokens/:name", (req, res) => {
  const principal = req.actor.principal
  if (!publicTokenFields(principal, req.params.name)) return fail(res, 404, "Token not found")
  principal.tokens = principal.tokens.filter(token => token.name !== req.params.name)
  noContent(res)
})

identity.post("/me/tokens/:name/rotate", (req, res) => rotateToken(res, req.actor.principal, req.params.name))

// ---- tokens (shared by self service and admin)

const createToken = (req, res, principal, status) => {
  const body = bodyOf(req)
  const { name, problem } = validName(body.name, MAX_NAME_LENGTH)
  if (problem) return fail(res, 400, problem.replace("Name", "Token name"))
  if (findToken(name)) return fail(res, 409, `Token '${name}' already exists`)
  const expiry = parseExpiry(body.expiresAt)
  if (expiry.problem) return fail(res, 400, expiry.problem)
  const scopes = parseScopes(body.scopes)
  if (scopes.problem) return fail(res, 400, scopes.problem)
  if (body.secret !== undefined && (typeof body.secret !== "string" || body.secret.trim() === "")) {
    return fail(res, 400, "Secret cannot be blank")
  }
  const token = addToken(principal, {
    name, description: typeof body.description === "string" ? body.description : "",
    expiresAt: expiry.expiresAt, scopes: scopes.scopes, secret: body.secret,
  })
  reply(res, { token: tokenDto(token), secret: token.secret }, status)
}

const rotateToken = (res, principal, name) => {
  const token = publicTokenFields(principal, name)
  if (!token) return fail(res, 404, "Token not found")
  token.secret = nextBearerSecret(token.id)
  token.lastUsedAt = null
  token.lastUsedIp = null
  reply(res, { token: tokenDto(token), secret: token.secret })
}

// ---- principals

const withPrincipal = (handler) => (req, res) => {
  const principal = findPrincipal(req.params.name)
  return principal ? handler(req, res, principal) : fail(res, 404, "Principal not found")
}

identity.get("/principals", (req, res) => {
  const kind = req.query.kind
  if (kind !== undefined && !["USER", "MACHINE"].includes(String(kind).toUpperCase())) {
    return fail(res, 400, `Unknown principal kind '${kind}', supported: USER, MACHINE`)
  }
  reply(res, [...principals]
    .filter(principal => kind === undefined || principal.kind === String(kind).toUpperCase())
    .sort((left, right) => left.name.localeCompare(right.name))
    .map(principalListItem))
})

identity.post("/principals", (req, res) => {
  const body = bodyOf(req)
  const kind = typeof body.kind === "string" ? body.kind.toUpperCase() : undefined
  if (!["USER", "MACHINE"].includes(kind)) return fail(res, 400, "Principal kind must be USER or MACHINE")
  const { name, problem } = validName(body.name, MAX_NAME_LENGTH)
  if (problem) return fail(res, 400, problem.replace("Name", "Principal name"))
  if (findPrincipal(name)) return fail(res, 409, `Principal '${name}' already exists`)
  if (kind === "MACHINE" && findTeam(PRIVATE_TEAM_PREFIX + name)) return fail(res, 409, `Principal '${name}' already exists`)
  const principal = addPrincipal({
    kind, name, disabled: body.disabled === true,
    displayName: typeof body.displayName === "string" && body.displayName.trim() ? body.displayName.trim() : name,
  })
  if (kind === "MACHINE") {
    addTeam({
      name: PRIVATE_TEAM_PREFIX + name,
      description: `Private team of machine principal ${name}`,
      members: [{ principal: name, source: "MANUAL" }],
    })
  }
  record(req, "PRINCIPAL_CREATED", principalTarget(principal))
  reply(res, principalDetail(principal), 201)
})

identity.get("/principals/:name", withPrincipal((req, res, principal) => reply(res, principalDetail(principal))))

identity.patch("/principals/:name", withPrincipal((req, res, principal) => {
  if (!requirePrecondition(req, res, principalDetail(principal), `Principal '${principal.name}'`)) return
  const body = bodyOf(req)
  if (body.displayName !== undefined && (typeof body.displayName !== "string" || body.displayName.trim() === "")) {
    return fail(res, 400, "Display name cannot be blank")
  }
  if (body.disabled !== undefined && typeof body.disabled !== "boolean") return fail(res, 400, "disabled must be true or false")
  if (body.disabled === true && leavesNoAdmin({ disabled: principal })) return fail(res, 409, LAST_ADMIN_MESSAGE)
  const fields = []
  if (body.displayName !== undefined && body.displayName.trim() !== principal.displayName) {
    principal.displayName = body.displayName.trim()
    fields.push("displayName")
  }
  if (body.disabled !== undefined && body.disabled !== principal.disabled) {
    principal.disabled = body.disabled
    if (body.disabled) dropSessionsOf(principal.name)
    else fields.push("disabled")
    if (body.disabled) record(req, "PRINCIPAL_DISABLED", principalTarget(principal))
  }
  if (fields.length > 0) record(req, "PRINCIPAL_UPDATED", { ...principalTarget(principal), fields })
  reply(res, principalDetail(principal))
}))

identity.delete("/principals/:name", withPrincipal((req, res, principal) => {
  if (!requirePrecondition(req, res, principalDetail(principal), `Principal '${principal.name}'`)) return
  if (leavesNoAdmin({ removed: principal })) return fail(res, 409, LAST_ADMIN_MESSAGE)
  dropSessionsOf(principal.name)
  principals = principals.filter(entry => entry !== principal)
  teams = teams.filter(team => !(isPrivateTeam(team) && same(team.name, PRIVATE_TEAM_PREFIX + principal.name)))
  for (const team of teams) team.members = team.members.filter(member => !same(member.principal, principal.name))
  record(req, "PRINCIPAL_DELETED", principalTarget(principal))
  noContent(res)
}))

identity.post("/principals/:name/password", withPrincipal((req, res, principal) => {
  const { password } = bodyOf(req)
  if (principal.kind === "MACHINE") return fail(res, 400, "Machine principals have no password")
  if (typeof password !== "string" || password.length < MIN_PASSWORD_LENGTH) {
    return fail(res, 400, `The password needs at least ${MIN_PASSWORD_LENGTH} characters`)
  }
  principal.password = password
  principal.mustChangePassword = true
  dropSessionsOf(principal.name)
  record(req, "PASSWORD_RESET", principalTarget(principal))
  noContent(res)
}))

const setInstanceAdmin = (value) => withPrincipal((req, res, principal) => {
  if (!requirePrecondition(req, res, principalDetail(principal), `Principal '${principal.name}'`)) return
  if (!value && principal.instanceRole && leavesNoAdmin({ demoted: principal })) return fail(res, 409, LAST_ADMIN_MESSAGE)
  if (principal.instanceRole !== value) {
    principal.instanceRole = value
    record(req, value ? "INSTANCE_ADMIN_GRANTED" : "INSTANCE_ADMIN_REVOKED", principalTarget(principal))
  }
  reply(res, principalDetail(principal))
})
identity.put("/principals/:name/instance-admin", setInstanceAdmin(true))
identity.delete("/principals/:name/instance-admin", setInstanceAdmin(false))

identity.get("/principals/:name/tokens", withPrincipal((req, res, principal) => reply(res, principal.tokens.map(tokenDto))))

identity.post("/principals/:name/tokens", withPrincipal((req, res, principal) => createToken(req, res, principal, 200)))

identity.post("/principals/:name/tokens/:tokenName/rotate", withPrincipal((req, res, principal) =>
  rotateToken(res, principal, req.params.tokenName)))

identity.delete("/principals/:name/tokens/:tokenName", withPrincipal((req, res, principal) => {
  if (!publicTokenFields(principal, req.params.tokenName)) return fail(res, 404, "Token not found")
  principal.tokens = principal.tokens.filter(token => token.name !== req.params.tokenName)
  noContent(res)
}))

// ---- teams

const withTeam = (handler) => (req, res) => {
  const team = findPublicTeam(req.params.name)
  return team ? handler(req, res, team) : fail(res, 404, "Team not found")
}

identity.get("/teams", (req, res) =>
  reply(res, teams.filter(team => !isPrivateTeam(team)).sort((left, right) => left.name.localeCompare(right.name)).map(teamDto)))

identity.post("/teams", (req, res) => {
  const body = bodyOf(req)
  // The reserved prefix is a conflict with the private teams, not a malformed name.
  if (typeof body.name === "string" && body.name.trim().toLowerCase().startsWith(PRIVATE_TEAM_PREFIX)) {
    return fail(res, 409, `Team names starting with '${PRIVATE_TEAM_PREFIX}' are reserved`)
  }
  const { name, problem } = validName(body.name, MAX_TEAM_NAME_LENGTH)
  if (problem) return fail(res, 400, problem.replace("Name", "Team name"))
  if (findTeam(name)) return fail(res, 409, `Team '${name}' already exists`)
  const team = addTeam({
    name, description: typeof body.description === "string" ? body.description : "",
    externalRef: typeof body.externalRef === "string" && body.externalRef.trim() ? body.externalRef.trim() : null,
  })
  record(req, "TEAM_CREATED", { team: team.name })
  reply(res, teamDetail(team), 201)
})

identity.get("/teams/:name", withTeam((req, res, team) => reply(res, teamDetail(team))))

identity.patch("/teams/:name", withTeam((req, res, team) => {
  if (!requirePrecondition(req, res, teamDetail(team), `Team '${team.name}'`)) return
  const body = bodyOf(req)
  if (body.description !== undefined && typeof body.description !== "string") return fail(res, 400, "description must be text")
  if (body.externalRef !== undefined && body.externalRef !== null && typeof body.externalRef !== "string") {
    return fail(res, 400, "externalRef must be text")
  }
  const fields = []
  if (body.description !== undefined && body.description !== team.description) {
    team.description = body.description
    fields.push("description")
  }
  if (body.externalRef !== undefined) {
    const externalRef = body.externalRef && body.externalRef.trim() ? body.externalRef.trim() : null
    if (externalRef !== team.externalRef) {
      team.externalRef = externalRef
      fields.push("externalRef")
    }
  }
  if (fields.length > 0) record(req, "TEAM_UPDATED", { team: team.name, fields })
  reply(res, teamDetail(team))
}))

identity.delete("/teams/:name", withTeam((req, res, team) => {
  if (!requirePrecondition(req, res, teamDetail(team), `Team '${team.name}'`)) return
  if (team.instanceRole && leavesNoAdmin({ removedTeam: team })) return fail(res, 409, LAST_ADMIN_MESSAGE)
  teams = teams.filter(entry => entry !== team)
  record(req, "TEAM_DELETED", { team: team.name })
  noContent(res)
}))

identity.put("/teams/:name/members", withTeam((req, res, team) => {
  if (!requirePrecondition(req, res, teamDetail(team), `Team '${team.name}'`)) return
  const { members } = bodyOf(req)
  if (!Array.isArray(members)) return fail(res, 400, "Members are required, send an empty list to remove all manual members")
  const resolved = []
  for (const entry of members) {
    const principal = typeof entry === "string" ? findPrincipal(entry) : undefined
    if (!principal) return fail(res, 400, `Unknown principal '${entry}'`)
    if (!resolved.includes(principal.name)) resolved.push(principal.name)
  }
  const kept = team.members.filter(member => member.source === "SYNC")
  const manual = resolved
    .filter(name => !kept.some(member => same(member.principal, name)))
    .map(name => ({ principal: name, source: "MANUAL" }))
  const before = team.members.map(member => member.principal)
  team.members = [...kept, ...manual]
  const after = team.members.map(member => member.principal)
  const membersAdded = after.filter(name => !before.some(other => same(other, name)))
  const membersRemoved = before.filter(name => !after.some(other => same(other, name)))
  if (membersAdded.length > 0 || membersRemoved.length > 0) {
    record(req, "TEAM_MEMBERSHIP_CHANGED", { team: team.name, added: membersAdded, removed: membersRemoved })
  }
  reply(res, teamDetail(team))
}))

identity.put("/teams/:name/grants", withTeam((req, res, team) => {
  if (!requirePrecondition(req, res, teamDetail(team), `Team '${team.name}'`)) return
  const { grants } = bodyOf(req)
  if (!Array.isArray(grants)) return fail(res, 400, "Grants are required, send an empty list to remove all grants")
  const parsed = []
  for (const entry of grants) {
    const grant = entry && typeof entry === "object" ? entry : {}
    const role = typeof grant.role === "string" ? grant.role.toUpperCase() : undefined
    if (!ROLES.includes(role)) return fail(res, 400, `Unknown role '${grant.role}', supported: ${ROLES.join(", ")}`)
    const repository = typeof grant.repository === "string" && grant.repository.trim() ? grant.repository.trim() : null
    if (repository !== null && repository.includes("/")) return fail(res, 400, `Repository '${repository}' must not contain '/'`)
    parsed.push({ repository, pathPrefix: normalizePrefix(grant.pathPrefix), role })
  }
  const describe = (grant) => `${grant.repository ?? "all repositories"}${grant.pathPrefix ? "/" + grant.pathPrefix : ""} (${grant.role})`
  const before = team.grants.map(describe)
  team.grants = parsed
  const after = team.grants.map(describe)
  const grantsAdded = after.filter(entry => !before.includes(entry))
  const grantsRemoved = before.filter(entry => !after.includes(entry))
  if (grantsAdded.length > 0 || grantsRemoved.length > 0) {
    record(req, "TEAM_GRANTS_CHANGED", { team: team.name, grantsAdded, grantsRemoved })
  }
  reply(res, teamDetail(team))
}))

const setTeamInstanceAdmin = (value) => withTeam((req, res, team) => {
  if (!requirePrecondition(req, res, teamDetail(team), `Team '${team.name}'`)) return
  if (!value && team.instanceRole && leavesNoAdmin({ demotedTeam: team })) return fail(res, 409, LAST_ADMIN_MESSAGE)
  if (team.instanceRole !== value) {
    team.instanceRole = value
    record(req, value ? "INSTANCE_ADMIN_GRANTED" : "INSTANCE_ADMIN_REVOKED", { team: team.name })
  }
  reply(res, teamDetail(team))
})
identity.put("/teams/:name/instance-admin", setTeamInstanceAdmin(true))
identity.delete("/teams/:name/instance-admin", setTeamInstanceAdmin(false))

// ---- access on a repository

identity.get("/repositories/:repository/access", (req, res) => {
  const repository = repositoriesOf().find(entry => same(entry.id, req.params.repository))
  if (!repository) return fail(res, 404, "Repository not found")
  reply(res, { repository: repository.id, visibility: repository.visibility, entries: accessEntries(repository.id) })
})

router.use("/api/identity", identity)

module.exports = {
  router,
  legacyTokenViews,
  decorateTokenMe,
  // The repositories come from the maven settings of the main file, which the dashboard can edit.
  connectRepositories: (supplier) => { repositoriesOf = supplier },
}
