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
import { errorMessage } from '../../../helpers/toast'
import { collectInheritedNodes } from './inheritedNodes'

/*
 * The nodes a subject inherits from teams, read for the editor's inherited section.
 *
 * `getSource` returns { teams, depth }: the names of the first level and their depth (0 for the
 * teams a principal belongs to, 1 for the parents of a team). `getApi` returns the api of
 * teamParentsApi; it is only asked for when there is a team to read.
 *
 * entries   [{ node, allowed, source, subject, depth }]
 * warnings  one line per team that could not be read; the rest still counts
 * status    idle (nothing to read), loading or ready
 *
 * Nothing throws. A load that a newer load overtook is dropped.
 */
export function useInheritedNodes(getSource, getApi) {
  const entries = ref([])
  const warnings = ref([])
  const status = ref('idle')

  let latest = 0

  const load = async () => {
    const request = ++latest
    const { teams, depth } = getSource()
    if (!teams || teams.length === 0) {
      entries.value = []
      warnings.value = []
      status.value = 'idle'
      return
    }
    status.value = 'loading'
    try {
      const api = getApi()
      const fetchTeam = async (name) => {
        try {
          return await api.team(name)
        } catch (error) {
          throw new Error(errorMessage(error), { cause: error })
        }
      }
      const result = await collectInheritedNodes({ start: teams, startDepth: depth, fetchTeam })
      if (request !== latest) return
      entries.value = result.entries
      warnings.value = result.errors.map(error => `Could not read the nodes of team ${error.team}: ${error.message}`)
    } catch (error) {
      if (request !== latest) return
      entries.value = []
      warnings.value = [`Could not read the inherited nodes: ${errorMessage(error)}`]
    }
    status.value = 'ready'
  }

  const reset = () => {
    latest++
    entries.value = []
    warnings.value = []
    status.value = 'idle'
  }

  return { entries, warnings, status, load, reset }
}
