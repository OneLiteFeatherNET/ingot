<!--
  - Copyright (c) 2026 OneLiteFeather
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { errorMessage } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdDataTable from '../md/MdDataTable.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import MdTextField from '../md/MdTextField.vue'
import TokenTag from '../tokens/TokenTag.vue'
import { ROLES, pathPrefixLabel } from '../access/accessTeams'

/*
 * Who can reach one repository and through which team: the answer of
 * GET /api/identity/repositories/{repository}/access. It sits below the repository settings, not
 * in their form, so it never touches the form's unsaved changes.
 *
 * The answer is one table row per principal. Instance admins come first, then the rest by name.
 * Each grant of a principal is one line of its row under "Granted through" and "Scope". The
 * filter field appears only above ten principals.
 *
 * The endpoint needs an instance admin, so the card draws nothing and asks for nothing for anyone
 * else. A team named machine:* is hidden from the team pages, so its grants are not linked: a
 * legacy route reads "Token route", any other grant "Direct grant". The paths of a legacy route
 * carry the repository name first; it is dropped, so the scope is relative to the repository.
 *
 * Props:
 *   repository  the id of the repository
 */
const props = defineProps<{
  repository: string
}>()

// One grant of a principal: the team it comes through, the role there and the path it covers.
interface AccessVia {
  source?: string
  team?: string | null
  role?: string
  pathPrefix?: string | null
}

// One principal of the answer, with what it may do on the repository and why.
interface AccessEntry {
  principal: string
  kind?: string
  disabled?: boolean
  instanceAdmin?: boolean
  effective?: string[]
  via?: AccessVia[]
}

interface RepositoryAccess {
  repository?: string
  visibility?: string
  entries?: AccessEntry[]
}

// One line of a principal under "Granted through" and "Scope": a linked team with its role, or a text.
interface GrantLine {
  team: string | null
  role?: string
  text?: string
  scope: string
}

const { client, isManager } = useSession()
const allowed = computed(() => isManager?.value === true)

const VISIBILITY_LABELS: Record<string, string> = { PUBLIC: 'Public', HIDDEN: 'Hidden', PRIVATE: 'Private' }
const KIND_LABELS: Record<string, string> = { USER: 'User', MACHINE: 'Machine' }
// The effective actions come as backend names, in the order the table shows them. An unknown one
// is shown as it is, after the known ones.
const ACTION_ORDER = ['READ', 'WRITE', 'DELETE', 'MANAGE_REPOSITORY']
const ACTION_LABELS: Record<string, string> = {
  READ: 'Read',
  WRITE: 'Write',
  DELETE: 'Delete',
  MANAGE_REPOSITORY: 'Manage',
  INSTANCE_ADMIN: 'Instance admin (all)'
}
const actionLabel = (action: string): string => ACTION_LABELS[action] ?? action
// A migrated access token's route, in plain words; the role names themselves are internal.
const LEGACY_ROUTE_LABELS: Record<string, string> = { LEGACY_READ: 'Token route, read', LEGACY_WRITE: 'Token route, write' }
const FILTER_THRESHOLD = 10
const COLUMNS = [
  { key: 'principal', label: 'Principal' },
  { key: 'access', label: 'Access' },
  { key: 'through', label: 'Granted through' },
  { key: 'scope', label: 'Scope' }
]

const access = ref<RepositoryAccess | null>(null)
const status = ref<'loading' | 'ready' | 'error'>('loading')
const failure = ref('')
const query = ref('')

let latest = 0

const load = async () => {
  const request = ++latest
  status.value = 'loading'
  failure.value = ''
  try {
    const response = await client.value.identity.repositoryAccess(props.repository)
    if (request !== latest) return
    access.value = response.data as RepositoryAccess
    status.value = 'ready'
  } catch (error) {
    if (request !== latest) return
    access.value = null
    status.value = 'error'
    failure.value = errorMessage(error)
  }
}

