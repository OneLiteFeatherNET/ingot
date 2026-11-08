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
import { readDashboardConfig, parseByteSize } from './config.js'

// Every test passes its own environment map, so nothing is read from the process.
describe('dashboard configuration', () => {
  it('uses the container defaults when no variable is set', () => {
    const config = readDashboardConfig({})

    expect(config.backend, 'default backend').toBe('http://ingot-backend:8080')
    expect(config.basePath, 'default base path').toBe('/')
    expect(config.maxUploadBytes, 'default upload size of 1024m').toBe(1024 * 1024 * 1024)
    expect(config.warnings, 'nothing is ignored by default').toEqual([])
  })

  it('accepts an absolute http backend', () => {
    expect(readDashboardConfig({ INGOT_BACKEND: 'http://server:8080' }).backend).toBe('http://server:8080')
  })

  it('accepts an absolute https backend', () => {
    expect(readDashboardConfig({ INGOT_BACKEND: 'https://ingot.internal' }).backend).toBe('https://ingot.internal')
  })

  it('refuses a backend without a scheme, and names the variable', () => {
    expect(() => readDashboardConfig({ INGOT_BACKEND: 'ingot-backend:8080' }), 'a host and port parse as a URL with a scheme of its own')
      .toThrow(/INGOT_BACKEND/)
  })

  it('refuses a backend that is not http or https', () => {
    expect(() => readDashboardConfig({ INGOT_BACKEND: 'ftp://server' })).toThrow(/INGOT_BACKEND/)
  })

  it('refuses a relative backend', () => {
    expect(() => readDashboardConfig({ INGOT_BACKEND: '/server' })).toThrow(/INGOT_BACKEND/)
  })

  it('applies a base path and keeps its trailing slash', () => {
    expect(readDashboardConfig({ INGOT_BASE_PATH: '/ingot/' }).basePath).toBe('/ingot/')
  })

  it('adds the trailing slash a base path is missing', () => {
    expect(readDashboardConfig({ INGOT_BASE_PATH: '/ingot' }).basePath, 'the document base needs the slash').toBe('/ingot/')
  })

  it('refuses a base path without a leading slash, and names the variable', () => {
    expect(() => readDashboardConfig({ INGOT_BASE_PATH: 'ingot/' })).toThrow(/INGOT_BASE_PATH/)
  })

  it('reads the upload size in bytes from a unit suffix', () => {
    expect(readDashboardConfig({ INGOT_MAX_UPLOAD_SIZE: '512m' }).maxUploadBytes).toBe(512 * 1024 * 1024)
  })

  it('refuses an upload size it cannot parse, and names the variable', () => {
    expect(() => readDashboardConfig({ INGOT_MAX_UPLOAD_SIZE: 'a lot' })).toThrow(/INGOT_MAX_UPLOAD_SIZE/)
  })

  it('accepts INGOT_RESOLVER without using it and reports that it is ignored', () => {
    const config = readDashboardConfig({ INGOT_RESOLVER: '127.0.0.11' })

    expect(config.warnings, 'the ignored variable is reported once').toHaveLength(1)
    expect(config.warnings[0]).toContain('INGOT_RESOLVER')
  })
})

describe('byte size parsing', () => {
  it.each([
    ['1024', 1024],
    ['1k', 1024],
    ['2M', 2 * 1024 * 1024],
    ['1g', 1024 * 1024 * 1024],
  ])('parses %s as %i bytes', (input, bytes) => {
    expect(parseByteSize(input)).toBe(bytes)
  })

  it('treats zero as no limit, as nginx does', () => {
    expect(parseByteSize('0')).toBe(Infinity)
  })

  it('returns null for a value with an unknown unit', () => {
    expect(parseByteSize('10x')).toBeNull()
  })
})
