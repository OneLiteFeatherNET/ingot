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
import { etagOf, isPreconditionFailed } from '../../store/client'
import { errorMessage } from '../../helpers/toast'
import { normalizeLoginSettings } from './loginSettings'

/*
 * The `login` settings domain for the Access pages. The server state is read with the settings
 * client and written with the tag it was read with, the same optimistic write the settings view
 * uses. A write that the server refuses because the domain changed elsewhere sets `conflict`; any
 * other refusal keeps the saved state as it was.
 *
 * `api` returns the settings client (settings.fetch and settings.update), so it is read at the
 * time of each call and never captured.
 */
const cloneJson = (value) => JSON.parse(JSON.stringify(value))

const useLoginSettings = (api) => {
  const settings = ref(normalizeLoginSettings())
  const etag = ref(null)
  const loaded = ref(false)
  const loadError = ref('')
  const saving = ref(false)
  const saveError = ref('')
  const conflict = ref(false)

  const adopt = (response) => {
    settings.value = normalizeLoginSettings(cloneJson(response.data ?? {}))
    etag.value = etagOf(response)
    conflict.value = false
  }

  const load = async () => {
    try {
      adopt(await api().fetch('login'))
      loadError.value = ''
    } catch (error) {
      loadError.value = errorMessage(error)
    } finally {
      loaded.value = true
    }
  }

  // Writes the whole domain. Resolves to true when the server took it and the saved state was read again.
  const save = async (next) => {
    saving.value = true
    saveError.value = ''
    try {
      await api().update('login', cloneJson(next), etag.value)
    } catch (error) {
      if (isPreconditionFailed(error)) {
        conflict.value = true
        saveError.value = 'The login settings changed elsewhere. Reload them, then make the change again.'
      } else {
        saveError.value = `Not saved: ${errorMessage(error)}`
      }
      return false
    } finally {
      saving.value = false
    }
    await load()
    return true
  }

  return { settings, etag, loaded, loadError, saving, saveError, conflict, load, save }
}

export { useLoginSettings }
