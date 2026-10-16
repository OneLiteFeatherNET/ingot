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

/*
 * The settings domains that have a dedicated page under Access. Their settings are edited there, next to the
 * users and teams they affect, so the generic settings form does not offer them as tabs. A domain stays loaded
 * and keeps traveling with the export, the import and the factory reset.
 */
const ACCESS_DOMAINS = {
  login: {
    text: 'The sign in providers and the LDAP directory are managed under Access, next to the users and teams they affect.',
    links: [
      { name: 'access-sso', label: 'Single sign on' },
      { name: 'access-ldap', label: 'LDAP' }
    ]
  }
}

const isAccessDomain = (domain) => Object.prototype.hasOwnProperty.call(ACCESS_DOMAINS, domain)

// The domains the settings tabs offer, in the order the server lists them.
const settingsTabDomains = (domains) => domains.filter(domain => !isAccessDomain(domain))

export { ACCESS_DOMAINS, isAccessDomain, settingsTabDomains }
