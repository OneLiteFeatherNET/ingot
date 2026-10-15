/*
 * Copyright (c) 2023 dzikoysk
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
import { useSession } from './session'
import { createSuccessToast, createErrorToast, errorMessage } from '../helpers/toast'
import { etagOf, isPreconditionFailed } from './client'

const tokenIsManager = (token) =>
  (token.permissions || []).some(permission => permission.identifier === 'access-token:manager')

const groupRoutes = (token) => {
  const byPath = {}
  ;(token.routes || []).forEach(route => {
    const entry = byPath[route.path] || { path: route.path, read: false, write: false }
    if (route.permission?.identifier === 'route:read') entry.read = true
    if (route.permission?.identifier === 'route:write') entry.write = true
    byPath[route.path] = entry
  })
  return Object.values(byPath)
}

const groupedToRequestRoutes = (grouped) =>
  grouped
    .filter(route => route.read || route.write)
    .map(route => ({ path: route.path, permissions: [route.read && 'r', route.write && 'w'].filter(Boolean) }))

const toMs = (value) =>
  Array.isArray(value) ? Date.UTC(value[0], value[1] - 1, value[2])
    : typeof value === 'number' ? (value < 1e12 ? value * 1000 : value)
    : new Date(value).getTime()

/** Trims a route path and makes sure it starts with a slash. A blank path stays blank. */
export const normalizeRoutePath = (path) => {
  const trimmed = (path || '').trim()
  if (trimmed === '') return ''
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`
}

/**
 * Checks the route draft against the routes the token already has. The route being edited is
 * passed as `original` and is not counted as a conflict with itself.
 *
 * @returns {{ path: string, error: null | 'empty' | 'duplicate', existing?: object }}
 */
export const checkRouteDraft = (token, draft) => {
  const path = normalizeRoutePath(draft.path)
  if (path === '') return { path, error: 'empty' }
  const originalPath = draft.original ? normalizeRoutePath(draft.original) : null
  const existing = groupRoutes(token).find(route =>
    normalizeRoutePath(route.path) === path && normalizeRoutePath(route.path) !== originalPath)
  if (existing) return { path, error: 'duplicate', existing }
  return { path, error: null }
}

/** @returns {null | 'empty' | 'invalid' | 'duplicate'} */
export const validateTokenName = (name, existingTokens) => {
  const trimmed = (name || '').trim()
  if (trimmed === '') return 'empty'
  if (/[:/]/.test(trimmed)) return 'invalid'
  if (existingTokens.some(token => token.name === trimmed)) return 'duplicate'
  return null
}

/** Compares a draft with the values it started from. Any differing key makes it dirty. */
export const isDraftDirty = (draft, baseline) =>
  Object.keys({ ...baseline, ...draft }).some(key => draft[key] !== baseline[key])

const defaultNotify = {
  success: createSuccessToast,
  error: createErrorToast
}

/**
 * Creates an independent token store. The dashboard shares one through `useTokens`; tests
 * create their own so that no state carries over between them.
 */
export function createTokenStore({ getClient, notify = defaultNotify }) {
  const tokens = ref([])
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref('')
  const secret = ref(null)
  /* The tag each token's details were last read with. The list carries none, so every token is read once per load. */
  const etags = {}
  /* The token whose stored state changed elsewhere, or null. The conflict dialog renders it. */
  const conflict = ref(null)

  const fetchEtag = (name) =>
    Promise.resolve()
      .then(() => getClient().tokens.get(name))
      .then(response => { etags[name] = etagOf(response) })
      .catch(() => { etags[name] = null })

  const fetchTokens = () => {
    loading.value = true
    loadError.value = ''
    return getClient().tokens.list()
      .then(response => {
        tokens.value = response.data
        loaded.value = true
        return Promise.all(response.data.map(token => fetchEtag(token.name)))
      })
      .catch(error => { loadError.value = errorMessage(error) })
      .finally(() => { loading.value = false })
  }

  const showSecret = (name, value) => { secret.value = { name, value } }
  const dismissSecret = () => { secret.value = null }

  const createToken = (name, { type, description = '' }) =>
    getClient().tokens.createOrUpdate(name, {
      type, secretType: 'RAW', secret: null, description, permissions: [], routes: [], expiresAt: null,
    })
      .then(response => fetchTokens().then(() => {
        showSecret(name, response.data.secret)
        return response.data
      }))

  const toUpdateRequest = (token) => ({
    description: token.description || '',
    permissions: tokenIsManager(token) ? ['access-token:manager'] : [],
    routes: groupedToRequestRoutes(groupRoutes(token)),
    expiresAt: token.expiresAt ? new Date(toMs(token.expiresAt)).toISOString() : null,
  })

  /*
   * Saves with the tag the token was read with. A 412 means the token changed elsewhere, so nothing is written and
   * the conflict dialog offers a reload or an overwrite. Other failures are reported as before.
   */
  const saveToken = (token, overrides, successMessage) => {
    const request = { ...toUpdateRequest(token), ...overrides }
    return getClient().tokens.update(token.name, request, etags[token.name])
      .then(() => fetchTokens())
      .then(() => { notify.success(successMessage); return true })
      .catch(error => {
        if (isPreconditionFailed(error)) {
          conflict.value = { kind: 'token', name: token.name, title: token.name, reviewed: false, differences: [], request, successMessage }
          return false
        }
        notify.error(errorMessage(error))
        return false
      })
  }

  /* Takes the server state. The local edit is dropped, because the view shows what the list now holds. */
  const reloadTokenConflict = () => {
    conflict.value = null
    return fetchTokens()
  }

  /* Sends the edit again without a precondition. The dialog asks for a second confirmation before this runs. */
  const overwriteTokenConflict = () => {
    const current = conflict.value
    if (current?.kind !== 'token') return Promise.resolve(false)
    return getClient().tokens.update(current.name, current.request)
      .then(() => fetchTokens())
      .then(() => {
        conflict.value = null
        notify.success(current.successMessage)
        return true
      })
      .catch(error => { notify.error(errorMessage(error)); return false })
  }

  /* Closes the dialog and keeps the edit. The next save meets the same conflict until the token is reloaded. */
  const dismissTokenConflict = () => {
    conflict.value = null
  }

  const saveTokenMeta = (token, { description, manager, expiresAt }) =>
    saveToken(token, {
      description: description.trim(),
      permissions: manager ? ['access-token:manager'] : [],
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
    }, `Token '${token.name}' updated`)

  const saveRoute = (token, route, originalPath) => {
    const replaced = new Set([route.path, originalPath].filter(Boolean))
    return saveToken(token, {
      routes: groupedToRequestRoutes([
        ...groupRoutes(token).filter(existing => !replaced.has(existing.path)),
        route,
      ]),
    }, `Route '${route.path}' saved`)
  }

  const removeRoute = (token, path) =>
    saveToken(token, {
      routes: groupedToRequestRoutes(groupRoutes(token).filter(route => route.path !== path)),
    }, `Route '${path}' removed`)

  const deleteToken = (name) =>
    getClient().tokens.delete(name)
      .then(() => fetchTokens())
      .then(() => notify.success(`Token '${name}' has been deleted`))
      .catch(error => notify.error(errorMessage(error)))

  const regenerateSecret = (name) =>
    getClient().tokens.regenerateSecret(name)
      .then(response => {
        showSecret(name, response.data)
        return response.data
      })

  return {
    tokens,
    loaded,
    loading,
    loadError,
    secret,
    dismissSecret,
    conflict,
    reloadTokenConflict,
    overwriteTokenConflict,
    dismissTokenConflict,
    fetchTokens,
    createToken,
    saveTokenMeta,
    saveRoute,
    removeRoute,
    deleteToken,
    regenerateSecret,
    groupRoutes,
    tokenIsManager,
    toMs,
    errorMessage
  }
}

const { client } = useSession()
let shared = null

/** The token store the dashboard shares between views. */
export function useTokens() {
  if (!shared) shared = createTokenStore({ getClient: () => client.value })
  return shared
}

/** Lets a component receive another token store than the shared one, which tests use for a fresh store. */
export const tokenStoreKey = Symbol('token-store')