watch(
  () => [allowed.value, props.repository],
  ([admin]) => {
    if (admin) {
      load()
    } else {
      latest++
    }
  },
  { immediate: true }
)

const isMachineTeam = (team: unknown) => String(team).startsWith('machine:')
const hasTeam = (via: AccessVia | undefined) => via?.team != null && via.team !== ''
const teamlessLabel = (via: AccessVia | undefined) => (via?.source === 'INSTANCE_ROLE' ? 'Instance role' : 'Instance admin')
const viaOf = (entry: AccessEntry | undefined): AccessVia[] => (Array.isArray(entry?.via) ? entry.via : [])
const hasTeamlessVia = (entry: AccessEntry) => viaOf(entry).some(via => !hasTeam(via))
const roleLabel = (role: string | undefined) => ROLES.find(entry => entry.value === role)?.label ?? role
const isLegacy = (via: AccessVia | undefined) => via?.source === 'LEGACY' || String(via?.role ?? '').startsWith('LEGACY_')

const visibility = computed(() => VISIBILITY_LABELS[access.value?.visibility ?? ''] ?? access.value?.visibility ?? '')
const entries = computed(() => access.value?.entries ?? [])
const caption = computed(() => `Principals with access to ${props.repository}`)
const showFilter = computed(() => entries.value.length > FILTER_THRESHOLD)

// The path of a legacy route starts with the repository name; the scope is the rest of it.
const legacyPath = (pathPrefix: string | null | undefined, repository: string) => {
  const text = String(pathPrefix ?? '').replace(/^\/+|\/+$/g, '')
  if (text === repository) return ''
  return text.startsWith(`${repository}/`) ? text.slice(repository.length + 1) : text
}

const scopeOf = (via: AccessVia, repository: string) =>
  pathPrefixLabel(isLegacy(via) ? legacyPath(via.pathPrefix, repository) : via.pathPrefix)

// The lines of one principal: one per grant, and the instance admin when it has no grant of its own.
const grantsOf = (entry: AccessEntry, repository: string): GrantLine[] => {
  const lines = viaOf(entry).map((via): GrantLine => {
    if (!hasTeam(via)) return { team: null, text: teamlessLabel(via), scope: 'Whole repository' }
    if (isLegacy(via)) return { team: null, text: LEGACY_ROUTE_LABELS[via.role ?? ''] ?? 'Token route', scope: scopeOf(via, repository) }
    if (isMachineTeam(via.team)) return { team: null, text: `Direct grant (${roleLabel(via.role)})`, scope: scopeOf(via, repository) }
    return { team: via.team ?? null, role: roleLabel(via.role), scope: scopeOf(via, repository) }
  })
  if (entry.instanceAdmin === true && !hasTeamlessVia(entry)) {
    lines.unshift({ team: null, text: 'Instance admin', scope: 'Whole repository' })
  }
  return lines
}

// Instance admin is shown once, as all actions; otherwise the known actions in their order.
const accessOf = (entry: AccessEntry): string[] => {
  if (entry.instanceAdmin === true) return [actionLabel('INSTANCE_ADMIN')]
  const actions = Array.isArray(entry.effective) ? entry.effective : []
  const known = ACTION_ORDER.filter(action => actions.includes(action))
  const unknown = actions.filter(action => !ACTION_ORDER.includes(action) && action !== 'INSTANCE_ADMIN')
  return [...known, ...unknown].map(actionLabel)
}

const byAdminThenName = (a: AccessEntry, b: AccessEntry) => {
  const admins = Number(b.instanceAdmin === true) - Number(a.instanceAdmin === true)
  return admins || String(a.principal).localeCompare(String(b.principal), undefined, { numeric: true })
}

// The grant lines of a table row; the table hands its rows back untyped.
const grantLinesOf = (row: Record<string, unknown>) => row.grants as GrantLine[]

