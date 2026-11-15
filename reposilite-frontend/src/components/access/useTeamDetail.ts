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
import type { AxiosError, AxiosResponse } from 'axios'
import { errorMessage } from '../../helpers/toast'
import { etagOf, isPreconditionFailed } from '../../store/client'
import { teamDetailOf, type TeamDetail, type TeamDetailAnswer, type TeamsApi } from './accessTeams'

/** A write of the team: it takes the entity tag and answers with the team as the server has it after the write. */
export type TeamWrite = (etag: string | null) => Promise<AxiosResponse<TeamDetailAnswer>>

/** The outcome of a write or a delete: ok, or the refusal with the server's message. A 412 is a conflict. */
export type WriteResult =
  | { ok: true }
  | { ok: false; conflict?: boolean; status?: number; message: string }

/** The status of the page: the loading state, the team as read, a team that does not exist, or a failed read. */
export type DetailStatus = 'loading' | 'ready' | 'missing' | 'error'

// The status of a failed request, when the failure carries one.
const statusOf = (error: unknown): number | undefined =>
  (error as { response?: { status?: number } } | undefined)?.response?.status

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
export function useTeamDetail(getName: () => string, getApi: () => TeamsApi) {
  const detail = ref<TeamDetail | null>(null)
  const etag = ref<string | null>(null)
  const status = ref<DetailStatus>('loading')
  const failure = ref('')
  const conflict = ref<{ kind: 'team'; name: string } | null>(null)

  let latest = 0
  let pending: TeamWrite | null = null

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
      status.value = statusOf(error) === 404 ? 'missing' : 'error'
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

  const write = async (run: TeamWrite): Promise<WriteResult> => {
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
      if (isPreconditionFailed(error as AxiosError)) {
        pending = run
        conflict.value = { kind: 'team', name: getName() }
        return { ok: false, conflict: true, status: 412, message: errorMessage(error) }
      }
      return { ok: false, status: statusOf(error), message: errorMessage(error) }
    }
  }

  /** Deletes the team. Like write, it returns { ok } or { ok: false, message }. */
  const remove = async (): Promise<WriteResult> => {
    try {
      await getApi().remove(getName())
      return { ok: true }
    } catch (error) {
      return { ok: false, status: statusOf(error), message: errorMessage(error) }
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

  const overwriteConflict = async (): Promise<WriteResult> => {
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
