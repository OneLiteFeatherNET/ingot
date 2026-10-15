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
 * The rules of the `login` settings domain: the single sign on providers (OIDC) and the LDAP
 * directory. Nothing here talks to the server; the pages read and write through useLoginSettings.
 */

// What the server sends in place of a stored secret. Sent back unchanged, it keeps the stored value.
const SECRET_PLACEHOLDER = '********'

const PROVIDER_ID_PATTERN = /^[a-z][a-z0-9-]{1,39}$/
const EMAIL_DOMAIN_PATTERN = /^[a-z0-9-]+(\.[a-z0-9-]+)+$/i

// The kinds the picker offers. `icon` is the icon enum of the backend, so a kind and its mark stay
// one value. The scopes are the ones the kind needs to return an identity; the registration text
// says where the admin creates the client at the provider.
const PROVIDER_TYPES = [
  {
    id: 'github',
    icon: 'GITHUB',
    label: 'GitHub',
    issuerHint: '',
    scopes: ['openid', 'profile', 'email'],
    issuerNote: 'GitHub publishes no OIDC issuer of its own. Enter the issuer of an OIDC bridge in front of it.',
    register: 'In GitHub, create an OAuth app under the organization settings, Developer settings. Set the callback URL below as its authorization callback URL.'
  },
  {
    id: 'gitlab',
    icon: 'GITLAB',
    label: 'GitLab',
    issuerHint: 'https://gitlab.com',
    scopes: ['openid', 'profile', 'email'],
    register: 'In GitLab, create an application under your user or group settings, Applications. Tick the openid, profile and email scopes and set the callback URL below as the redirect URI.'
  },
  {
    id: 'google',
    icon: 'GOOGLE',
    label: 'Google',
    issuerHint: 'https://accounts.google.com',
    scopes: ['openid', 'profile', 'email'],
    register: 'In the Google Cloud console, create an OAuth client ID of type Web application and add the callback URL below under authorized redirect URIs.'
  },
  {
    id: 'microsoft',
    icon: 'MICROSOFT',
    label: 'Microsoft',
    issuerHint: 'https://login.microsoftonline.com/<tenant-id>/v2.0',
    scopes: ['openid', 'profile', 'email'],
    register: 'In Microsoft Entra ID, register an application under App registrations, add the callback URL below as a Web redirect URI and create a client secret.'
  },
  {
    id: 'keycloak',
    icon: 'KEYCLOAK',
    label: 'Keycloak',
    issuerHint: 'https://keycloak.example.org/realms/<realm>',
    scopes: ['openid', 'profile', 'email'],
    register: 'In your Keycloak realm, create a client with client authentication switched on and the callback URL below as a valid redirect URI.'
  },
  {
    id: 'authentik',
    icon: 'AUTHENTIK',
    label: 'Authentik',
    issuerHint: 'https://authentik.example.org/application/o/<slug>/',
    scopes: ['openid', 'profile', 'email'],
    register: 'In Authentik, create an OAuth2/OpenID provider with the callback URL below as its redirect URI, and an application that uses it.'
  },
  {
    id: 'okta',
    icon: 'OKTA',
    label: 'Okta',
    issuerHint: 'https://<org>.okta.com',
    scopes: ['openid', 'profile', 'email'],
    register: 'In the Okta admin console, create an OIDC web application and set the callback URL below as its sign-in redirect URI.'
  },
  {
    id: 'auth0',
    icon: 'AUTH0',
    label: 'Auth0',
    issuerHint: 'https://<tenant>.auth0.com/',
    scopes: ['openid', 'profile', 'email'],
    register: 'In the Auth0 dashboard, create a Regular Web Application and add the callback URL below to its Allowed Callback URLs.'
  },
  {
    id: 'gitea',
    icon: 'GITEA',
    label: 'Gitea',
    issuerHint: 'https://gitea.example.org',
    scopes: ['openid', 'profile', 'email'],
    register: 'In Gitea, create an OAuth2 application under Settings, Applications, and set the callback URL below as its redirect URI.'
  },
  {
    id: 'oidc',
    icon: 'GENERIC',
    label: 'Generic OIDC',
    issuerHint: 'https://sso.example.org',
    scopes: ['openid', 'profile', 'email'],
    register: 'At your provider, register the callback URL below as the redirect URI of a confidential OIDC client.'
  }
]

const providerTypeOf = (icon) => PROVIDER_TYPES.find(type => type.icon === icon) ?? PROVIDER_TYPES[PROVIDER_TYPES.length - 1]

