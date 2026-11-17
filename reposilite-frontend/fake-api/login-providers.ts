/*
 * The sign in providers of the fake backend: the list that the sign in dialog reads, the single sign
 * on logins (which sign the visitor in at once), the LDAP sign in, the two test endpoints, and the
 * `login` settings domain with its schema and entity tag. Mounted by identity.cjs, after its CSRF and
 * actor middleware, so that these routes share the cookie and CORS handling of the identity routes.
 *
 * The answers mirror the backend (identity model, section 14): the same fields and defaults, the same
 * error texts, and no null fields, because the backend's mapper leaves them out.
 */

const express = require("express")

const SECRET_PLACEHOLDER = "********"
const LDAP_LOGIN = { username: "ldapuser", password: "ldap-password-12" }
const MAX_FAILED_LDAP_LOGINS = 5
const LDAP_RETRY_AFTER_SECONDS = 900
const GITHUB_ISSUER = "https://github.com"
const GITHUB_ENDPOINTS = {
  authorization: "https://github.com/login/oauth/authorize",
  token: "https://github.com/login/oauth/access_token",
  userinfo: "https://api.github.com/user",
}
const LOOPBACK_HOSTS = ["localhost", "127.0.0.1", "[::1]"]
const PUBLIC_URL_RULE = "Enter an absolute https:// address, or http:// for localhost, without a query, a fragment or a user name"

// The defaults of LoginSettings. The sample providers are the ones the dashboard shows; GitHub is an
// OpenID Connect bridge here (it has an issuer of its own), so its test fails in the way a bridge does.
const defaultSettings = () => ({
  sessionLifetimeMinutes: 15,
  refreshLifetimeHours: 168,
  refreshAbsoluteDays: 30,
  maxFailedAttempts: 5,
  lockoutMinutes: 15,
  publicUrl: "",
  oidcProviders: [
    {
      id: "keycloak",
      enabled: true,
      label: "Keycloak",
      icon: "KEYCLOAK",
      issuerUrl: "https://sso.example.org/realms/ingot",
      clientId: "ingot",
      clientSecret: "keycloak-secret",
      scopes: "openid profile email",
      usernameClaim: "preferred_username",
      displayNameClaim: "name",
      emailClaim: "email",
      groupsClaim: "groups",
      allowedEmailDomains: [],
      autoCreateUsers: true,
      adminGroups: ["ingot-admins"],
    },
    {
      id: "github",
      enabled: true,
      label: "GitHub",
      icon: "GITHUB",
      issuerUrl: "https://github-bridge.example.org",
      clientId: "Iv1.example",
      clientSecret: "github-secret",
      scopes: "openid profile email",
      usernameClaim: "preferred_username",
      displayNameClaim: "name",
      emailClaim: "email",
      groupsClaim: "groups",
      allowedEmailDomains: ["example.org"],
      autoCreateUsers: false,
      adminGroups: [],
    },
  ],
  ldap: {
    enabled: true,
    label: "Corporate LDAP",
    url: "ldaps://ldap.example.org:636",
    startTls: false,
    bindDn: "cn=svc-ingot,dc=example,dc=org",
    bindPassword: "bind-secret",
    userBaseDn: "ou=people,dc=example,dc=org",
    userFilter: "(uid={username})",
    usernameAttribute: "uid",
    displayNameAttribute: "cn",
    emailAttribute: "mail",
    groupBaseDn: "ou=groups,dc=example,dc=org",
    groupFilter: "(member={dn})",
    groupNameAttribute: "cn",
    autoCreateUsers: true,
    adminGroups: ["ingot-admins"],
  },
})

