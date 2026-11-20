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

import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/*
 * The nexus-import plugin, the real jar the backend build ships beside the server (reposilite-plugins/nexus-import-plugin).
 * The suite copies it into the plugins directory of the server, with the plugin switch on, so the Migration page of the
 * dashboard exists and its first step can be walked. Nothing is written: the jar is only read from the build output.
 */

const here = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(here, '..', '..', '..')
const DEFAULT_JAR = join(repositoryRoot, 'reposilite-test', 'workspace', 'plugins', 'nexus-import-plugin.jar')

/** Returns the nexus-import plugin jar, or fails with the command that builds it. */
const resolveNexusImportJar = (): string => {
  const jar = process.env.E2E_NEXUS_IMPORT_JAR ? resolve(process.env.E2E_NEXUS_IMPORT_JAR) : DEFAULT_JAR
  if (!existsSync(jar)) {
    throw new Error(`no nexus-import-plugin.jar at ${jar}; build it first with ./gradlew :reposilite-plugins:nexus-import-plugin:shadowJar`)
  }
  return jar
}

/** The switch that makes the server load the plugin and show the Migration page. Operators set the same variable. */
const NEXUS_IMPORT_ENV = { INGOT_NEXUS_IMPORT_ENABLED: 'true' }

export { resolveNexusImportJar, NEXUS_IMPORT_ENV }
