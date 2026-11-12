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

import { randomBytes } from 'node:crypto'
import { buildStylePlugin } from './plugin-fixture.js'
import { resolveJar, startServer } from './server.js'

/*
 * One server for the whole run. Its secrets are generated here, handed to the tests through environment
 * variables of this process and never written into the repository. The first administrator is created the
 * way an operator creates it: with the one-time setup code from the server log. The password account is the
 * one the tests sign in with; the bootstrap token is the one the tests seed data with.
 *
 * The returned function is the global teardown: it stops the server and removes its data directory.
 */

const TOKEN_NAME = 'e2e-admin'
const ADMIN_USER = 'e2e-operator'

const secret = (bytes) => randomBytes(bytes).toString('base64url')

export default async function globalSetup() {
  const tokenSecret = secret(24)
  const adminPassword = secret(24)
  // The style fixture is loaded by the shared server, so the stylesheet spec and the rest of the run see one instance.
  const stylePlugin = buildStylePlugin(resolveJar())
  let server
  try {
    server = await startServer({ tokenName: TOKEN_NAME, tokenSecret, plugins: [stylePlugin.jar] })
  } catch (error) {
    stylePlugin.dispose()
    throw error
  }

  try {
    const setupCode = await server.setupCodeFrom()
    const response = await fetch(`${server.baseUrl}/api/auth/setup`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        setupCode,
        username: ADMIN_USER,
        password: adminPassword,
        displayName: 'E2E operator',
      }),
    })
    if (!response.ok) {
      throw new Error(`completing the setup answered ${response.status}: ${await response.text()}`)
    }
  } catch (error) {
    await server.stop()
    stylePlugin.dispose()
    throw error
  }

  process.env.E2E_BASE_URL = server.baseUrl
  process.env.E2E_TOKEN_NAME = TOKEN_NAME
  process.env.E2E_TOKEN_SECRET = tokenSecret
  process.env.E2E_ADMIN_USER = ADMIN_USER
  process.env.E2E_ADMIN_PASSWORD = adminPassword

  return async () => {
    await server.stop()
    stylePlugin.dispose()
  }
}
