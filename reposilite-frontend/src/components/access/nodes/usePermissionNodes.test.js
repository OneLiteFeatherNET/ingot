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

import { describe, it, expect, vi } from 'vitest'
import { usePermissionNodes } from './usePermissionNodes'

vi.mock('../../../helpers/toast', () => ({
  errorMessage: (error) => error?.response?.data?.message ?? error?.message ?? `${error}`
}))

/*
 * The composable is driven through a fake api (the shape permissionNodesApi returns). Nothing
 * waits on a timer: every call resolves as a promise.
 */

const failure = (status, message, extra = {}) =>
  Object.assign(new Error(message), { response: { status, data: { message, ...extra } } })

const SAVED = () => [
  { node: 'ingot.repository.releases.read', allowed: true },
  { node: 'ingot.admin.users', allowed: false }
]

const makeApi = (overrides = {}) => ({
  load: vi.fn(async () => ({ nodes: SAVED(), etag: '"1"' })),
  save: vi.fn(async (kind, name, nodes) => ({ nodes, etag: '"2"' })),
  catalog: vi.fn(async () => [{ node: '*', description: 'Everything', kind: 'WILDCARD' }]),
  templates: vi.fn(async () => [{ id: 'read', label: 'Read', nodes: ['ingot.repository.{repository}.read'] }]),
  ...overrides
})

const open = async (api = makeApi(), subject = { kind: 'principal', name: 'alice' }) => {
  const nodes = usePermissionNodes(() => subject, () => api)
  await nodes.load()
  return { nodes, api }
}

