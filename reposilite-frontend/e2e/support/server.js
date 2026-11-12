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

import { spawn } from 'node:child_process'
import { openSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { copyFile, mkdir, mkdtemp, rm } from 'node:fs/promises'
import { basename } from 'node:path'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

/*
 * The server under test: the shaded jar of the backend, started in a fresh data directory on a free loopback
 * port with one bootstrap token. The jar is the one the backend build produces, so the suite tests the bytes
 * the image ships. Nothing outside the temporary directory is written.
 */

const here = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(here, '..', '..', '..')
const LIBS = join(repositoryRoot, 'reposilite-backend', 'build', 'libs')

const READY_ATTEMPTS = 300
const READY_INTERVAL_MS = 200
const STOP_GRACE_MS = 15_000

const pause = (ms) => new Promise(resolvePause => setTimeout(resolvePause, ms))

const resolveJar = () => {
  if (process.env.E2E_JAR) {
    return resolve(process.env.E2E_JAR)
  }
  const candidates = readdirSync(LIBS)
    .filter(file => /^ingot-.*\.jar$/.test(file))
    .map(file => join(LIBS, file))
    .sort((left, right) => statSync(right).mtimeMs - statSync(left).mtimeMs)
  if (candidates.length === 0) {
    throw new Error(`no ingot-<version>.jar in ${LIBS}; build it first with ./gradlew :reposilite-backend:shadowJar`)
  }
  return candidates[0]
}

const freePort = () => new Promise((resolvePort, reject) => {
  const probe = createServer()
  probe.once('error', reject)
  probe.listen(0, '127.0.0.1', () => {
    const { port } = probe.address()
    probe.close(() => resolvePort(port))
  })
})

const tailOf = (file, lines = 40) => {
  try {
    return readFileSync(file, 'utf8').split('\n').slice(-lines).join('\n')
  } catch {
    return '(no server log)'
  }
}

/*
 * Starts the server and waits until its readiness answer is UP. The returned stop() ends the process and removes
 * the data directory. `setupCodeFrom()` reads the one-time setup code the server writes to its log.
 */
const startServer = async ({ tokenName, tokenSecret, plugins = [], env = {} }) => {
  const jar = resolveJar()
  const dataDirectory = await mkdtemp(join(tmpdir(), 'ingot-e2e-'))
  // Plugin jars are copied into the plugins directory of the data directory, where the server looks for them.
  if (plugins.length > 0) {
    await mkdir(join(dataDirectory, 'plugins'))
    await Promise.all(plugins.map(plugin => copyFile(plugin, join(dataDirectory, 'plugins', basename(plugin)))))
  }
  const port = await freePort()
  const baseUrl = `http://127.0.0.1:${port}`
  const logPath = join(dataDirectory, 'server.log')
  const log = openSync(logPath, 'w')
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java'

  const child = spawn(java, [
    '-jar', jar,
    '--working-directory', dataDirectory,
    '--port', String(port),
    '--hostname', '127.0.0.1',
    '--token', `${tokenName}:${tokenSecret}`,
  ], { stdio: ['ignore', log, log], env: { ...process.env, ...env } })

  const stop = async () => {
    if (child.exitCode === null && child.signalCode === null) {
      await new Promise((resolveExit) => {
        const kill = setTimeout(() => child.kill('SIGKILL'), STOP_GRACE_MS)
        child.once('exit', () => {
          clearTimeout(kill)
          resolveExit()
        })
        child.kill('SIGTERM')
      })
    }
    await rm(dataDirectory, { recursive: true, force: true })
  }

  const failStart = async (reason) => {
    await stop()
    throw new Error(`${reason}\n--- server log tail ---\n${tailOf(logPath)}`)
  }

  for (let attempt = 0; attempt < READY_ATTEMPTS; attempt++) {
    if (child.exitCode !== null) {
      await failStart(`the server exited with code ${child.exitCode} before it was ready`)
    }
    try {
      const ready = await fetch(`${baseUrl}/api/status/ready`)
      if (ready.ok) {
        const setupCodeFrom = async () => {
          for (let search = 0; search < READY_ATTEMPTS; search++) {
            const match = /setup code: (\S+)/.exec(readFileSync(logPath, 'utf8'))
            if (match) {
              return match[1]
            }
            await pause(READY_INTERVAL_MS)
          }
          return failStart('the server did not print a setup code')
        }
        return { baseUrl, port, dataDirectory, setupCodeFrom, stop }
      }
    } catch {
      // The port is not answering yet. The next attempt asks again.
    }
    await pause(READY_INTERVAL_MS)
  }
  return failStart(`the server was not ready after ${READY_ATTEMPTS} attempts`)
}

export { startServer, resolveJar }
