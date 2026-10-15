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

import { etagOf } from '../../store/client'
import { buildCreateBody as buildPrincipalBody } from '../access/accessPrincipals'
import { grantPayload, manualMembers, teamDetailOf } from '../access/accessTeams'
import { buildCreateBody as buildTokenBody } from '../identity/identityTokens'

/*
 * The identity requests the wizard's access step makes, on top of the identity client
 * (`client.identity`). The wizard's create sequence calls these; each one is a single step that
 * can be retried on its own.
 *
 * The team writes replace a whole list (PUT grants, PUT members), so each one reads the team
 * first and sends the tag it got back as If-Match: the grants and the manual members the team
 * already has are kept, and a change made by someone else in between answers 412 instead of
 * being overwritten. A write that would change nothing is skipped, so a retry after an answer that
 * was lost does not fail on its own earlier success.
 */
export const createAccessPorts = (identity) => {
  const readTeam = async (name) => {
    const response = await identity.teams.get(name)
    return { detail: teamDetailOf(response.data), etag: etagOf(response) }
  }

  return {
    async createTeam(name) {
      await identity.teams.create({ name })
    },

    /* Appends { repository, pathPrefix: null, role } to the grants the team has. */
    async grantTeam(team, { repository, role }) {
      const { detail, etag } = await readTeam(team)
      const existing = detail.grants.map(grantPayload)
      const added = grantPayload({ repository, pathPrefix: null, role })
      const present = existing.some(grant =>
        grant.repository === added.repository && grant.pathPrefix === added.pathPrefix && grant.role === added.role)
      if (present) return
      await identity.teams.setGrants(team, [...existing, added], etag)
    },

    async createMachine(name) {
      await identity.principals.create(buildPrincipalBody({ kind: 'MACHINE', name }))
    },

    /* Machine accounts get no team by themselves: the manual members stay, the machine joins them. */
    async addMachineToTeam(team, machine) {
      const { detail, etag } = await readTeam(team)
      const members = manualMembers(detail)
      if (members.includes(machine)) return
      await identity.teams.setMembers(team, [...members, machine], etag)
    },

    /* The token is named like the machine account, because the token name is the user name of a Basic login. */
    async createMachineToken(machine, scopes) {
      const response = await identity.principalTokens(machine).create(
        buildTokenBody({ name: machine, preset: 'never', scopes })
      )
      return response.data.secret
    }
  }
}
