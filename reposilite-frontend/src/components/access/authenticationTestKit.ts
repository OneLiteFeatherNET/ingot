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
// The schema the backend serves for the authentication domain, copied from its generated output.
import authenticationSchema from '../settings/fixtures/authentication.schema.json'

/*
 * Fixtures for the authentication domain as the Access pages read it. Every call builds fresh data, so a test
 * never shares state with another one.
 */
const authenticationServer = () => ({
  bruteForceProtection: { enabled: false, maxAttempts: 10, banDurationSeconds: 300 },
  ldap: {
    enabled: false,
    ssl: false,
    hostname: 'ldap.domain.com',
    port: 389,
    baseDn: 'dc=company,dc=com',
    searchUserDn: 'cn=reposilite,ou=admins,dc=domain,dc=com',
    searchUserPassword: '********',
    typeAttribute: 'person',
    userAttribute: 'cn',
    userFilter: '(&(objectClass=person)(ou=Maven Users))',
    userType: 'PERSISTENT'
  }
})

// A settings client that serves the authentication domain only. `update` is the write, replaceable by a spy.
const authenticationClient = ({ server = authenticationServer(), update = async () => ({ data: {} }) } = {}) => ({
  settings: {
    domains: async () => ({ data: ['authentication'] }),
    schema: async () => ({ data: JSON.parse(JSON.stringify(authenticationSchema)) }),
    fetch: async () => ({ data: JSON.parse(JSON.stringify(server)), headers: { etag: '"v1"' } }),
    update
  }
})

// A configuration store of its own, to be provided to the views under test.
const authenticationStore = (client = authenticationClient()) => createConfigurationStore(ref(client))

export { authenticationClient, authenticationServer, authenticationStore }
