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
import useTheme from './theme'

const THEME_KEY = 'theme-mode'

// jsdom has no matchMedia. The stub answers only the dark-scheme query, and only with the
// preference the test sets, so nothing reads the real system settings.
const stubSystemPreference = (prefersDark) => {
  window.matchMedia = (query) => ({
    matches: prefersDark && query === '(prefers-color-scheme: dark)',
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {}
  })
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    delete window.matchMedia
    localStorage.clear()
  })

  it('resolves a stored dark mode to a dark theme', () => {
    localStorage.setItem(THEME_KEY, 'dark')
    stubSystemPreference(false)

    const { theme, fetchColorMode } = useTheme()
    fetchColorMode()

    expect(theme.mode).toBe('dark')
    expect(theme.isDark).toBe(true)
  })

  it('resolves a stored light mode to a light theme even when the system prefers dark', () => {
    localStorage.setItem(THEME_KEY, 'light')
    stubSystemPreference(true)

    const { theme, fetchColorMode } = useTheme()
    fetchColorMode()

    expect(theme.mode).toBe('light')
    expect(theme.isDark).toBe(false)
  })

  it('follows a system preference for dark in auto mode', () => {
    localStorage.setItem(THEME_KEY, 'auto')
    stubSystemPreference(true)

    const { theme, fetchColorMode } = useTheme()
    fetchColorMode()

    expect(theme.isDark).toBe(true)
  })

  it('follows a system preference for light in auto mode', () => {
    localStorage.setItem(THEME_KEY, 'auto')
    stubSystemPreference(false)

    const { theme, fetchColorMode } = useTheme()
    fetchColorMode()

    expect(theme.isDark).toBe(false)
  })

  it('defaults to auto mode and stores that default when nothing is saved', () => {
    stubSystemPreference(false)

    const { theme, fetchColorMode } = useTheme()
    fetchColorMode()

    expect(theme.mode).toBe('auto')
    expect(localStorage.getItem(THEME_KEY)).toBe('auto')
  })

  it('changeTheme stores the new mode and applies it', () => {
    stubSystemPreference(false)

    const { theme, changeTheme } = useTheme()
    changeTheme('dark')

    expect(localStorage.getItem(THEME_KEY)).toBe('dark')
    expect(theme.isDark).toBe(true)
  })
})
