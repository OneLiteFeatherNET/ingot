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
import { etagOf, isPreconditionFailed } from '../../store/client'
import { teamDetailOf } from './accessTeams'

/*
 * One team for the detail page: the detail, the entity tag that came with it, and the writes that
 * send that tag back as If-Match.
 *
 * `getName` returns the open team and `getApi` returns the teams client ({ get, update, remove,
 * setMembers, grantInstanceAdmin, revokeInstanceAdmin }).
 *
 * write(run) calls run(etag) and takes the answer (a TeamDetailDto) as the new detail. It never
 * throws: it returns { ok: true } or { ok: false, message, status }. A 412 additionally opens the
 * conflict (`conflict`), which is then settled by reloadConflict, overwriteConflict or
 * dismissConflict. Overwriting reads the team again and runs the same write with the fresh tag.
 *
 * A load that is overtaken by a newer one is dropped.
 */
export function useTeamDetail(getName, getApi) {
  const detail = ref(null)
  const etag = ref(null)
  const status = ref('loading')
  const failure = ref('')
  const conflict = ref(null)

  let latest = 0
  let pending = null

  const load = async () => {
    const request = ++latest
    status.value = detail.value ? status.value : 'loading'
    failure.value = ''
    try {
      const response = await getApi().get(getName())
      if (request !== latest) return
      detail.value = teamDetailOf(response.data)
      etag.value = etagOf(response)
      status.value = 'ready'
    } catch (error) {
      if (request !== latest) return
      detail.value = null
      etag.value = null
      status.value = error?.response?.status === 404 ? 'missing' : 'error'
      failure.value = errorMessage(error)
    }
  }

  const reset = () => {
    latest++
    detail.value = null
    etag.value = null
    conflict.value = null
    pending = null
    status.value = 'loading'
  }

  const write = async (run) => {
    try {
      const response = await run(etag.value)
      if (response?.data) detail.value = teamDetailOf(response.data)
      const tag = etagOf(response)
      if (tag) {
        etag.value = tag
      } else {
        // An answer without a tag: read the team again so the next write has a current one.
        await load()
      }
      return { ok: true }
    } catch (error) {
      if (isPreconditionFailed(error)) {
        pending = run
        conflict.value = { kind: 'team', name: getName() }
        return { ok: false, conflict: true, status: 412, message: errorMessage(error) }
      }
      return { ok: false, status: error?.response?.status, message: errorMessage(error) }
    }
  }

  /** Deletes the team. Like write, it returns { ok } or { ok: false, message }. */
  const remove = async () => {
    try {
      await getApi().remove(getName())
      return { ok: true }
    } catch (error) {
      return { ok: false, status: error?.response?.status, message: errorMessage(error) }
    }
  }

  const dismissConflict = () => {
    conflict.value = null
    pending = null
  }

  const reloadConflict = async () => {
    dismissConflict()
    await load()
  }

  const overwriteConflict = async () => {
    const run = pending
    dismissConflict()
    await load()
    if (run && status.value === 'ready') return write(run)
    return { ok: false, message: failure.value }
  }

  return {
    detail,
    etag,
    status,
    failure,
    conflict,
    load,
    reset,
    write,
    remove,
    dismissConflict,
    reloadConflict,
    overwriteConflict
  }
}
