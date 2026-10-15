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
import { detailOf } from './accessPrincipals'

/*
 * The principals of one kind for a list page. The list endpoint answers with the facts every
 * row needs; the instance admin role, the teams and the tokens (and with them the last use of a
 * machine account) are only in the detail, so the details are read once the list is on screen
 * and the rows fill in as they arrive. A detail that cannot be read leaves its row without those
 * facts rather than failing the page.
 *
 * `getApi` returns { list(kind), get(name) } of the identity client. A load that is overtaken by
 * a newer one is dropped, so the page always shows the answer to the last question.
 */
export function usePrincipalList(kind, getApi) {
  const principals = ref([])
  const details = ref({})
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref('')

  let latest = 0

  const readDetails = async (api, list, request) => {
    const answers = await Promise.allSettled(list.map(principal => api.get(principal.name)))
    if (request !== latest) return
    const next = {}
    answers.forEach((answer, index) => {
      if (answer.status === 'fulfilled') {
        next[list[index].name] = detailOf(answer.value.data)
      }
    })
    details.value = next
  }

  const load = async () => {
    const request = ++latest
    const api = getApi()
    loading.value = true
    loadError.value = ''
    try {
      const response = await api.list(kind)
      if (request !== latest) return
      principals.value = response.data ?? []
      loaded.value = true
      loading.value = false
      await readDetails(api, principals.value, request)
    } catch (error) {
      if (request === latest) loadError.value = errorMessage(error)
    } finally {
      if (request === latest) loading.value = false
    }
  }

  return { principals, details, loaded, loading, loadError, load }
}