// A new provider, prefilled from its kind. The secret is left empty: the admin types it.
const emptyProvider = (type) => ({
  id: type.id,
  enabled: true,
  label: type.label,
  icon: type.icon,
  issuerUrl: '',
  clientId: '',
  clientSecret: '',
  scopes: [...type.scopes],
  usernameClaim: 'preferred_username',
  displayNameClaim: 'name',
  emailClaim: 'email',
  groupsClaim: 'groups',
  allowedEmailDomains: [],
  autoCreateUsers: true,
  adminGroups: []
})

const LDAP_DEFAULTS = {
  enabled: false,
  label: 'LDAP',
  url: '',
  startTls: false,
  bindDn: '',
  bindPassword: '',
  userBaseDn: '',
  userFilter: '(uid={0})',
  usernameAttribute: 'uid',
  displayNameAttribute: 'cn',
  emailAttribute: 'mail',
  groupBaseDn: '',
  groupFilter: '(objectClass=groupOfNames)',
  groupNameAttribute: 'cn',
  autoCreateUsers: true,
  adminGroups: []
}

const isHttpsUrl = (value) => {
  try {
    return new URL(value).protocol === 'https:'
  } catch {
    return false
  }
}

const isLdapUrl = (value) => /^ldaps?:\/\/\S+$/.test(value)

/*
 * The problems of one provider, keyed by field. An empty object means the provider can be saved.
 * A provider that already has a secret keeps it when the field still shows the placeholder.
 */
const validateProvider = (provider, { takenIds = [] } = {}) => {
  const problems = {}
  if (!PROVIDER_ID_PATTERN.test(provider.id)) {
    problems.id = 'Use 2 to 40 lowercase letters, digits or dashes, starting with a letter'
  } else if (takenIds.includes(provider.id)) {
    problems.id = 'Another provider already has this id'
  }
  if (!provider.label.trim()) problems.label = 'Enter a label, as users read it on the sign in button'
  if (!isHttpsUrl(provider.issuerUrl)) problems.issuerUrl = 'Enter the issuer, starting with https://'
  if (!provider.clientId.trim()) problems.clientId = 'Enter the client id from the provider'
  if (!provider.clientSecret) problems.clientSecret = 'Enter the client secret from the provider'
  if (!provider.scopes.includes('openid')) problems.scopes = 'Include the openid scope'
  const badDomain = provider.allowedEmailDomains.find(domain => !EMAIL_DOMAIN_PATTERN.test(domain))
  if (badDomain !== undefined) problems.allowedEmailDomains = `Enter domains such as example.org, not "${badDomain}"`
  return problems
}

const validateLdap = (ldap) => {
  if (!ldap.enabled) return {}
  const problems = {}
  if (!ldap.label.trim()) problems.label = 'Enter a label, as users read it on the sign in button'
  if (!isLdapUrl(ldap.url)) problems.url = 'Enter the address, starting with ldap:// or ldaps://'
  if (!ldap.userBaseDn.trim()) problems.userBaseDn = 'Enter the base DN where users are found'
  if (!ldap.userFilter.trim()) problems.userFilter = 'Enter the filter that finds a user by the typed name'
  if (!ldap.usernameAttribute.trim()) problems.usernameAttribute = 'Enter the attribute that holds the username'
  if (ldap.bindDn.trim() && !ldap.bindPassword) problems.bindPassword = 'Enter the password of the bind DN'
  return problems
}

// The address an admin registers at the provider. The base path is the one the dashboard is served under.
const callbackUrlFor = (id, productionUrl) => {
  const base = productionUrl.endsWith('/') ? productionUrl : `${productionUrl}/`
  return new URL(`api/auth/oidc/${id}/callback`, base).href
}

// The `login` domain as the pages use it: both parts always present, so that a fresh server reads the same.
const normalizeLoginSettings = (data = {}) => ({
  ...data,
  oidcProviders: Array.isArray(data.oidcProviders) ? data.oidcProviders : [],
  ldap: { ...LDAP_DEFAULTS, ...(data.ldap ?? {}) }
})

// Puts a provider into the list: in the place of the one with `previousId`, or at the end.
const replaceProvider = (list, provider, previousId) => {
  const index = previousId === undefined ? -1 : list.findIndex(item => item.id === previousId)
  if (index === -1) return [...list, provider]
  return list.map((item, position) => (position === index ? provider : item))
}

const removeProvider = (list, id) => list.filter(provider => provider.id !== id)

export {
  LDAP_DEFAULTS,
  PROVIDER_TYPES,
  SECRET_PLACEHOLDER,
  callbackUrlFor,
  emptyProvider,
  normalizeLoginSettings,
  providerTypeOf,
  removeProvider,
  replaceProvider,
  validateLdap,
  validateProvider
}
