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
import { ACCESS_DOMAINS, isAccessDomain, settingsTabDomains } from './accessDomains'

describe('accessDomains', () => {
  it('names the login domain as managed under Access, with the pages it links to', () => {
    expect(isAccessDomain('login')).toBe(true)
    expect(ACCESS_DOMAINS.login.links.map(link => link.name)).toEqual(['access-sso', 'access-ldap'])
  })

  it('leaves every other domain to the generic settings form', () => {
    expect(isAccessDomain('maven')).toBe(false)
    expect(isAccessDomain('constructor')).toBe(false)
  })

  it('takes the managed domains out of the tabs and keeps the order of the others', () => {
    expect(settingsTabDomains(['maven', 'login', 'ingot'])).toEqual(['maven', 'ingot'])
    expect(settingsTabDomains(['login'])).toEqual([])
  })
})
