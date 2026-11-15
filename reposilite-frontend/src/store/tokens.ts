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
import { instantToDate } from '../helpers/apiTime'
import { etagOf, isPreconditionFailed, type createClient } from './client'
import type { AxiosError, AxiosResponse } from 'axios'

/* The fields of a token the store reads. The server sends more; they are not named here. */
export interface TokenPermission {
  identifier: string
}

export interface TokenRoute {
  path: string
  permission?: TokenPermission | null
}

export interface Token {
  name: string
  description?: string | null
  expiresAt?: number | string | null
  permissions?: TokenPermission[] | null
  routes?: TokenRoute[] | null
}

/* A token's routes grouped by path: whether it may read and whether it may write. */
export interface GroupedRoute {
  path: string
  read: boolean
  write: boolean
}

/* A route as the update request carries it. */
export interface RequestRoute {
  path: string
  permissions: ('r' | 'w')[]
}

/* The fields of an update request. */
export interface TokenUpdate {
  description: string
  permissions: string[]
  routes: RequestRoute[]
  expiresAt: string | null
}

type StoreClient = ReturnType<typeof createClient>

export interface Notifier {
  success: (message: string) => unknown
  error: (message: string) => unknown
}

const tokenIsManager = (token: Token): boolean =>
  (token.permissions || []).some(permission => permission.identifier === 'access-token:manager')

const groupRoutes = (token: Token): GroupedRoute[] => {
  const byPath: Record<string, GroupedRoute> = {}
  ;(token.routes || []).forEach(route => {
    const entry = byPath[route.path] || { path: route.path, read: false, write: false }
    if (route.permission?.identifier === 'route:read') entry.read = true
    if (route.permission?.identifier === 'route:write') entry.write = true
    byPath[route.path] = entry
  })
  return Object.values(byPath)
}

const groupedToRequestRoutes = (grouped: GroupedRoute[]): RequestRoute[] =>
  grouped
    .filter(route => route.read || route.write)
    .map(route => ({
      path: route.path,
      permissions: [route.read && 'r', route.write && 'w'].filter((permission): permission is 'r' | 'w' => Boolean(permission))
    }))


