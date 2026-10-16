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
 * The teams for the list page. One request answers for every row: the list items carry the facts
 * a row shows (description, member and grant counts, whether the team holds the instance admin
 * role), so no detail is read per team. The detail of a team is read by the detail view alone,
 * when its row is opened.
 *
 * `getApi` returns { list() } of the teams client. A load that is overtaken by a newer one is
 * dropped, so the page always shows the answer to the last question.
 */
export function useTeamList(getApi) {
  const teams = ref([])
  const loaded = ref(false)
  const loadError = ref('')

  let latest = 0

  const load = async () => {
    const request = ++latest
    const api = getApi()
    loadError.value = ''
    try {
      const response = await api.list()
      if (request !== latest) return
      teams.value = response.data ?? []
      loaded.value = true
    } catch (error) {
      if (request === latest) loadError.value = errorMessage(error)
    }
  }

  /** Whether the team holds the instance admin role. A list item without the field counts as not holding it. */
  const isInstanceAdmin = (team) => team?.instanceAdmin === true || team?.instanceRole === true

  return { teams, loaded, loadError, load, isInstanceAdmin }
}