// The schema of the `login` domain, as the settings page reads it. The domain has no form of its own:
// the Access pages edit it, and the Settings page only links to them.
const LOGIN_SCHEMA = {
  type: "object",
  title: "Login",
  description: "Browser login settings",
  properties: {
    sessionLifetimeMinutes: { type: "integer", title: "Session Lifetime Minutes" },
    refreshLifetimeHours: { type: "integer", title: "Refresh Lifetime Hours" },
    refreshAbsoluteDays: { type: "integer", title: "Refresh Absolute Days" },
    maxFailedAttempts: { type: "integer", title: "Maximum Failed Attempts" },
    lockoutMinutes: { type: "integer", title: "Lockout Minutes" },
    publicUrl: { type: "string", title: "Public URL" },
    oidcProviders: {
      type: "array",
      title: "OpenID Connect Providers",
      items: {
        type: "object",
        properties: {
          id: { type: "string", title: "Id" },
          enabled: { type: "boolean", title: "Enabled" },
          label: { type: "string", title: "Label" },
          icon: { type: "string", title: "Icon", enum: ["GENERIC", "GITHUB", "GITLAB", "GOOGLE", "MICROSOFT", "KEYCLOAK", "AUTHENTIK", "OKTA", "AUTH0", "GITEA"] },
          issuerUrl: { type: "string", title: "Issuer URL" },
          clientId: { type: "string", title: "Client Id" },
          clientSecret: { type: "string", title: "Client Secret" },
          scopes: { type: "string", title: "Scopes" },
          usernameClaim: { type: "string", title: "Username Claim" },
          displayNameClaim: { type: "string", title: "Display Name Claim" },
          emailClaim: { type: "string", title: "Email Claim" },
          groupsClaim: { type: "string", title: "Groups Claim" },
          allowedEmailDomains: { type: "array", title: "Allowed Email Domains", items: { type: "string" } },
          autoCreateUsers: { type: "boolean", title: "Automatically Create Users" },
          adminGroups: { type: "array", title: "Admin Groups", items: { type: "string" } },
        },
      },
    },
    ldap: {
      type: "object",
      title: "LDAP",
      properties: {
        enabled: { type: "boolean", title: "Enabled" },
        label: { type: "string", title: "Label" },
        url: { type: "string", title: "URL" },
        startTls: { type: "boolean", title: "StartTLS" },
        bindDn: { type: "string", title: "Bind DN" },
        bindPassword: { type: "string", title: "Bind Password" },
        userBaseDn: { type: "string", title: "User Base DN" },
        userFilter: { type: "string", title: "User Filter" },
        usernameAttribute: { type: "string", title: "Username Attribute" },
        displayNameAttribute: { type: "string", title: "Display Name Attribute" },
        emailAttribute: { type: "string", title: "Email Attribute" },
        groupBaseDn: { type: "string", title: "Group Base DN" },
        groupFilter: { type: "string", title: "Group Filter" },
        groupNameAttribute: { type: "string", title: "Group Name Attribute" },
        autoCreateUsers: { type: "boolean", title: "Automatically Create Users" },
        adminGroups: { type: "array", title: "Admin Groups", items: { type: "string" } },
      },
    },
  },
}

// The dashboard the provider returns the visitor to: the page it came from, or the fake's own
// dashboard address when the browser sends no referrer.
const dashboardOrigin = (req) => {
  try {
    if (req.get("Referer")) return new URL(req.get("Referer")).origin
  } catch {
    // a malformed referrer falls through to the default below
  }
  return process.env.INGOT_FAKE_UI_ORIGIN || "http://localhost:8888"
}

// Only a path of this dashboard may be the return address; anything else lands on the overview.
const safeReturnPath = (value) =>
  typeof value === "string" && value.startsWith("/") && !value.startsWith("//") ? value : "/"

// The rule of the backend's PublicUrl: blank is not set; otherwise an absolute https address, or http
// for a loopback host, without a query, a fragment or a user name. Returns the rule when it is broken.
const publicUrlProblem = (value) => {
  if (typeof value !== "string" || value.trim() === "") return null
  if (value !== value.trim() || /\s/.test(value)) return PUBLIC_URL_RULE
  let url
  try {
    url = new URL(value)
  } catch {
    return PUBLIC_URL_RULE
  }
  const loopbackHttp = url.protocol === "http:" && LOOPBACK_HOSTS.includes(url.hostname)
  const allowed = url.protocol === "https:" || loopbackHttp
  const broken = !url.hostname || value.includes("?") || value.includes("#") || url.username !== "" || url.password !== ""
  return allowed && !broken ? null : PUBLIC_URL_RULE
}

// GitHub uses its own endpoints for an empty issuer or GitHub's own; a provider then needs a client id
// and a secret. Any other provider needs an issuer and a client id. These are the backend's rules.
const usesGitHub = (provider) => {
  const issuer = String(provider.issuerUrl ?? "").trim()
  return provider.icon === "GITHUB" && (issuer === "" || issuer.replace(/\/$/, "") === GITHUB_ISSUER)
}

const missingField = (provider) => {
  if (usesGitHub(provider)) {
    if (!provider.clientId) return "clientId"
    return provider.clientSecret ? null : "clientSecret"
  }
  if (!String(provider.issuerUrl ?? "").trim()) return "issuerUrl"
  return provider.clientId ? null : "clientId"
}