/** Trims a route path and makes sure it starts with a slash. A blank path stays blank. */
export const normalizeRoutePath = (path: string | null | undefined): string => {
  const trimmed = (path || '').trim()
  if (trimmed === '') return ''
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`
}

/** The repository a route path belongs to: its first segment, or '' for the root and for a blank path. */
export const repositoryOfRoutePath = (path: string | null | undefined): string =>
  normalizeRoutePath(path).split('/').filter(segment => segment !== '')[0] ?? ''

/**
 * Checks the route draft against the routes the token already has. The route being edited is
 * passed as `original` and is not counted as a conflict with itself.
 *
 * @returns the normalised path, the error if any, and the route it clashes with
 */
export const checkRouteDraft = (token: Token, draft: { path: string; original?: string | null }): { path: string; error: null | 'empty' | 'duplicate'; existing?: GroupedRoute } => {
  const path = normalizeRoutePath(draft.path)
  if (path === '') return { path, error: 'empty' }
  const originalPath = draft.original ? normalizeRoutePath(draft.original) : null
  const existing = groupRoutes(token).find(route =>
    normalizeRoutePath(route.path) === path && normalizeRoutePath(route.path) !== originalPath)
  if (existing) return { path, error: 'duplicate', existing }
  return { path, error: null }
}

/** @returns null when the name is usable, otherwise why it is not */
export const validateTokenName = (name: string | null | undefined, existingTokens: Token[]): 'empty' | 'invalid' | 'duplicate' | null => {
  const trimmed = (name || '').trim()
  if (trimmed === '') return 'empty'
  if (/[:/]/.test(trimmed)) return 'invalid'
  if (existingTokens.some(token => token.name === trimmed)) return 'duplicate'
  return null
}

/** Compares a draft with the values it started from. Any differing key makes it dirty. */
export const isDraftDirty = (draft: Record<string, unknown>, baseline: Record<string, unknown>): boolean =>
  Object.keys({ ...baseline, ...draft }).some(key => draft[key] !== baseline[key])

const defaultNotify = {
  success: createSuccessToast,
  error: createErrorToast
}

/* A save that met a newer stored token, kept until the reader reloads or overwrites it. */
export interface TokenConflict {
  kind: 'token'
  name: string
  title: string
  reviewed: boolean
  request: TokenUpdate
  successMessage: string
}

/* The body of a token write that generated a secret. The server returns the secret once. */
interface GeneratedSecret {
  secret: string
}

/**
 * Creates an independent token store. The dashboard shares one through `useTokens`; tests
 * create their own so that no state carries over between them.
 */
export function createTokenStore({ getClient, notify = defaultNotify }: { getClient: () => StoreClient; notify?: Notifier }) {
  const tokens = ref<Token[]>([])
  const loaded = ref(false)
  const loading = ref(false)
  const loadError = ref('')
  const secret = ref<{ name: string; value: string } | null>(null)
  /* The tag each token's details were last read with. The list carries none, so every token is read once per load. */
  const etags: Record<string, string | null | undefined> = {}
  /* The token whose stored state changed elsewhere, or null. The conflict dialog renders it. */
  const conflict = ref<TokenConflict | null>(null)

  const fetchEtag = (name: string) =>
    Promise.resolve()
      .then(() => getClient().tokens.get(name))
      .then((response: AxiosResponse<Token>) => { etags[name] = etagOf(response) })
      .catch(() => { etags[name] = null })

  const fetchTokens = () => {
    loading.value = true
    loadError.value = ''
    return getClient().tokens.list()
      .then((response: AxiosResponse<Token[]>) => {
        tokens.value = response.data
        loaded.value = true
        return Promise.all(response.data.map(token => fetchEtag(token.name)))
      })
      .catch(error => { loadError.value = errorMessage(error) })
      .finally(() => { loading.value = false })
  }

  const showSecret = (name: string, value: string) => { secret.value = { name, value } }
  const dismissSecret = () => { secret.value = null }

  const createToken = (name: string, { type, description = '' }: { type: string; description?: string }) =>
    getClient().tokens.createOrUpdate(name, {
      type, secretType: 'RAW', secret: null, description, permissions: [], routes: [], expiresAt: null,
    })
      .then((response: AxiosResponse<GeneratedSecret>) => fetchTokens().then(() => {
        showSecret(name, response.data.secret)
        return response.data
      }))

  const toUpdateRequest = (token: Token): TokenUpdate => ({
    description: token.description || '',
    permissions: tokenIsManager(token) ? ['access-token:manager'] : [],
    routes: groupedToRequestRoutes(groupRoutes(token)),
    // expiresAt is an Instant on the wire: epoch seconds.
    expiresAt: instantToDate(token.expiresAt)?.toISOString() ?? null,
  })

  /*
   * Saves with the tag the token was read with. A 412 means the token changed elsewhere, so nothing is written and
   * the conflict dialog offers a reload or an overwrite. Other failures are reported as before.
   */
  const saveToken = (token: Token, overrides: Partial<TokenUpdate>, successMessage: string): Promise<boolean> => {
    const request: TokenUpdate = { ...toUpdateRequest(token), ...overrides }
    return getClient().tokens.update(token.name, request, etags[token.name])
      .then(() => fetchTokens())
      .then(() => { notify.success(successMessage); return true })
      .catch((error: AxiosError) => {
        if (isPreconditionFailed(error)) {
          conflict.value = { kind: 'token', name: token.name, title: token.name, reviewed: false, request, successMessage }
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

  const saveTokenMeta = (token: Token, { description, manager, expiresAt }: { description: string; manager: boolean; expiresAt?: string | null }) =>
    saveToken(token, {
      description: description.trim(),
      permissions: manager ? ['access-token:manager'] : [],
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
    }, `Token '${token.name}' updated`)

  const saveRoute = (token: Token, route: GroupedRoute, originalPath: string | null = null) => {
    const replaced = new Set([route.path, originalPath].filter((path): path is string => Boolean(path)))
    return saveToken(token, {
      routes: groupedToRequestRoutes([
        ...groupRoutes(token).filter(existing => !replaced.has(existing.path)),
        route,
      ]),
    }, `Route '${route.path}' saved`)
  }

  const removeRoute = (token: Token, path: string) =>
    saveToken(token, {
      routes: groupedToRequestRoutes(groupRoutes(token).filter(route => route.path !== path)),
    }, `Route '${path}' removed`)

  const deleteToken = (name: string) =>
    getClient().tokens.delete(name)
      .then(() => fetchTokens())
      .then(() => notify.success(`Token '${name}' has been deleted`))
      .catch(error => notify.error(errorMessage(error)))

  const regenerateSecret = (name: string) =>
    getClient().tokens.regenerateSecret(name)
      .then((response: AxiosResponse<string>) => {
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
    errorMessage
  }
}

const { client } = useSession()
let shared: ReturnType<typeof createTokenStore> | null = null

/** The token store the dashboard shares between views. */
export function useTokens(): ReturnType<typeof createTokenStore> {
  if (!shared) shared = createTokenStore({ getClient: () => client.value })
  return shared
}

/** Lets a component receive another token store than the shared one, which tests use for a fresh store. */
export const tokenStoreKey = Symbol('token-store')