describe('usePermissionNodes', () => {
  describe('load', () => {
    it('loads the list and the entity tag of the subject', async () => {
      const { nodes, api } = await open()

      expect(api.load).toHaveBeenCalledWith('principal', 'alice')
      expect(nodes.saved.value).toEqual(SAVED())
      expect(nodes.draft.value).toEqual(SAVED())
      expect(nodes.etag.value).toBe('"1"')
      expect(nodes.status.value).toBe('ready')
    })

    it('reports a missing subject for a 404', async () => {
      const api = makeApi({ load: vi.fn(async () => { throw failure(404, 'No such principal') }) })

      const { nodes } = await open(api)

      expect(nodes.status.value).toBe('missing')
      expect(nodes.failure.value).toBe('No such principal')
    })

    it('reports an error for any other failure', async () => {
      const api = makeApi({ load: vi.fn(async () => { throw failure(500, 'Boom') }) })

      const { nodes } = await open(api)

      expect(nodes.status.value).toBe('error')
    })

    it('drops a load that a newer load overtook', async () => {
      let release
      const slow = new Promise(resolve => { release = resolve })
      const api = makeApi({
        load: vi.fn()
          .mockImplementationOnce(() => slow)
          .mockImplementationOnce(async () => ({ nodes: [{ node: '*', allowed: true }], etag: '"9"' }))
      })
      const nodes = usePermissionNodes(() => ({ kind: 'team', name: 'ops' }), () => api)

      const first = nodes.load()
      await nodes.load()
      release({ nodes: SAVED(), etag: '"1"' })
      await first

      expect(nodes.etag.value, 'the newer answer stays').toBe('"9"')
    })
  })

  describe('draft', () => {
    it('is not dirty before an edit', async () => {
      const { nodes } = await open()

      expect(nodes.dirty.value).toBe(false)
    })

    it('counts an added row as one change', async () => {
      const { nodes } = await open()

      nodes.setDraft([...nodes.draft.value, { node: 'ingot.admin.teams', allowed: true }])

      expect(nodes.dirty.value).toBe(true)
      expect(nodes.changeCount.value).toBe(1)
    })

    it('goes back to the saved list on discard', async () => {
      const { nodes } = await open()
      nodes.setDraft([])

      nodes.discard()

      expect(nodes.draft.value).toEqual(SAVED())
      expect(nodes.dirty.value).toBe(false)
    })
  })

  describe('save', () => {
    it('sends the draft with the entity tag it loaded', async () => {
      const { nodes, api } = await open()
      nodes.setDraft([{ node: 'ingot.admin.teams', allowed: true }])

      const result = await nodes.save()

      expect(result).toEqual({ ok: true })
      expect(api.save).toHaveBeenCalledWith('principal', 'alice', [{ node: 'ingot.admin.teams', allowed: true }], '"1"')
    })

    it('takes the answer as the saved list and keeps the new entity tag', async () => {
      const { nodes } = await open()
      nodes.setDraft([{ node: 'ingot.admin.teams', allowed: true }])

      await nodes.save()

      expect(nodes.saved.value).toEqual([{ node: 'ingot.admin.teams', allowed: true }])
      expect(nodes.etag.value).toBe('"2"')
      expect(nodes.dirty.value).toBe(false)
    })

    it('reads the list again when the answer carries no entity tag', async () => {
      const api = makeApi({ save: vi.fn(async (kind, name, nodes) => ({ nodes, etag: null })) })
      const { nodes } = await open(api)
      api.load.mockResolvedValueOnce({ nodes: [], etag: '"7"' })

      await nodes.save()

      expect(nodes.etag.value).toBe('"7"')
    })

    it('maps the problems of a 400 to the rows they name', async () => {
      const api = makeApi({
        save: vi.fn(async () => {
          throw failure(400, '2 permission nodes are invalid', {
            problems: [
              { node: 'ingot.admin.users', message: 'Unknown admin area' },
              { node: 'ingot.admin.users', message: 'second message for the same row' }
            ]
          })
        })
      })
      const { nodes } = await open(api)

      const result = await nodes.save()

      expect(result.ok).toBe(false)
      expect(result.status).toBe(400)
      expect(nodes.rowErrors.value).toEqual({ 'ingot.admin.users': 'Unknown admin area' })
      expect(nodes.formError.value).toBe('')
    })

    it('puts a problem that names no listed row into the form error', async () => {
      const api = makeApi({
        save: vi.fn(async () => {
          throw failure(400, 'Nodes must be a list', { problems: [{ node: null, message: 'Nodes must be a list' }] })
        })
      })
      const { nodes } = await open(api)

      await nodes.save()

      expect(nodes.formError.value).toBe('Nodes must be a list')
    })

    it('keeps the draft after a failed save', async () => {
      const api = makeApi({ save: vi.fn(async () => { throw failure(500, 'Boom') }) })
      const { nodes } = await open(api)
      nodes.setDraft([])

      const result = await nodes.save()

      expect(result).toMatchObject({ ok: false, status: 500, message: 'Boom' })
      expect(nodes.draft.value).toEqual([])
    })

    it('drops the error of a row once the row is removed', async () => {
      const api = makeApi({
        save: vi.fn(async () => {
          throw failure(400, 'bad', { problems: [{ node: 'ingot.admin.users', message: 'Unknown admin area' }] })
        })
      })
      const { nodes } = await open(api)
      await nodes.save()

      nodes.setDraft(nodes.draft.value.filter(row => row.node !== 'ingot.admin.users'))

      expect(nodes.rowErrors.value).toEqual({})
    })

    it('is not saving after the request ended', async () => {
      const { nodes } = await open()

      await nodes.save()

      expect(nodes.saving.value).toBe(false)
    })
  })

  describe('conflict', () => {
    const conflicted = async () => {
      const api = makeApi()
      api.save.mockRejectedValueOnce(failure(412, 'Precondition failed'))
      const { nodes } = await open(api)
      nodes.setDraft([{ node: 'ingot.admin.teams', allowed: true }])
      const result = await nodes.save()
      return { nodes, api, result }
    }

    it('opens the conflict for the existing dialog on a 412', async () => {
      const { nodes, result } = await conflicted()

      expect(result).toMatchObject({ ok: false, conflict: true, status: 412 })
      expect(nodes.conflict.value).toEqual({ kind: 'nodes', name: 'alice' })
    })

    it('keeps the draft while the conflict is open', async () => {
      const { nodes } = await conflicted()

      expect(nodes.draft.value).toEqual([{ node: 'ingot.admin.teams', allowed: true }])
    })

    it('drops the draft and takes the stored list on reload', async () => {
      const { nodes, api } = await conflicted()
      api.load.mockResolvedValueOnce({ nodes: [{ node: '*', allowed: true }], etag: '"8"' })

      await nodes.reloadConflict()

      expect(nodes.conflict.value).toBeNull()
      expect(nodes.draft.value).toEqual([{ node: '*', allowed: true }])
      expect(nodes.etag.value).toBe('"8"')
    })

    it('writes the draft with the fresh entity tag on overwrite', async () => {
      const { nodes, api } = await conflicted()
      api.load.mockResolvedValueOnce({ nodes: [{ node: '*', allowed: true }], etag: '"8"' })

      const result = await nodes.overwriteConflict()

      expect(result).toEqual({ ok: true })
      expect(api.save).toHaveBeenLastCalledWith('principal', 'alice', [{ node: 'ingot.admin.teams', allowed: true }], '"8"')
      expect(nodes.conflict.value).toBeNull()
    })

    it('closes the conflict and keeps the draft on dismiss', async () => {
      const { nodes } = await conflicted()

      nodes.dismissConflict()

      expect(nodes.conflict.value).toBeNull()
      expect(nodes.draft.value).toEqual([{ node: 'ingot.admin.teams', allowed: true }])
    })
  })

  describe('catalog', () => {
    it('loads the catalog and the templates once', async () => {
      const { nodes, api } = await open()

      await nodes.loadCatalog()
      await nodes.loadCatalog()

      expect(api.catalog).toHaveBeenCalledTimes(1)
      expect(nodes.catalog.value).toHaveLength(1)
      expect(nodes.templates.value).toHaveLength(1)
    })

    it('leaves the catalog empty when it cannot be read, so free text still works', async () => {
      const api = makeApi({ catalog: vi.fn(async () => { throw failure(500, 'Boom') }) })
      const { nodes } = await open(api)

      await nodes.loadCatalog()

      expect(nodes.catalog.value).toEqual([])
      expect(nodes.templates.value).toHaveLength(1)
    })
  })
})
