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
import {
  LDAP_DEFAULTS,
  MFA_DEFAULTS,
  PROVIDER_TYPES,
  SECRET_PLACEHOLDER,
  callbackPatternFor,
  callbackUrlFor,
  emptyProvider,
  mfaWarnings,
  normalizeLoginSettings,
  providerTypeOf,
  removeProvider,
  registeredBase,
  replaceProvider,
  scopeList,
  validateLdap,
  validateMfa,
  validatePublicUrl,
  validateProvider
} from './loginSettings'

const KEYCLOAK = {
  id: 'keycloak',
  enabled: true,
  label: 'Keycloak',
  icon: 'KEYCLOAK',
  issuerUrl: 'https://sso.example.org/realms/ingot',
  clientId: 'ingot',
  clientSecret: SECRET_PLACEHOLDER,
  scopes: 'openid profile email',
  usernameClaim: 'preferred_username',
  displayNameClaim: 'name',
  emailClaim: 'email',
  groupsClaim: 'groups',
  allowedEmailDomains: [],
  autoCreateUsers: true,
  adminGroups: [],
  mfaAcrValues: [],
  mfaAmrValues: []
}

describe('provider types', () => {
  it('offers the ten kinds of the picker, each with the icon the backend names', () => {
    expect(PROVIDER_TYPES.map(type => type.icon)).toEqual([
      'GITHUB', 'GITLAB', 'GOOGLE', 'MICROSOFT', 'KEYCLOAK', 'AUTHENTIK', 'OKTA', 'AUTH0', 'GITEA', 'GENERIC'
    ])
  })

  it('tells the admin how to register the callback for every kind', () => {
    for (const type of PROVIDER_TYPES) {
      expect(type.register, `${type.label} needs its registration steps`).toMatch(/callback|redirect/i)
    }
  })

  it('prefills a new provider from its kind: id, label, icon and the default scopes, disabled', () => {
    const provider = emptyProvider(providerTypeOf('GITLAB'))

    expect(provider).toMatchObject({
      id: 'gitlab',
      label: 'GitLab',
      icon: 'GITLAB',
      enabled: false,
      scopes: 'openid profile email',
      allowedEmailDomains: [],
      adminGroups: [],
      autoCreateUsers: true
    })
    expect(provider.clientSecret, 'a new provider has no secret yet').toBe('')
  })

  it('gives the generic kind the id oidc and the generic key', () => {
    expect(emptyProvider(providerTypeOf('GENERIC'))).toMatchObject({ id: 'oidc', label: 'Generic OIDC', icon: 'GENERIC' })
  })

  it('suggests an issuer for kinds that have a fixed one and leaves the rest to the admin', () => {
    expect(providerTypeOf('GOOGLE').issuerHint).toBe('https://accounts.google.com')
    expect(providerTypeOf('GITHUB').issuerHint, 'GitHub has no OIDC issuer of its own, so no hint is invented').toBe('')
    expect(providerTypeOf('GITHUB').issuerNote).toMatch(/bridge/i)
  })
})

