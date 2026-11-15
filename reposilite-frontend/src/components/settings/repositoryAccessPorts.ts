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
import { manualMembers, teamDetailOf } from '../access/accessTeams'
import { repositoryRoleNodes } from '../access/nodes/nodeTemplates'
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

    /*
     * Grants the role on the repository as permission nodes: the nodes the role needs are added to the
     * node list of the team, and a denied one is switched to allowed. The server refuses a repository-wide
     * grant on the grants endpoint, so this is the only write for a role. A team that holds the nodes
     * already is not written again.
     */
    async grantTeam(team, { repository, role }) {
      const response = await identity.nodes.get('teams', team)
      const etag = etagOf(response)
      const existing = (response.data?.nodes ?? []).map(entry => ({ node: entry.node, allowed: entry.allowed === true }))
      const wanted = repositoryRoleNodes(role, repository)
      const allowedNow = new Set(existing.filter(entry => entry.allowed).map(entry => entry.node))
      const missing = wanted.filter(node => !allowedNow.has(node))
      if (missing.length === 0) return

      const missingSet = new Set(missing)
      const listed = new Set(existing.map(entry => entry.node))
      const kept = existing.map(entry => ({ node: entry.node, allowed: missingSet.has(entry.node) ? true : entry.allowed }))
      const added = missing.filter(node => !listed.has(node)).map(node => ({ node, allowed: true }))
      await identity.nodes.set('teams', team, [...kept, ...added], etag)
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
