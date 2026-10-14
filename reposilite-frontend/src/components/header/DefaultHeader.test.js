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

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import DefaultHeader from './DefaultHeader.vue'

// The window scroll position is a plain value each test sets before it mounts the header.
const scroll = vi.hoisted(() => ({ y: 0 }))

vi.mock('@vueuse/core', async (importOriginal) => {
  const { ref: vueRef } = await import('vue')
  const actual = await importOriginal()
  return {
    ...actual,
    useWindowScroll: () => ({ x: vueRef(0), y: vueRef(scroll.y) })
  }
})

// The account menu is not under test here, so the session is a signed out one.
vi.mock('../../store/session', () => ({
  useSession: () => ({
    token: ref({ name: '', secret: '' }),
    isLogged: ref(false),
    logout: vi.fn(),
    login: vi.fn(),
    loginFailureMessage: (error) => String(error)
  })
}))

const mountHeader = () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:qualifier(.*)', component: { render: () => null } }]
  })
  return mount(DefaultHeader, { global: { plugins: [router] } })
}

describe('DefaultHeader scroll state', () => {
  beforeEach(() => {
    scroll.y = 0
  })

  it('keeps the plain surface colour while the page is at the top', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header').classes(), 'the header must not tint before the page moves')
      .toContain('bg-surface')
    expect(wrapper.find('header').classes()).not.toContain('bg-surface-container')
  })

  it('tints the header with the surface container colour once the page scrolls', () => {
    scroll.y = 120
    const wrapper = mountHeader()

    expect(wrapper.find('header').classes()).toContain('bg-surface-container')
    expect(wrapper.find('header').classes(), 'the plain surface is replaced, not kept beside the tint')
      .not.toContain('bg-surface')
  })

  it('keeps the header sticky at the top of the page', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('header').classes()).toEqual(expect.arrayContaining(['sticky', 'top-0']))
  })
})
