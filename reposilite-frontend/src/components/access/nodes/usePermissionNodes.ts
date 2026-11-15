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

import { computed, ref } from 'vue'
import { errorMessage } from '../../../helpers/toast'
import { isPreconditionFailed } from '../../../store/client'
import { diffNodes } from './nodeList'

/*
 * The permission nodes of one subject for the editor: the saved list, the entity tag that came with
 * it, the draft the user edits, and the save that sends the draft back as If-Match.
 *
 * `getSubject` returns { kind: 'principal' | 'team', name } and `getApi` returns the object from
 * permissionNodesApi ({ load, save, catalog, templates }).
 *
 * save() never throws: it returns { ok: true } or { ok: false, message, status }. A 400 maps the
 * problems of the server to `rowErrors` (node text to message); a problem that names no listed node
 * goes to `formError`. A 412 opens the conflict (`conflict`, kind 'nodes') for the existing conflict
 * dialog, which is then settled by reloadConflict (take the stored list, drop the draft),
 * overwriteConflict (keep the draft and write it over the stored list) or dismissConflict.
 *
 * A load that is overtaken by a newer one is dropped. Nothing here waits on a timer.
 */
const copy = (rows) => rows.map(row => ({ node: row.node, allowed: row.allowed }))

export function usePermissionNodes(getSubject, getApi) {
  const saved = ref([])
  const draft = ref([])
  const etag = ref(null)
  const status = ref('loading')
  const failure = ref('')
  const saving = ref(false)
  const rowErrors = ref({})
  const formError = ref('')
  const conflict = ref(null)
  const catalog = ref([])
  const templates = ref([])

  let latest = 0
  let catalogLoaded = false

  const changes = computed(() => diffNodes(saved.value, draft.value))
  const changeCount = computed(() => changes.value.count)
  const dirty = computed(() => changeCount.value > 0)

  const clearErrors = () => {
    rowErrors.value = {}
    formError.value = ''
  }

  const adopt = (result) => {
    saved.value = copy(result.nodes)
    draft.value = copy(result.nodes)
    etag.value = result.etag
    clearErrors()
  }

  const load = async () => {
    const request = ++latest
    const { kind, name } = getSubject()
    if (status.value !== 'ready') status.value = 'loading'
    failure.value = ''
    try {
      const result = await getApi().load(kind, name)
      if (request !== latest) return
      adopt(result)
      status.value = 'ready'
    } catch (error) {
      if (request !== latest) return
      saved.value = []
      draft.value = []
      etag.value = null
      status.value = error?.response?.status === 404 ? 'missing' : 'error'
      failure.value = errorMessage(error)
    }
  }

  /** Loads the catalog and the templates once. A failure leaves them empty: free text still works. */
  const loadCatalog = async () => {
    if (catalogLoaded) return
    catalogLoaded = true
    const api = getApi()
    const [catalogResult, templatesResult] = await Promise.allSettled([api.catalog(''), api.templates()])
    if (catalogResult.status === 'fulfilled') catalog.value = catalogResult.value
    if (templatesResult.status === 'fulfilled') templates.value = templatesResult.value
  }

  const reset = () => {
    latest++
    saved.value = []
    draft.value = []
    etag.value = null
    conflict.value = null
    saving.value = false
    clearErrors()
    status.value = 'loading'
  }

  /** Replaces the draft. An error that belonged to a node no longer listed goes away. */
  const setDraft = (rows) => {
    draft.value = copy(rows)
    const listed = new Set(draft.value.map(row => row.node))
    rowErrors.value = Object.fromEntries(Object.entries(rowErrors.value).filter(([node]) => listed.has(node)))
  }

  const discard = () => {
    draft.value = copy(saved.value)
    clearErrors()
  }

  const mapProblems = (problems) => {
    const listed = new Set(draft.value.map(row => row.node))
    const errors = {}
    const others = []
    for (const problem of problems) {
      if (listed.has(problem.node)) {
        if (!(problem.node in errors)) errors[problem.node] = problem.message
      } else {
        others.push(problem.message)
      }
    }
    rowErrors.value = errors
    formError.value = others.join('\n')
  }

  const save = async () => {
    const { kind, name } = getSubject()
    saving.value = true
    clearErrors()
    try {
      const sent = copy(draft.value)
      let result = await getApi().save(kind, name, sent, etag.value)
      if (!result.etag) {
        // An answer without a tag: read the list again so the next write has a current one.
        const fresh = await getApi().load(kind, name)
        result = { nodes: result.nodes, etag: fresh.etag }
      }
      adopt(result)
      return { ok: true }
    } catch (error) {
      const code = error?.response?.status
      if (isPreconditionFailed(error)) {
        conflict.value = { kind: 'nodes', name }
        return { ok: false, conflict: true, status: code, message: errorMessage(error) }
      }
      if (code === 400 && Array.isArray(error.response?.data?.problems)) {
        mapProblems(error.response.data.problems)
      }
      return { ok: false, status: code, message: errorMessage(error) }
    } finally {
      saving.value = false
    }
  }

  const dismissConflict = () => {
    conflict.value = null
  }

  const reloadConflict = async () => {
    dismissConflict()
    await load()
  }

  const overwriteConflict = async () => {
    dismissConflict()
    const { kind, name } = getSubject()
    try {
      const fresh = await getApi().load(kind, name)
      // The stored list becomes the baseline, the draft stays as the user left it.
      saved.value = copy(fresh.nodes)
      etag.value = fresh.etag
    } catch (error) {
      return { ok: false, status: error?.response?.status, message: errorMessage(error) }
    }
    return save()
  }

  return {
    saved,
    draft,
    etag,
    status,
    failure,
    saving,
    rowErrors,
    formError,
    conflict,
    catalog,
    templates,
    changes,
    changeCount,
    dirty,
    load,
    loadCatalog,
    reset,
    setDraft,
    discard,
    save,
    dismissConflict,
    reloadConflict,
    overwriteConflict
  }
}
