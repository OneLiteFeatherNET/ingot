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

import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'

const SCANNED_EXTENSIONS = ['.vue', '.js', '.css']

// The shipped sources under src/, read from disk. Tests and the gate itself are left out, so the gate never scans its
// own fixtures. Paths are relative to the frontend directory, with forward slashes, so that they match the baselines.
export function collectSources(srcDir) {
  const frontendDir = dirname(srcDir)

  return walk(srcDir)
    .map((path) => relative(frontendDir, path).split(sep).join('/'))
    .filter((file) => !file.startsWith('src/style-gate/'))
    .filter((file) => SCANNED_EXTENSIONS.some((extension) => file.endsWith(extension)))
    .filter((file) => !file.endsWith('.test.js'))
    .sort()
    .map((file) => ({ file, text: readFileSync(join(frontendDir, file), 'utf8') }))
}

const walk = (directory) =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? walk(path) : [path]
  })
