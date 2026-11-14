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

import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { compile } from '@tailwindcss/node'

// The class names the stylesheet can produce, read from the compiler and not from a list kept by hand. The compiler
// output holds the utilities built from the theme, the @utility declarations and the plain rules of the sheet, so a
// hook defined there resolves too.

const escapeForPattern = (character) => character.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&')

// The selector Tailwind writes for a candidate: each character that is not a plain identifier character may be
// preceded by a backslash, which is how the compiler escapes ':', '/', '[' and the other punctuation.
const selectorPattern = (token) => {
  const body = [...token]
    .map((character) => (/[\w-]/.test(character) ? character : `\\\\?${escapeForPattern(character)}`))
    .join('')

  return new RegExp(`(?<![\\w.\\-])\\.${body}(?![\\w-])`)
}

// Compiles the stylesheet of the dashboard once per resolver. The compiler is incremental: every build call returns the
// css of all candidates seen so far, so each token is built once and its answer is kept.
export async function createResolver(srcDir) {
  const css = readFileSync(join(srcDir, 'style.css'), 'utf8')
  const compiler = await compile(css, { base: srcDir, onDependency() {} })
  const answers = new Map()

  return {
    resolves(token) {
      if (!answers.has(token)) {
        answers.set(token, buildsSelector(compiler, token))
      }
      return answers.get(token)
    }
  }
}

const buildsSelector = (compiler, token) => {
  try {
    return selectorPattern(token).test(compiler.build([token]))
  } catch {
    return false
  }
}
