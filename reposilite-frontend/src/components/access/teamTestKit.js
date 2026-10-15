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

import { vi } from 'vitest'
import { ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ROUTES } from '../../router/routes'
import TeamDetail from './TeamDetail.vue'

/*
 * What the team detail tests share: the fixtures, a fake teams client and the mounting. The
 * session and the toast module are replaced in each test file with vi.mock, because vi.mock is
 * hoisted per file; the file hands its session holder to `installClient`.
 */

const TEAM = {
  name: 'developers',
  description: 'Everyone who writes code',
  externalRef: null,
  memberCount: 3,
  grantCount: 2,
  instanceRole: false,
  members: [
    { principal: 'alice', kind: 'USER', source: 'MANUAL' },
    { principal: 'bob', kind: 'USER', source: 'SYNC' },
    { principal: 'ci-bot', kind: 'MACHINE', source: 'MANUAL' }
  ],
  grants: [
    { repository: null, pathPrefix: null, role: 'READ' },
    { repository: 'releases', pathPrefix: '/com/example/', role: 'DEPLOY' }
  ]
}

const PRINCIPALS = [
  { name: 'alice', kind: 'USER', displayName: 'Alice Example', disabled: false },
  { name: 'bob', kind: 'USER', displayName: 'Bob Example', disabled: true },
  { name: 'carol', kind: 'USER', displayName: 'Carol Example', disabled: true },
  { name: 'dave', kind: 'USER', displayName: 'Dave Example', disabled: false },
  { name: 'ci-bot', kind: 'MACHINE', displayName: 'CI bot', disabled: false },
  { name: 'builder', kind: 'MACHINE', displayName: 'Builder', disabled: false }
]

const REPOSITORIES = [{ id: 'releases' }, { id: 'snapshots' }]

const answer = (data, etag = '"6"') => ({ data, headers: { etag } })

const failure = (status, message) => Object.assign(new Error(message), { response: { status, data: { message } } })

/** A fake teams client. Every write answers with the team as it would be after the write. */
const makeTeamsApi = (team = TEAM) => ({
  get: vi.fn(async () => answer(team, '"5"')),
  list: vi.fn(async () => ({ data: [] })),
  create: vi.fn(),
  update: vi.fn(async (name, body) => answer({ ...team, ...body })),
  remove: vi.fn(async () => ({ status: 204 })),
  setMembers: vi.fn(async (name, members) => answer({
    ...team,
    members: [
      ...team.members.filter(member => member.source === 'SYNC'),
      ...members.map(principal => ({ principal, kind: 'USER', source: 'MANUAL' }))
    ]
  })),
  setGrants: vi.fn(async (name, grants) => answer({ ...team, grants })),
  grantInstanceAdmin: vi.fn(async () => answer({ ...team, instanceRole: true })),
  revokeInstanceAdmin: vi.fn(async () => answer({ ...team, instanceRole: false }))
})

/** Puts a fake client into the session holder the test file mocked. */
const installClient = (holder, teams) => {
  holder.session = {
    client: ref({
      identity: {
        teams,
        principals: { list: vi.fn(async () => ({ data: PRINCIPALS })) }
      },
      maven: { repositories: vi.fn(async () => ({ data: { repositories: REPOSITORIES } })) }
    })
  }
}

const shell = { render: () => null }

const mountTeamDetail = async (name = 'developers') => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ROUTES.map(route => (route.redirect ? route : { ...route, component: shell }))
  })
  await router.push('/access/teams')
  await router.isReady()
  const wrapper = mount(TeamDetail, { props: { name }, attachTo: document.body, global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

const typeInto = (selector, value) => {
  const input = document.querySelector(selector)
  input.value = value
  input.dispatchEvent(new Event('input'))
}

const confirmDialog = () => document.querySelector('[role="dialog"]')

/** Presses the confirming button of the open confirmation dialog. */
const confirm = async () => {
  confirmDialog().querySelector('[data-test="confirm-button"]').click()
  await flushPromises()
}

export {
  PRINCIPALS,
  REPOSITORIES,
  TEAM,
  answer,
  confirm,
  confirmDialog,
  failure,
  installClient,
  makeTeamsApi,
  mountTeamDetail,
  typeInto
}
