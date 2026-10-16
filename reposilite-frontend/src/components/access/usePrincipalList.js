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
 * The principals of one kind for a list page. One request answers for every row: the list items
 * carry the facts a row shows (the instance admin flags, the token count, the login providers and
 * the last seen time), so no detail is read per row. The detail of a principal is read by the
 * detail view alone, when its row is opened.
 *
 * `getApi` returns { list(kind) } of the identity client. A load that is overtaken by a newer one
 * is dropped, so the page always shows the answer to the last question.
 */
export function usePrincipalList(kind, getApi) {
  const principals = ref([])
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref('')

  let latest = 0

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
    } catch (error) {
      if (request === latest) loadError.value = errorMessage(error)
    } finally {
      if (request === latest) loading.value = false
    }
  }

  return { principals, loaded, loading, loadError, load }
}
