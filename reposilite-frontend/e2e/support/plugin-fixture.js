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

import { spawnSync } from 'node:child_process'
import { cpSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

/*
 * The e2e fixture plugin, built for each run from fixtures/e2e-style against the shaded server jar. It is a real
 * plugin jar that the server loads from its plugins directory, so the suite exercises the same loading path as an
 * operator's plugin. Nothing is written into the repository: the jar lives in a temporary directory that dispose()
 * removes.
 */

const here = dirname(fileURLToPath(import.meta.url))
const FIXTURE = join(here, 'fixtures', 'e2e-style')
const PLUGIN_NAME = 'e2e-style'

const toolPath = (name) => (process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', name) : name)

const run = (tool, args) => {
  const result = spawnSync(toolPath(tool), args, { encoding: 'utf8' })
  if (result.status !== 0) {
    throw new Error(`${tool} failed with ${result.status}: ${result.stderr || result.stdout}`)
  }
}

/** Compiles the fixture against [serverJar] and returns the jar path and the function that removes its directory. */
const buildStylePlugin = (serverJar) => {
  const work = mkdtempSync(join(tmpdir(), 'ingot-e2e-plugin-'))
  const classes = join(work, 'classes')
  mkdirSync(classes)
  try {
    run('javac', ['-cp', serverJar, '-d', classes, join(FIXTURE, 'e2e', 'style', 'E2eStylePlugin.java')])
    cpSync(join(FIXTURE, 'dashboard.css'), join(classes, 'dashboard.css'))
    cpSync(join(FIXTURE, 'META-INF'), join(classes, 'META-INF'), { recursive: true })
    const jar = join(work, `${PLUGIN_NAME}.jar`)
    run('jar', ['--create', '--file', jar, '-C', classes, '.'])
    return { jar, dispose: () => rmSync(work, { recursive: true, force: true }) }
  } catch (error) {
    rmSync(work, { recursive: true, force: true })
    throw error
  }
}

export { buildStylePlugin, PLUGIN_NAME }
