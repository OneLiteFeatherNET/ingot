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
import { pageTitle } from './title'

const SITE = 'Ingot Repository'

describe('pageTitle', () => {
  it('shows the site title alone on overview', () => {
    expect(pageTitle({ name: 'overview', params: {}, meta: { section: 'overview' } }, SITE)).toBe(SITE)
  })

  it('names the repository path being browsed', () => {
    const route = { name: 'browse', params: { qualifier: ['releases', 'gav'] }, meta: { section: 'overview' } }

    expect(pageTitle(route, SITE)).toBe('releases/gav · Ingot Repository')
  })

  it('names the section for a manager section', () => {
    expect(pageTitle({ name: 'tokens', params: {}, meta: { section: 'tokens' } }, SITE)).toBe('Tokens · Ingot Repository')
  })

  it('names the section for a token editor as well', () => {
    expect(pageTitle({ name: 'token', params: { name: 'ci' }, meta: { section: 'tokens' } }, SITE)).toBe('Tokens · Ingot Repository')
  })

  it('names the settings section for an entry of the settings', () => {
    expect(pageTitle({ name: 'settings-entry', params: {}, meta: { section: 'settings' } }, SITE)).toBe('Settings · Ingot Repository')
  })

  it('says the page was not found', () => {
    expect(pageTitle({ name: 'not-found', params: {}, meta: { section: null } }, SITE)).toBe('Not found · Ingot Repository')
  })
})
