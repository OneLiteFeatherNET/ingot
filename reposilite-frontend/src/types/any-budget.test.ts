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

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { type Dirent, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, relative, sep } from 'node:path'
import { parse } from '@vue/compiler-sfc'
import ts from 'typescript'

/*
 * The budget for explicit any in the typed sources. A file may hold as many any as its entry in any-budget.json
 * allows, no more, and no entry may be higher than the count, so the budget only shrinks. Phase 4 of
 * migrate-dashboard-to-typescript deletes the budget once the count is zero.
 */

const countKeywords = (text: string): number => {
  const source = ts.createSourceFile('budget.ts', text, ts.ScriptTarget.Latest, true)
  let count = 0
  const visit = (node: ts.Node): void => {
    if (node.kind === ts.SyntaxKind.AnyKeyword) {
      count++
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  return count
}

// The any keywords of a .ts file, or of the script blocks of a component that are written in TypeScript.
const countAny = (file: string, text: string): number => {
  if (file.endsWith('.ts')) {
    return countKeywords(text)
  }
  const { descriptor } = parse(text)
  return [descriptor.script, descriptor.scriptSetup]
    .filter((block) => block !== null && (block.lang === 'ts' || block.lang === 'tsx'))
    .reduce((sum, block) => sum + countKeywords(block!.content), 0)
}

const walk = (directory: string): string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry: Dirent) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? walk(path) : [path]
  })

// Problems as readable lines, empty when the sources and the budget agree. Keys are relative to the frontend
// directory with forward slashes, like the baselines of the style gate.
const checkAnyBudget = (srcDir: string, budget: Record<string, number>): string[] => {
  const frontendDir = dirname(srcDir)
  const counts = new Map<string, number>()

  for (const path of walk(srcDir)) {
    if (!path.endsWith('.ts') && !path.endsWith('.vue')) {
      continue
    }
    const file = relative(frontendDir, path).split(sep).join('/')
    const count = countAny(file, readFileSync(path, 'utf8'))
    if (count > 0) {
      counts.set(file, count)
    }
  }

  const problems: string[] = []
  for (const [file, count] of counts) {
    if (!(file in budget)) {
      problems.push(`${file} has ${count} any, no budget entry`)
    } else if (count > budget[file]) {
      problems.push(`${file} has ${count} any, budget ${budget[file]}`)
    }
  }
  for (const [file, allowed] of Object.entries(budget)) {
    const count = counts.get(file) ?? 0
    if (count < allowed) {
      problems.push(`${file} has ${count} any, budget ${allowed}: lower the entry`)
    }
  }
  return problems.sort()
}

// Fixtures are written into a temporary directory of the test's own, so nothing is read from the repository sources.
let src: string

beforeEach(() => {
  src = join(mkdtempSync(join(tmpdir(), 'any-budget-')), 'src')
  mkdirSync(src)
})

afterEach(() => {
  rmSync(dirname(src), { recursive: true, force: true })
})

const write = (file: string, text: string) => {
  mkdirSync(dirname(join(src, file)), { recursive: true })
  writeFileSync(join(src, file), text)
}

describe('any budget', () => {
  it('accepts a file whose count equals its entry', () => {
    write('store/a.ts', 'export const a: any = 1 // any: third-party shape\n')

    expect(checkAnyBudget(src, { 'src/store/a.ts': 1 }), 'no problem for a file at its budget').toEqual([])
  })

  it('reports a file with more any than its entry', () => {
    write('store/a.ts', 'export const a: any = 1\nexport const b = 2 as any\n')

    const problems = checkAnyBudget(src, { 'src/store/a.ts': 1 })

    expect(problems, 'one problem for the file over budget').toHaveLength(1)
    expect(problems[0], 'the problem names the file and both counts').toContain('src/store/a.ts has 2 any, budget 1')
  })

  it('reports a file with any and no entry', () => {
    write('store/a.ts', 'export const a: any = 1\n')

    const problems = checkAnyBudget(src, {})

    expect(problems, 'one problem for the file without an entry').toHaveLength(1)
    expect(problems[0], 'the problem names the file and says it has no entry').toContain('src/store/a.ts has 1 any, no budget entry')
  })

  it('reports an entry above the actual count so that the budget only shrinks', () => {
    write('store/a.ts', 'export const a: any = 1\n')

    const problems = checkAnyBudget(src, { 'src/store/a.ts': 3 })

    expect(problems, 'one problem for the stale entry').toHaveLength(1)
    expect(problems[0], 'the problem asks to lower the entry').toContain('src/store/a.ts has 1 any, budget 3: lower the entry')
  })

  it('reports an entry for a file that no longer exists', () => {
    expect(checkAnyBudget(src, { 'src/store/gone.ts': 1 }), 'a missing file is a stale entry').toEqual([
      'src/store/gone.ts has 0 any, budget 1: lower the entry',
    ])
  })

  it('does not count the word any in a comment or a string', () => {
    write('store/a.ts', "// any value\nexport const a = 'any'\n")

    expect(checkAnyBudget(src, {}), 'only the type keyword counts').toEqual([])
  })

  it('counts any in the typed script block of a component', () => {
    write('components/A.vue', '<script setup lang="ts">\nconst a: any = 1\n</script>\n<template><p>{{ a }}</p></template>\n')

    expect(checkAnyBudget(src, { 'src/components/A.vue': 1 }), 'the any in the script block of the component').toEqual([])
  })

  it('ignores JavaScript files', () => {
    write('store/a.js', '/** @type {any} */\nexport const a = 1\n')

    expect(checkAnyBudget(src, {}), 'JavaScript has no type annotations to count').toEqual([])
  })

  // A full-tree scan: every .ts and .vue file of src/ is read and parsed. That is well above the default test timeout
  // under load, so this one test gets a longer timeout. The global timeout stays as it is.
  it('holds for the committed sources and the committed budget', { timeout: 30_000 }, () => {
    const budget = JSON.parse(readFileSync(join(process.cwd(), 'src', 'types', 'any-budget.json'), 'utf8')) as Record<string, number>

    expect(checkAnyBudget(join(process.cwd(), 'src'), budget), 'the budget file matches the sources').toEqual([])
  })
})
