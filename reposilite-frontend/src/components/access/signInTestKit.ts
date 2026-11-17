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

import { ref } from 'vue'
import { createConfigurationStore } from '../../store/configuration'
import { authenticationServer } from './authenticationTestKit'
import authenticationSchema from '../settings/fixtures/authentication.schema.json'
// A subset of the schema of the login domain, copied from the properties of LoginSettings that the sign-in page draws.
import loginSchema from '../settings/fixtures/login.schema.json'

/*
 * Fixtures for the two settings domains of the sign-in protection page: `authentication` (brute force protection)
 * and `login` (account throttle, password policy, idle timeout). Every call builds fresh data, so a test never
 * shares state with another one.
 */
const loginServer = () => ({
  maxFailedAttempts: 5,
  lockoutMinutes: 15,
  accountThrottle: { threshold: 10, quietMinutes: 60, maxDelaySeconds: 300, knownAddressDays: 30 },
  passwordPolicy: {
    minLength: 12,
    breachCheck: { mode: 'OFF', file: '', rangeUrl: 'https://api.pwnedpasswords.com/range/', failClosed: false },
  },
  idleTimeoutMinutes: 0,
})

const schemas: Record<string, unknown> = { authentication: authenticationSchema, login: loginSchema }

const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T

// A settings client that serves both domains. `update` is the write, replaceable by a spy.
const signInClient = ({ update = () => Promise.resolve({ data: {} }) }: { update?: (...args: unknown[]) => Promise<unknown> } = {}) => {
  const servers: Record<string, unknown> = { authentication: authenticationServer(), login: loginServer() }
  return {
    settings: {
      domains: () => Promise.resolve({ data: ['authentication', 'login'] }),
      schema: (name: string) => Promise.resolve({ data: copy(schemas[name]) }),
      fetch: (name: string) => Promise.resolve({ data: copy(servers[name]), headers: { etag: '"v1"' } }),
      update,
    },
  }
}

// A configuration store of its own, to be provided to the views under test.
const signInStore = (client = signInClient()) =>
  createConfigurationStore(ref(client) as unknown as Parameters<typeof createConfigurationStore>[0])

export { signInClient, signInStore, loginServer }
