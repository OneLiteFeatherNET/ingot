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

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../router/routes'
import AccessDomainCard from './AccessDomainCard.vue'

const shell = { render: () => null }

const makeRouter = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
  })
  await router.push('/settings/maven')
  await router.isReady()
  return router
}

const mountCard = async (props = { domain: 'login', title: 'Sign in' }) => {
  const router = await makeRouter()
  return mount(AccessDomainCard, { props, global: { plugins: [router] } })
}

describe('AccessDomainCard', () => {
  let wrapper

  beforeEach(async () => {
    wrapper = await mountCard()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  const card = () => wrapper.get('[data-test="access-domain"]')

  it('titles the card with the given title as managed under Access', () => {
    expect(wrapper.get('h3').text()).toBe('Sign in is managed under Access')
  })

  it('names the pages of the domain as the text of the card', () => {
    expect(wrapper.get('p').text()).toContain('managed under Access')
  })

  it('links to each page of the domain by its label', () => {
    const labels = wrapper.findAll('li a').map(link => link.text())

    expect(labels).toEqual(['Single sign on', 'LDAP'])
  })

  it('keeps the access-domain-card anchor on the card root', () => {
    expect(card().attributes('data-ingot-component')).toBe('access-domain-card')
  })

  it('is drawn as the low variant of the card, a flat tinted panel on the page', () => {
    expect(card().attributes('data-variant')).toBe('low')
  })

  it('keeps the medium corner radius, the default padding and the outline of the card', () => {
    expect(card().classes()).toContain('rounded-corner-medium')
    expect(card().classes()).toContain('p-4')
    expect(card().classes()).toContain('border-outline-variant')
  })
})