describe('validateProvider', () => {
  it('accepts a complete provider', () => {
    expect(validateProvider(KEYCLOAK, { takenIds: [] })).toEqual({})
  })

  it('wants a slug id of the backend: lowercase letters, digits and dashes, at most 64 characters', () => {
    expect(validateProvider({ ...KEYCLOAK, id: 'My Provider' }, {}).id).toMatch(/lowercase/)
    expect(validateProvider({ ...KEYCLOAK, id: 'x'.repeat(65) }, {}).id).toBeDefined()
    expect(validateProvider({ ...KEYCLOAK, id: '1st' }, {})).toEqual({})
    expect(validateProvider({ ...KEYCLOAK, id: 'a' }, {})).toEqual({})
  })

  it('refuses an id that another provider already has', () => {
    expect(validateProvider({ ...KEYCLOAK, id: 'github' }, { takenIds: ['github'] }).id).toMatch(/already/)
  })

  it('needs a label, as users read it on the button', () => {
    expect(validateProvider({ ...KEYCLOAK, label: '  ' }, {}).label).toBeDefined()
  })

  it('needs an https issuer, and accepts http only for a loopback address, as the backend does', () => {
    expect(validateProvider({ ...KEYCLOAK, issuerUrl: 'http://sso.example.org' }, {}).issuerUrl).toMatch(/https/)
    expect(validateProvider({ ...KEYCLOAK, issuerUrl: 'not a url' }, {}).issuerUrl).toMatch(/https/)
    expect(validateProvider({ ...KEYCLOAK, issuerUrl: '' }, {}).issuerUrl).toBeDefined()
    expect(validateProvider({ ...KEYCLOAK, issuerUrl: 'http://localhost:8080/realms/ingot' }, {})).toEqual({})
    expect(validateProvider({ ...KEYCLOAK, issuerUrl: 'http://127.0.0.1:8080' }, {})).toEqual({})
  })

  it('needs a client id', () => {
    expect(validateProvider({ ...KEYCLOAK, clientId: '' }, {}).clientId).toBeDefined()
  })

  it('does not need a secret for a public client, because the backend sends none', () => {
    expect(validateProvider({ ...KEYCLOAK, clientSecret: '' }, {})).toEqual({})
  })

  it('needs the secret for GitHub, whose OAuth app is confidential', () => {
    const github = { ...KEYCLOAK, id: 'github', icon: 'GITHUB', issuerUrl: '', scopes: '', clientSecret: '' }

    expect(validateProvider(github, {}).clientSecret).toMatch(/GitHub/)
    expect(validateProvider({ ...github, clientSecret: SECRET_PLACEHOLDER }, {})).toEqual({})
  })

  it('needs no issuer and no openid scope for GitHub, whose endpoints are known', () => {
    const github = { ...KEYCLOAK, id: 'github', icon: 'GITHUB', issuerUrl: '', scopes: '', clientSecret: SECRET_PLACEHOLDER }

    expect(validateProvider(github, {})).toEqual({})
    expect(validateProvider({ ...github, issuerUrl: 'https://github.com/' }, {})).toEqual({})
  })

  it('treats GitHub with an issuer of its own as an OpenID Connect bridge', () => {
    const bridge = { ...KEYCLOAK, id: 'github', icon: 'GITHUB', issuerUrl: 'https://bridge.example.org', scopes: 'profile' }

    expect(validateProvider(bridge, {}).scopes).toMatch(/openid/)
  })

  it('needs the openid scope, without which the provider cannot sign anyone in', () => {
    expect(validateProvider({ ...KEYCLOAK, scopes: 'profile' }, {}).scopes).toMatch(/openid/)
  })

  it('splits the scopes text into its words', () => {
    expect(scopeList('openid  profile\temail')).toEqual(['openid', 'profile', 'email'])
    expect(scopeList('')).toEqual([])
  })

  it('checks each allowed email domain', () => {
    expect(validateProvider({ ...KEYCLOAK, allowedEmailDomains: ['example.org'] }, {})).toEqual({})
    expect(validateProvider({ ...KEYCLOAK, allowedEmailDomains: ['no spaces.org'] }, {}).allowedEmailDomains)
      .toMatch(/example\.org/)
  })
})

describe('callbackUrlFor', () => {
  it('builds the callback under the origin and the base path of the dashboard', () => {
    expect(callbackUrlFor('keycloak', 'https://ingot.example.org/ingot/'))
      .toBe('https://ingot.example.org/ingot/api/auth/oidc/keycloak/callback')
  })

  it('works at the root of the origin too', () => {
    expect(callbackUrlFor('github', 'https://ingot.example.org/'))
      .toBe('https://ingot.example.org/api/auth/oidc/github/callback')
  })
})