const rows = computed(() => {
  const repository = props.repository
  const needle = query.value.trim().toLowerCase()
  return [...entries.value]
    .sort(byAdminThenName)
    .filter(entry => !showFilter.value || needle === '' || String(entry.principal).toLowerCase().includes(needle))
    .map(entry => ({
      principal: entry.principal,
      kind: KIND_LABELS[entry.kind ?? ''] ?? entry.kind,
      disabled: entry.disabled === true,
      access: accessOf(entry),
      grants: grantsOf(entry, repository)
    }))
})
</script>

<template>
  <section
    v-if="allowed"
    aria-labelledby="repository-access-title"
    data-ingot-component="repository-access-card"
  >
    <MdCard
      variant="low"
      data-test="repository-access"
      class="flex flex-col gap-4"
    >
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h3
          id="repository-access-title"
          class="md-title-small text-on-surface"
        >
          Who has access
        </h3>
        <TokenTag
          v-if="status === 'ready'"
          data-test="visibility"
        >
          {{ visibility }}
        </TokenTag>
      </div>

      <MdStatePanel
        v-if="status === 'loading'"
        state="loading"
        data-ingot-state="loading"
        title="Loading who has access"
        data-test="loading"
      />
      <MdStatePanel
        v-else-if="status === 'error'"
        state="error"
        title="Could not load who has access"
        :description="failure"
      >
        <template #actions>
          <MdButton
            data-test="retry"
            variant="tonal"
            type="button"
            @click="load"
          >
            Retry
          </MdButton>
        </template>
      </MdStatePanel>
      <p
        v-else-if="entries.length === 0"
        data-test="empty"
        class="md-body-medium text-on-surface-variant"
      >
        No one has access through identity grants yet.
      </p>
      <div
        v-else
        class="flex flex-col gap-3"
      >
        <MdTextField
          v-if="showFilter"
          v-model="query"
          data-test="filter"
          label="Filter principals"
          variant="outlined"
          type="search"
          autocomplete="off"
        />

        <MdDataTable
          :caption="caption"
          :columns="COLUMNS"
          :rows="rows"
        >
          <template #cell="{ row, column }">
            <div
              v-if="column.key === 'principal'"
              class="flex flex-col gap-1"
              data-test="access-row"
              :data-principal="row.principal"
            >
              <span
                data-test="principal"
                class="break-all md-title-small text-on-surface"
              >{{ row.principal }}</span>
              <span class="flex flex-wrap items-center gap-2 md-label-medium text-on-surface-variant">
                <span data-test="kind">{{ row.kind }}</span>
                <span
                  v-if="row.disabled"
                  data-test="disabled"
                >Disabled</span>
              </span>
            </div>
            <ul
              v-else-if="column.key === 'access'"
              class="m-0 flex list-none flex-col gap-1 p-0"
            >
              <li
                v-for="label in row.access"
                :key="label"
                data-test="action"
              >
                {{ label }}
              </li>
            </ul>
            <ul
              v-else-if="column.key === 'through'"
              class="m-0 flex list-none flex-col gap-1 p-0"
            >
              <li
                v-for="(grant, index) in grantLinesOf(row)"
                :key="index"
                data-test="via"
              >
                <template v-if="grant.team">
                  Team
                  <router-link
                    data-test="via-team"
                    class="text-primary underline"
                    :to="{ name: 'access-team', params: { name: grant.team } }"
                  >
                    {{ grant.team }}
                  </router-link>
                  ({{ grant.role }})
                </template>
                <span
                  v-else
                  data-test="via-team"
                >{{ grant.text }}</span>
              </li>
            </ul>
            <ul
              v-else-if="column.key === 'scope'"
              class="m-0 flex list-none flex-col gap-1 p-0"
            >
              <li
                v-for="(grant, index) in grantLinesOf(row)"
                :key="index"
                data-test="via-prefix"
                class="break-all"
              >
                {{ grant.scope }}
              </li>
            </ul>
          </template>
        </MdDataTable>

        <p
          v-if="rows.length === 0"
          data-test="no-match"
          class="md-body-medium text-on-surface-variant"
        >
          No principal matches the filter.
        </p>
      </div>
    </MdCard>
  </section>
</template>
