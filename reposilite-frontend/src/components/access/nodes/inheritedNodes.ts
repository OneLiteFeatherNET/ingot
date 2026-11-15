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
 * The walk up the team tree that finds the nodes a subject inherits. Pure: the caller hands in
 * `fetchTeam(name)`, which resolves with { nodes: [{ node, allowed }], parents: [teamName] } and
 * rejects for a team that cannot be read.
 *
 * The walk goes level by level, so the nearest team comes first. `start` are the teams of the first
 * level and `startDepth` is their depth: 0 for the teams a principal belongs to (source TEAM) and 1
 * for the parents of a team (source INHERITED_TEAM). Each further level is one step up. A team is
 * read once, at its nearest depth, so a cycle or a diamond ends. No team deeper than `maxDepth`
 * is read, the limit of the server.
 *
 * Returns { entries, errors }. `entries` is flat, [{ node, allowed, source, subject, depth }].
 * A team that cannot be read gives an entry in `errors`, [{ team, message }], and its parents are
 * not followed.
 */

const MAX_TEAM_DEPTH = 5

/** What fetchTeam resolves with: the nodes of one team and the names of its parents. */
export interface TeamNodes {
  nodes?: { node: string; allowed?: boolean }[] | null
  parents?: string[] | null
}

/** One node a subject inherits, with the team it came from and how far up that team sits. */
export interface InheritedEntry {
  node: string
  allowed: boolean
  source: 'TEAM' | 'INHERITED_TEAM'
  subject: string
  depth: number
}

export interface CollectInput {
  start?: string[] | null
  startDepth?: number
  fetchTeam: (name: string) => Promise<TeamNodes>
  maxDepth?: number
}

const keyOf = (name: string): string => String(name).toLowerCase()

const messageOf = (error: unknown): string => (error as { message?: string } | null | undefined)?.message ?? String(error)

const collectInheritedNodes = async ({ start, startDepth = 1, fetchTeam, maxDepth = MAX_TEAM_DEPTH }: CollectInput): Promise<{ entries: InheritedEntry[]; errors: { team: string; message: string }[] }> => {
  const entries: InheritedEntry[] = []
  const errors: { team: string; message: string }[] = []
  const visited = new Set<string>()
  let level = start ?? []
  let depth = startDepth

  while (level.length > 0 && depth <= maxDepth) {
    const fresh: string[] = []
    for (const name of level) {
      if (visited.has(keyOf(name))) continue
      visited.add(keyOf(name))
      fresh.push(name)
    }
    const results = await Promise.all(fresh.map(async (name): Promise<{ name: string; team?: TeamNodes; error?: unknown }> => {
      try {
        return { name, team: await fetchTeam(name) }
      } catch (error) {
        return { name, error }
      }
    }))
    const next: string[] = []
    for (const { name, team, error } of results) {
      if (error) {
        errors.push({ team: name, message: messageOf(error) })
        continue
      }
      for (const entry of team?.nodes ?? []) {
        entries.push({
          node: entry.node,
          allowed: entry.allowed === true,
          source: depth === 0 ? 'TEAM' : 'INHERITED_TEAM',
          subject: name,
          depth
        })
      }
      next.push(...(team?.parents ?? []))
    }
    level = next
    depth++
  }

  return { entries, errors }
}

export { MAX_TEAM_DEPTH, collectInheritedNodes }