describe('validateLdap', () => {
  const ldap = {
    ...LDAP_DEFAULTS,
    enabled: true,
    url: 'ldaps://ldap.example.org:636',
    userBaseDn: 'ou=people,dc=example,dc=org',
    userFilter: '(uid={username})'
  }

  it('defaults to the placeholders of the backend: {username} and {dn}', () => {
    expect(LDAP_DEFAULTS.userFilter).toBe('(uid={username})')
    expect(LDAP_DEFAULTS.groupFilter).toBe('(member={dn})')
  })

  it('accepts a complete directory when it is enabled', () => {
    expect(validateLdap(ldap)).toEqual({})
  })

  it('checks nothing while the directory is disabled', () => {
    expect(validateLdap({ ...LDAP_DEFAULTS, enabled: false })).toEqual({})
  })

  it('needs an ldap or ldaps address', () => {
    expect(validateLdap({ ...ldap, url: 'https://ldap.example.org' }).url).toMatch(/ldaps?:\/\//)
  })

  it('needs the base of the user search and the user filter', () => {
    const problems = validateLdap({ ...ldap, userBaseDn: '', userFilter: '' })

    expect(problems.userBaseDn).toBeDefined()
    expect(problems.userFilter).toBeDefined()
  })

  it('needs the {username} placeholder in the user filter', () => {
    expect(validateLdap({ ...ldap, userFilter: '(uid=alice)' }).userFilter).toMatch(/\{username\}/)
    expect(validateLdap({ ...ldap, userFilter: '(uid={0})' }).userFilter).toMatch(/\{username\}/)
  })

  it('needs {dn} or {username} in the group filter only when a group base is given', () => {
    expect(validateLdap({ ...ldap, groupBaseDn: 'ou=groups,dc=example,dc=org', groupFilter: '(objectClass=groupOfNames)' }).groupFilter).toMatch(/\{dn\}/)
    expect(validateLdap({ ...ldap, groupBaseDn: 'ou=groups,dc=example,dc=org', groupFilter: '(memberUid={username})' })).toEqual({})
    expect(validateLdap({ ...ldap, groupBaseDn: '', groupFilter: '(objectClass=groupOfNames)' })).toEqual({})
  })

  it('needs a bind password when a bind DN is given', () => {
    expect(validateLdap({ ...ldap, bindDn: 'cn=svc,dc=example,dc=org', bindPassword: '' }).bindPassword).toBeDefined()
    expect(validateLdap({ ...ldap, bindDn: 'cn=svc,dc=example,dc=org', bindPassword: SECRET_PLACEHOLDER })).toEqual({})
  })
})

describe('validatePublicUrl', () => {
  it.each([
    'https://repo.example.org/',
    'https://repo.example.org/ingot',
    'http://localhost:8080/',
    'http://127.0.0.1:8080',
    '',
    '   '
  ])('accepts %j, which the backend accepts too', (value) => {
    expect(validatePublicUrl(value)).toBe('')
  })

  it.each([
    'http://repo.example.org/',
    'repo.example.org',
    'ftp://repo.example.org/',
    'https://',
    'https://repo.example.org/?next=/',
    'https://repo.example.org/#top',
    'https://alice:secret@repo.example.org/',
    'https://repo.example.org/with space'
  ])('refuses %j', (value) => {
    expect(validatePublicUrl(value)).toMatch(/https:\/\//)
  })
})

describe('registeredBase and the callback pattern', () => {
  it('uses the public address when it is set and valid, else the dashboard origin', () => {
    expect(registeredBase('https://repo.example.org', 'http://localhost:3000/')).toBe('https://repo.example.org')
    expect(registeredBase('', 'http://localhost:3000/')).toBe('http://localhost:3000/')
    expect(registeredBase('http://repo.example.org', 'http://localhost:3000/')).toBe('http://localhost:3000/')
  })

  it('shows the callback pattern with the id still to come', () => {
    expect(callbackPatternFor('https://repo.example.org')).toBe('https://repo.example.org/api/auth/oidc/<id>/callback')
    expect(callbackPatternFor('https://repo.example.org/')).toBe('https://repo.example.org/api/auth/oidc/<id>/callback')
  })

  it('gives a server answer without a public address an empty one', () => {
    expect(normalizeLoginSettings({}).publicUrl).toBe('')
  })
})

describe('normalizeLoginSettings', () => {
  it('fills a server answer that has no login settings yet with empty lists and the directory defaults', () => {
    const settings = normalizeLoginSettings({})

    expect(settings.oidcProviders).toEqual([])
    expect(settings.ldap).toEqual(LDAP_DEFAULTS)
  })

  it('keeps what the server sent', () => {
    const settings = normalizeLoginSettings({ oidcProviders: [KEYCLOAK], ldap: { ...LDAP_DEFAULTS, enabled: true } })

    expect(settings.oidcProviders).toEqual([KEYCLOAK])
    expect(settings.ldap.enabled).toBe(true)
  })
})

describe('provider list edits', () => {
  const GITHUB = { ...KEYCLOAK, id: 'github' }

  it('replaces a provider in place and keeps the order', () => {
    const list = replaceProvider([KEYCLOAK, GITHUB], { ...GITHUB, label: 'GH' }, 'github')

    expect(list.map(provider => provider.id)).toEqual(['keycloak', 'github'])
    expect(list[1].label).toBe('GH')
  })

  it('appends a new provider', () => {
    const list = replaceProvider([KEYCLOAK], { ...GITHUB }, undefined)

    expect(list.map(provider => provider.id)).toEqual(['keycloak', 'github'])
  })

  it('renames a provider when its id changed', () => {
    const list = replaceProvider([KEYCLOAK], { ...KEYCLOAK, id: 'sso' }, 'keycloak')

    expect(list.map(provider => provider.id)).toEqual(['sso'])
  })

  it('removes a provider by its id and leaves the others', () => {
    expect(removeProvider([KEYCLOAK, GITHUB], 'keycloak').map(provider => provider.id)).toEqual(['github'])
  })
})

describe('second factor policy defaults', () => {
  it('fills a server answer without a policy with every switch off, as the backend has it', () => {
    expect(normalizeLoginSettings({}).mfa).toEqual({
      requiredForAdmins: false,
      requiredForTeams: [],
      graceDays: 14,
      stepUpMinutes: 5,
      passkeyFirstFactor: false
    })
    expect(MFA_DEFAULTS.passkeyFirstFactor, 'the passkey first factor stays off until an admin turns it on').toBe(false)
  })

  it('keeps the policy the server sent', () => {
    const settings = normalizeLoginSettings({ mfa: { ...MFA_DEFAULTS, requiredForAdmins: true, graceDays: 30 } })

    expect(settings.mfa).toEqual({ ...MFA_DEFAULTS, requiredForAdmins: true, graceDays: 30 })
  })
})

describe('second factor policy validation', () => {
  it('accepts the defaults', () => {
    expect(validateMfa(MFA_DEFAULTS)).toEqual({})
  })

  it('refuses grace days outside 0 to 365', () => {
    expect(validateMfa({ ...MFA_DEFAULTS, graceDays: 366 }).graceDays, 'more than a year of grace is refused').toMatch(/0 and 365/)
    expect(validateMfa({ ...MFA_DEFAULTS, graceDays: -1 }).graceDays, 'negative grace is refused').toMatch(/0 and 365/)
  })

  it('refuses step-up minutes outside 1 to 60', () => {
    expect(validateMfa({ ...MFA_DEFAULTS, stepUpMinutes: 0 }).stepUpMinutes, 'zero minutes is refused').toMatch(/1 and 60/)
    expect(validateMfa({ ...MFA_DEFAULTS, stepUpMinutes: 61 }).stepUpMinutes, 'an hour and a minute is refused').toMatch(/1 and 60/)
  })

  it('refuses a blank team name in the team list', () => {
    expect(validateMfa({ ...MFA_DEFAULTS, requiredForTeams: ['developers', ' '] }).requiredForTeams).toMatch(/must not be blank/)
  })
})

describe('break-glass warning of the second factor policy', () => {
  it('warns that the last administrator may need the break-glass token when the admin requirement is turned on', () => {
    const warnings = mfaWarnings({ ...MFA_DEFAULTS, requiredForAdmins: true })

    expect(warnings, 'one warning names the break-glass token').toHaveLength(1)
    expect(warnings[0], 'the warning names the flag of the token').toMatch(/--token/)
    expect(warnings[0], 'the warning names the reset of the factors').toMatch(/reset/i)
  })

  it('shows no warning while the admin requirement is off', () => {
    expect(mfaWarnings(MFA_DEFAULTS), 'nothing is turned on, so nothing to warn about').toEqual([])
  })

  it('does not refuse the save: the warning is not a problem of the policy', () => {
    expect(validateMfa({ ...MFA_DEFAULTS, requiredForAdmins: true }), 'the save goes through with the warning').toEqual({})
  })
})

describe('OpenID Connect trusted values of the second factor', () => {
  it('starts a new provider with no trusted acr or amr values, which trusts nothing', () => {
    const provider = emptyProvider(providerTypeOf('KEYCLOAK'))

    expect(provider.mfaAcrValues, 'no acr value is trusted by default').toEqual([])
    expect(provider.mfaAmrValues, 'no amr value is trusted by default').toEqual([])
  })

  it('refuses a blank trusted acr value that is stored', () => {
    const problems = validateProvider({ ...KEYCLOAK, mfaAcrValues: ['urn:mfa', ''] })

    expect(problems.mfaAcrValues, 'a blank acr value is named on its field').toMatch(/blank/)
  })

  it('refuses a blank trusted amr value that is stored', () => {
    const problems = validateProvider({ ...KEYCLOAK, mfaAmrValues: [' '] })

    expect(problems.mfaAmrValues, 'a blank amr value is named on its field').toMatch(/blank/)
  })

  it('accepts a provider with trusted values', () => {
    expect(validateProvider({ ...KEYCLOAK, mfaAcrValues: ['urn:mfa'], mfaAmrValues: ['mfa', 'otp'] })).toEqual({})
  })
})
