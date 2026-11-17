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
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

// The named transition classes are read from disk, as the other stylesheet tests do: vitest does not process
// stylesheets for unit tests. Every test reads the files again, so no state carries from one test to another.
const readStyles = (name: string) => readFileSync(join(process.cwd(), 'src', 'styles', 'md3', name), 'utf8')

const PATTERNS = ['fade-through', 'shared-x', 'shared-y', 'menu', 'dialog', 'sheet', 'snackbar', 'expand', 'fade']

// Every declaration of the file as { property, value }, comments removed first.
const declarationsOf = (css: string) => {
  const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '')
  return [...withoutComments.matchAll(/([a-z-]+)\s*:\s*([^;{}]+);/g)].map((match) => ({
    property: match[1],
    value: match[2].trim(),
  }))
}

// The declarations that set a time: a transition, an animation and their durations.
const timingOf = (css: string) =>
  declarationsOf(css).filter(({ property }) =>
    ['transition', 'transition-duration', 'animation', 'animation-duration'].includes(property)
  )

const LITERAL_TIME = /(?<![\w-])\d*\.?\d+(ms|s)\b/

describe('named transition classes', () => {
  const css = readStyles('motion-transitions.css')

  it.each(PATTERNS)('declares the enter and leave classes of the %s pattern', (pattern) => {
    expect(css, `a .motion-${pattern}-enter-active class`).toContain(`.motion-${pattern}-enter-active`)
    expect(css, `a .motion-${pattern}-leave-active class`).toContain(`.motion-${pattern}-leave-active`)
  })

  it('finds a timing declaration in the file', () => {
    expect(timingOf(css).length, 'the transition declarations of the patterns').toBeGreaterThan(0)
  })

  it('takes every duration from a motion duration token', () => {
    for (const { property, value } of timingOf(css)) {
      expect(value, `${property}: ${value} names a motion duration token`).toMatch(/var\(--md-sys-motion-duration-[a-z0-9-]+\)/)
    }
  })

  it('holds no literal duration in any transition or animation declaration', () => {
    for (const { property, value } of timingOf(css)) {
      expect(value, `${property}: ${value} holds no literal time such as 100ms or .1s`).not.toMatch(LITERAL_TIME)
    }
  })

  it('takes every easing of a transition from a motion easing token', () => {
    for (const { property, value } of timingOf(css).filter((declaration) => declaration.property === 'transition')) {
      expect(value, `${property}: ${value} names a motion easing token`).toMatch(/var\(--md-sys-motion-easing-[a-z-]+\)/)
    }
  })

  it('refers only to tokens that the motion foundation declares', () => {
    const foundation = readStyles('shape-elevation-motion.css')
    const used = [...css.matchAll(/var\((--md-sys-motion-[a-z0-9-]+)\)/g)].map((match) => match[1])

    expect(used.length, 'the file refers to motion tokens').toBeGreaterThan(0)
    for (const name of new Set(used)) {
      expect(foundation, `${name} is declared in shape-elevation-motion.css`).toContain(`${name}:`)
    }
  })
})
