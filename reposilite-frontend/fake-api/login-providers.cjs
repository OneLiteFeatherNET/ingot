/*
 * The sign in providers of the fake backend: the list that the sign in dialog reads, the single sign
 * on logins (which sign the visitor in at once), the LDAP sign in, the two test endpoints, and the
 * `login` settings domain with its entity tag. Mounted by identity.cjs, after its CSRF and actor
 * middleware, so that these routes share the cookie and CORS handling of the identity routes.
 */

const express = require("express")

const SECRET_PLACEHOLDER = "********"
const LDAP_LOGIN = { username: "ldapuser", password: "ldap-password-12" }
const MAX_FAILED_LDAP_LOGINS = 5
const LDAP_RETRY_AFTER_SECONDS = 900

// The sample providers. GitHub is a sample of a kind without an issuer of its own: its test fails, so
// the failure path of the test is reachable in the dashboard without editing this file.
const defaultSettings = () => ({
  oidcProviders: [
    {
      id: "keycloak",
      enabled: true,
      label: "Keycloak",
      icon: "KEYCLOAK",
      issuerUrl: "https://sso.example.org/realms/ingot",
      clientId: "ingot",
      clientSecret: "keycloak-secret",
      scopes: ["openid", "profile", "email"],
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
      scopes: ["openid", "profile", "email"],
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
    userFilter: "(uid={0})",
    usernameAttribute: "uid",
    displayNameAttribute: "cn",
    emailAttribute: "mail",
    groupBaseDn: "ou=groups,dc=example,dc=org",
    groupFilter: "(objectClass=groupOfNames)",
    groupNameAttribute: "cn",
    autoCreateUsers: true,
    adminGroups: ["ingot-admins"],
  },
})

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

const create = ({ findPrincipal, addPrincipal, startSession, fail, bodyOf, etagOf, setupRequired }) => {
  const router = express.Router()
  let settings = defaultSettings()
  let settingsEtag = etagOf(settings)
  const failedLdapLogins = new Map()

  const providerList = () => [
    { id: "local", type: "LOCAL", label: "Ingot account", icon: "GENERIC" },
    ...(settings.ldap.enabled ? [{ id: "ldap", type: "LDAP", label: settings.ldap.label, icon: "GENERIC" }] : []),
    ...settings.oidcProviders
      .filter(provider => provider.enabled)
      .map(provider => ({
        id: provider.id,
        type: "OIDC",
        label: provider.label,
        icon: provider.icon,
        loginUrl: `/api/auth/oidc/${provider.id}/login`,
      })),
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
  // the notice of a failed provider sign in can be seen.
  router.get("/api/auth/oidc/:id/login", (req, res) => {
    const provider = settings.oidcProviders.find(entry => entry.id === req.params.id && entry.enabled)
    if (!provider) return fail(res, 404, "The provider is not configured")
    const target = `${dashboardOrigin(req)}${safeReturnPath(req.query.redirect)}`
    if (req.query.fail) {
      const [path, query = ""] = target.split("?")
      const separator = query ? "&" : ""
      return res.redirect(`${path}?${query}${separator}loginError=${encodeURIComponent(String(req.query.fail))}`)
    }
    startSession(res, ensureUser(`${provider.id}-user`, `${provider.label} user`))
    res.redirect(target)
  })

  // The LDAP sign in of the same route as the local one. Other providers are left to the identity routes.
  router.post("/api/auth/login", (req, res, next) => {
    const { provider, username, password } = bodyOf(req)
    if (provider !== "ldap") return next()
    if (!settings.ldap.enabled) return fail(res, 400, "The LDAP provider is not enabled")
    const attempted = typeof username === "string" ? username.trim().toLowerCase() : ""
    const correct = username === LDAP_LOGIN.username && password === LDAP_LOGIN.password
    if (!correct) {
      if ((failedLdapLogins.get(attempted) ?? 0) >= MAX_FAILED_LDAP_LOGINS) {
        res.setHeader("Retry-After", String(LDAP_RETRY_AFTER_SECONDS))
        return fail(res, 429, "Too many failed attempts")
      }
      failedLdapLogins.set(attempted, (failedLdapLogins.get(attempted) ?? 0) + 1)
      return fail(res, 401, "Invalid credentials")
    }
    failedLdapLogins.delete(attempted)
    const principal = ensureUser(LDAP_LOGIN.username, "LDAP User")
    res.send(startSession(res, principal))
  })

  router.get("/api/settings/domain/login", requireAdmin, (req, res) => {
    res.setHeader("ETag", settingsEtag)
    res.send(publicSettings())
  })

  router.put("/api/settings/domain/login", requireAdmin, (req, res) => {
    const ifMatch = req.get("If-Match")
    if (ifMatch && ifMatch !== settingsEtag) return fail(res, 412, "The settings changed since they were read")
    const body = bodyOf(req)
    settings = { ...defaultSettings(), ...mergeSettings(body) }
    settings.ldap = { ...defaultSettings().ldap, ...settings.ldap }
    settingsEtag = etagOf(settings)
    res.setHeader("ETag", settingsEtag)
    res.send("")
  })

  router.post("/api/auth/providers/oidc/:id/test", requireAdmin, (req, res) => {
    const provider = settings.oidcProviders.find(entry => entry.id === req.params.id)
    if (!provider) return fail(res, 404, "The provider is not configured")
    if (provider.issuerUrl.includes("github-bridge")) {
      return res.send({ ok: false, error: `No discovery document at ${provider.issuerUrl}/.well-known/openid-configuration` })
    }
    res.send({
      ok: true,
      issuer: provider.issuerUrl,
      endpoints: {
        authorization: `${provider.issuerUrl}/protocol/openid-connect/auth`,
        token: `${provider.issuerUrl}/protocol/openid-connect/token`,
        jwks: `${provider.issuerUrl}/protocol/openid-connect/certs`,
      },
    })
  })

  router.post("/api/auth/providers/ldap/test", requireAdmin, (req, res) => {
    const { username, password } = bodyOf(req)
    if (!settings.ldap.url) return res.send({ ok: false, error: "No server address is configured" })
    if (username === undefined || username === "") {
      return res.send({ ok: true })
    }
    if (username !== LDAP_LOGIN.username) {
      return res.send({ ok: false, error: "No user with that name in the user base" })
    }
    if (password !== LDAP_LOGIN.password) {
      return res.send({ ok: false, error: "The password does not match" })
    }
    res.send({
      ok: true,
      userDn: `uid=${LDAP_LOGIN.username},${settings.ldap.userBaseDn}`,
      groups: ["ingot-admins", "staff"],
    })
  })

  return router
}

module.exports = { create }