const ldapMissingField = (ldap) => {
  if (!ldap.url) return "url"
  return ldap.userBaseDn ? null : "userBaseDn"
}

const isText = (value) => typeof value === "string" && value.trim() !== ""

const create = ({ findPrincipal, addPrincipal, startSession, fail, bodyOf, etagOf, setupRequired }) => {
  const router = express.Router()
  let settings = defaultSettings()
  let settingsEtag = etagOf(settings)
  const failedLdapLogins = new Map()

  // Providers that are enabled and have what they need: the ones the login page announces.
  const announcedOidc = () => settings.oidcProviders.filter(provider => provider.enabled && missingField(provider) === null)
  const ldapAnnounced = () => settings.ldap.enabled && ldapMissingField(settings.ldap) === null

  // The order of the backend: the local account first, then the OpenID Connect providers, then LDAP.
  const providerList = () => [
    ...(setupRequired() ? [] : [{ id: "local", type: "LOCAL", label: "Username and password", icon: "LOCAL" }]),
    ...announcedOidc().map(provider => ({
      id: provider.id,
      type: "OIDC",
      label: provider.label || provider.id,
      icon: provider.icon,
      loginUrl: `/api/auth/oidc/${provider.id}/login`,
    })),
    ...(ldapAnnounced() ? [{ id: "ldap", type: "LDAP", label: settings.ldap.label || "LDAP", icon: "LDAP" }] : []),
  ]

  // A stored secret is never sent back: the placeholder stands in for it, as the real server does.
  const publicSettings = () => ({
    ...settings,
    oidcProviders: settings.oidcProviders.map(provider => ({
      ...provider,
      clientSecret: provider.clientSecret ? SECRET_PLACEHOLDER : "",
    })),
    ldap: { ...settings.ldap, bindPassword: settings.ldap.bindPassword ? SECRET_PLACEHOLDER : "" },
  })

  // A write keeps a stored secret when the placeholder comes back, and takes any other value as the new one.
  const keepSecret = (next, current) => (next === SECRET_PLACEHOLDER ? current : next)
  const mergeSettings = (body) => ({
    ...body,
    publicUrl: typeof body.publicUrl === "string" ? body.publicUrl : "",
    oidcProviders: (Array.isArray(body.oidcProviders) ? body.oidcProviders : []).map(provider => {
      const stored = settings.oidcProviders.find(entry => entry.id === provider.id)
      return { ...provider, clientSecret: keepSecret(provider.clientSecret, stored?.clientSecret ?? "") }
    }),
    ldap: {
      ...body.ldap,
      bindPassword: keepSecret(body.ldap?.bindPassword ?? "", settings.ldap.bindPassword),
    },
  })

  const requireAdmin = (req, res, next) => {
    if (!req.actor) return fail(res, 401, "Invalid credentials")
    if (!req.actor.admin) return fail(res, 403, "Instance admin required")
    next()
  }

  // The account a provider sign in acts as. It is created on the first sign in.
  const ensureUser = (name, displayName) => {
    const principal = findPrincipal(name) ?? addPrincipal({ kind: "USER", name, displayName })
    principal.lastSeenAt = new Date().toISOString()
    return principal
  }

  router.get("/api/auth/providers", (req, res) => {
    res.send({ providers: providerList(), tokenLogin: true, setupRequired: setupRequired() })
  })

  // The single sign on start. The fake signs the visitor in at once as a user of the provider, then
  // returns to the page the dashboard sent along. ?fail=<code> returns the failure instead, so that
  // the notice of a failed provider sign in can be seen (the real callback answers with the same codes).
  router.get("/api/auth/oidc/:id/login", (req, res) => {
    const provider = announcedOidc().find(entry => entry.id === req.params.id)
    if (!provider) {
      const configured = settings.oidcProviders.find(entry => entry.id === req.params.id && entry.enabled)
      if (configured) return fail(res, 503, `The login provider is not configured: ${missingField(configured)} is missing`)
      return fail(res, 404, "Unknown login provider")
    }
    const target = `${dashboardOrigin(req)}${safeReturnPath(req.query.redirect)}`
    if (req.query.fail) {
      const [path, query = ""] = target.split("?")
      const separator = query ? "&" : ""
      return res.redirect(`${path}?${query}${separator}loginError=${encodeURIComponent(String(req.query.fail))}`)
    }
    startSession(res, ensureUser(`${provider.id}-user`, `${provider.label || provider.id} user`))
    res.redirect(target)
  })

  // The LDAP sign in of the same route as the local one. Other providers are left to the identity routes.
  router.post("/api/auth/login", (req, res, next) => {
    const { provider, username, password } = bodyOf(req)
    if (provider !== "ldap") return next()
    if (!isText(provider) || !isText(username) || !isText(password)) {
      return fail(res, 400, "Login requires provider, username and password")
    }
    if (!ldapAnnounced()) return fail(res, 400, "Unknown login provider")
    const attempted = username.trim().toLowerCase()
    const correct = username === LDAP_LOGIN.username && password === LDAP_LOGIN.password
    if (!correct) {
      if ((failedLdapLogins.get(attempted) ?? 0) >= MAX_FAILED_LDAP_LOGINS) {
        res.setHeader("Retry-After", String(LDAP_RETRY_AFTER_SECONDS))
        return fail(res, 429, "Too many failed login attempts, try again later")
      }
      failedLdapLogins.set(attempted, (failedLdapLogins.get(attempted) ?? 0) + 1)
      return fail(res, 401, "Invalid username or password")
    }
    failedLdapLogins.delete(attempted)
    const principal = ensureUser(LDAP_LOGIN.username, "LDAP User")
    res.send(startSession(res, principal))
  })

  router.get("/api/settings/schema/login", (req, res) => {
    res.send(LOGIN_SCHEMA)
  })

  router.get("/api/settings/domain/login", requireAdmin, (req, res) => {
    res.setHeader("ETag", settingsEtag)
    res.send(publicSettings())
  })

  router.put("/api/settings/domain/login", requireAdmin, (req, res) => {
    const ifMatch = req.get("If-Match")
    if (ifMatch && ifMatch !== settingsEtag) return fail(res, 412, "The settings changed since they were read")
    const merged = mergeSettings(bodyOf(req))
    const problem = publicUrlProblem(merged.publicUrl)
    if (problem) return fail(res, 400, `java.lang.IllegalArgumentException: Invalid login settings: publicUrl: ${problem}`)
    settings = { ...defaultSettings(), ...merged }
    settings.ldap = { ...defaultSettings().ldap, ...settings.ldap }
    settingsEtag = etagOf(settings)
    res.setHeader("ETag", settingsEtag)
    res.send("")
  })

  // Reads the discovery document and the key set of the provider, as the backend does. A missing
  // setting is answered in the report, not as an error; a provider that is not announced is a 404.
  router.post("/api/auth/providers/oidc/:id/test", requireAdmin, (req, res) => {
    const provider = settings.oidcProviders.find(entry => entry.id === req.params.id && entry.enabled)
    if (!provider) return fail(res, 404, "Unknown login provider")
    const missing = missingField(provider)
    if (missing) return res.send({ ok: false, error: `The setting ${missing} is missing` })
    if (usesGitHub(provider)) {
      return res.send({ ok: true, endpoints: GITHUB_ENDPOINTS })
    }
    if (provider.issuerUrl.includes("github-bridge")) {
      return res.send({ ok: false, issuer: provider.issuerUrl, error: "discovery answered 404" })
    }
    const issuer = provider.issuerUrl.replace(/\/$/, "")
    res.send({
      ok: true,
      issuer,
      endpoints: {
        authorization: `${issuer}/protocol/openid-connect/auth`,
        token: `${issuer}/protocol/openid-connect/token`,
        jwks: `${issuer}/protocol/openid-connect/certs`,
        userinfo: `${issuer}/protocol/openid-connect/userinfo`,
      },
    })
  })

  // The service bind is the only check without a user name. With one, the user is looked up; a
  // password is then used for a user bind, and the groups come from the group base when it is set.
  router.post("/api/auth/providers/ldap/test", requireAdmin, (req, res) => {
    const { username, password } = bodyOf(req)
    const missing = ldapMissingField(settings.ldap)
    if (missing) return res.send({ ok: false, error: `The setting ${missing} is missing` })
    if (!isText(username)) return res.send({ ok: true })
    if (username.trim() !== LDAP_LOGIN.username) return res.send({ ok: false, error: "No entry matches the user name" })
    const userDn = `uid=${LDAP_LOGIN.username},${settings.ldap.userBaseDn}`
    if (isText(password) && password !== LDAP_LOGIN.password) {
      return res.send({ ok: false, userDn, error: "The user bind with this password was refused" })
    }
    res.send({ ok: true, userDn, groups: settings.ldap.groupBaseDn ? ["ingot-admins", "staff"] : [] })
  })

  return router
}

module.exports = { create }
