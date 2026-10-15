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
import { errorMessage } from '../../helpers/toast'

/*
 * The teams for the list page. The list endpoint answers with the facts of a row (name,
 * description, counts). Whether a team holds the instance admin role is only certain in the
 * detail, so a team whose list entry does not say is read once the list is on screen, and its
 * chip fills in as the answer arrives. A detail that cannot be read leaves its row without the
 * chip rather than failing the page.
 *
 * `getApi` returns { list(), get(name) } of the teams client. A load that is overtaken by a newer
 * one is dropped, so the page always shows the answer to the last question.
 */
export function useTeamList(getApi) {
  const teams = ref([])
  const instanceAdmins = ref({})
  const loaded = ref(false)
  const loadError = ref('')

  let latest = 0

  const readAdmins = async (api, list, request) => {
    const answers = await Promise.allSettled(list.map(team => api.get(team.name)))
    if (request !== latest) return
    const next = {}
    answers.forEach((answer, index) => {
      if (answer.status === 'fulfilled') next[list[index].name] = answer.value.data?.instanceRole === true
    })
    instanceAdmins.value = next
  }

  const load = async () => {
    const request = ++latest
    const api = getApi()
    loadError.value = ''
    try {
      const response = await api.list()
      if (request !== latest) return
      teams.value = response.data ?? []
      loaded.value = true
      const unknown = teams.value.filter(team => typeof team.instanceRole !== 'boolean')
      await readAdmins(api, unknown, request)
    } catch (error) {
      if (request === latest) loadError.value = errorMessage(error)
    }
  }

  const isInstanceAdmin = (team) =>
    typeof team.instanceRole === 'boolean' ? team.instanceRole : instanceAdmins.value[team.name] === true

  return { teams, loaded, loadError, load, isInstanceAdmin }
}
