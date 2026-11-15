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
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import RailBrand from './RailBrand.vue'

const mountBrand = () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:qualifier(.*)', component: { render: () => null } }]
  })
  return mount(RailBrand, { global: { plugins: [router] } })
}

describe('RailBrand', () => {
  it('fills the corner above the navigation entries with a link to the start page', () => {
    const link = mountBrand().find('a')

    expect(link.exists(), 'the corner is a link, so it is a keyboard stop').toBe(true)
    expect(link.attributes('href'), 'the start page').toBe('/')
  })

  it('names the link after the site, because the logo carries no text', () => {
    const link = mountBrand().find('a')

    expect(link.attributes('aria-label'), 'the link says where it goes').toMatch(/start page$/)
  })

  it('draws the logo as decoration, since the link already has a name', () => {
    const image = mountBrand().find('img')

    expect(image.exists(), 'the logo is drawn').toBe(true)
    expect(image.attributes('alt'), 'the image is hidden from assistive technology').toBe('')
  })
})
