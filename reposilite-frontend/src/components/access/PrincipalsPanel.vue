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

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import TokenTag from '../tokens/TokenTag.vue'
import NewMachineDialog from './NewMachineDialog.vue'
import NewUserDialog from './NewUserDialog.vue'
import PrincipalActionsMenu from './PrincipalActionsMenu.vue'
import PrincipalDetail from './PrincipalDetail.vue'
import { filterPrincipals, seenLabel, usedLabel } from './accessPrincipals'
import { usePrincipalList } from './usePrincipalList'

/*
 * The list-detail page of users or of machine accounts. The list pane has the search field, the
 * button that opens the create dialog, and one row per principal with its badges, its last
 * activity and its action menu. The detail pane shows the principal named by the address. Below
 * 840px only one of the two is on screen.
 *
 * Props:
 *   kind  USER or MACHINE
 *   name  the open principal, or undefined for the list alone
 */
const props = defineProps({
  kind: {
    type: String,
    required: true,
    validator: (value) => ['USER', 'MACHINE'].includes(value)
  },
  name: {
    type: String,
    default: undefined
  }
})

const router = useRouter()
const { client } = useSession()
const api = computed(() => client.value.identity.principals)

const isMachine = computed(() => props.kind === 'MACHINE')
const COPY = computed(() => (isMachine.value
  ? {
    title: 'Machine accounts',
    newLabel: 'New machine account',
    search: 'Search by name or display name',
    empty: 'There are no machine accounts yet.',
    select: 'Select a machine account to see its tokens.',
    list: 'access-machines',
    detail: 'access-machine'
  }
  : {
    title: 'Users',
    newLabel: 'New user',
    search: 'Search by username or display name',
    empty: 'There are no users yet.',
    select: 'Select a user to see the details.',
    list: 'access-users',
    detail: 'access-user'
  }))

const { principals, loaded, loadError, load } = usePrincipalList(props.kind, () => api.value)

const query = ref('')
const newOpen = ref(false)
// The row whose actions menu is open. Rows are stacking contexts, so it must rise above its followers.
const menuRow = ref(null)
const toggleMenu = (name, open) => {
  if (open) menuRow.value = name
  else if (menuRow.value === name) menuRow.value = null
}

onMounted(load)

// Everything a row shows comes from its list item. A machine account counts as used when it last
// authenticated, which the server records as the last seen time of the principal.
const rows = computed(() => filterPrincipals(principals.value, query.value).map(principal => {
  const activity = isMachine.value ? usedLabel(principal.lastSeenAt ?? null) : seenLabel(principal.lastSeenAt ?? null)
  const prefix = isMachine.value ? 'Last used' : 'Last sign-in'
  return {
    principal,
    // The chip shows the role however it is held. The menu offers to change the direct role only.
    admin: principal.instanceAdmin === true,
    directAdmin: (principal.instanceRole ?? principal.instanceAdmin) === true,
    activity,
    // "Last sign-in 3 days ago", or the bare "Never signed in".
    activityText: activity.title ? `${prefix} ${activity.text}` : activity.text,
    href: router.resolve({ name: COPY.value.detail, params: { name: principal.name } }).href
  }
}))

const emptyText = computed(() =>
  principals.value.length === 0 ? COPY.value.empty : `Nothing matches "${query.value.trim()}".`)

const open = (name) => router.push({ name: COPY.value.detail, params: { name } })
const close = () => router.push({ name: COPY.value.list })

const created = (name) => {
  load()
  open(name)
}

const deleted = (principal) => {
  load()
  if (principal.name === props.name) close()
}
</script>

<template>
  <section
    :aria-label="COPY.title"
    class="grid overflow-hidden min-[840px]:rounded-corner-large min-[840px]:border min-[840px]:border-outline-variant min-[840px]:grid-cols-[minmax(0,380px)_minmax(0,1fr)]"
  >
    <div
      class="flex min-w-0 flex-col gap-3 min-[840px]:border-r min-[840px]:border-outline-variant min-[840px]:p-3"
      :class="name ? 'max-[839px]:hidden' : ''"
    >
      <MdButton
        data-test="new-principal"
        variant="tonal"
        class="self-start"
        @click="newOpen = true"
      >
        {{ COPY.newLabel }}
      </MdButton>

      <div role="search">
        <MdTextField
          v-model="query"
          data-test="search"
          :label="COPY.search"
          variant="outlined"
          type="search"
          autocomplete="off"
        >
          <template #leading-icon>
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
            >
              <path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1 0 9.5 16a6.5 6.5 0 0 0 4.2-1.6l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z" />
            </svg>
          </template>
        </MdTextField>
      </div>

      <div
        v-if="loadError"
        role="alert"
        class="flex flex-wrap items-center gap-3 px-1"
      >
        <span class="md-body-medium text-error">Could not load the list: {{ loadError }}</span>
        <MdButton
          variant="text"
          @click="load()"
        >
          Retry
        </MdButton>
      </div>
      <p
        v-else-if="!loaded"
        role="status"
        class="px-1 md-body-medium text-on-surface-variant"
      >
        Loading...
      </p>
      <ul
        v-else-if="rows.length > 0"
        class="m-0 flex list-none flex-col gap-1 p-0"
      >
        <li
          v-for="row in rows"
          :key="row.principal.name"
          data-test="row"
          class="md-state-layer relative flex items-start gap-1 rounded-corner-large"
          :class="[
            row.principal.name === name ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface',
            row.principal.name === menuRow ? 'z-20' : ''
          ]"
        >
          <a
            :href="row.href"
            :aria-current="row.principal.name === name ? 'true' : undefined"
            class="md-focus-ring-inset flex min-w-0 flex-1 flex-col gap-1 rounded-corner-large px-4 py-3 no-underline"
            :class="row.principal.name === name ? 'text-on-secondary-container' : 'text-on-surface'"
            @click.prevent="open(row.principal.name)"
          >
            <span
              class="md-title-small [overflow-wrap:anywhere]"
              data-test="display-name"
            >{{ row.principal.displayName || row.principal.name }}</span>
            <span
              class="md-body-medium [overflow-wrap:anywhere]"
              :class="row.principal.name === name ? 'text-on-secondary-container' : 'text-on-surface-variant'"
              data-test="username"
            >{{ row.principal.name }}</span>
            <span
              v-if="row.principal.disabled || row.admin"
              class="flex flex-wrap gap-2"
            >
              <TokenTag
                v-if="row.principal.disabled"
                data-test="disabled-badge"
              >Disabled</TokenTag>
              <TokenTag
                v-if="row.admin"
                accent
                data-test="admin-badge"
              >Instance admin</TokenTag>
            </span>
            <span
              class="md-body-small"
              :class="row.principal.name === name ? 'text-on-secondary-container' : 'text-on-surface-variant'"
              data-test="activity"
              :title="row.activity.title"
            >{{ row.activityText }}</span>
          </a>
          <PrincipalActionsMenu
            class="mr-1 mt-1"
            :principal="row.principal"
            :instance-admin="row.directAdmin"
            :api="api"
            @changed="load()"
            @deleted="deleted"
            @toggle="toggleMenu(row.principal.name, $event)"
          />
        </li>
      </ul>
      <p
        v-else
        class="px-1 py-4 md-body-medium text-on-surface-variant"
      >
        {{ emptyText }}
      </p>
    </div>

    <div
      class="min-w-0 min-[840px]:p-6"
      :class="name ? '' : 'max-[839px]:hidden'"
    >
      <PrincipalDetail
        v-if="name"
        :key="`${kind}:${name}`"
        :name="name"
        :kind="kind"
        @changed="load()"
        @deleted="deleted"
        @back="close()"
      />
      <p
        v-else
        class="md-body-large text-on-surface-variant"
      >
        {{ COPY.select }}
      </p>
    </div>

    <NewMachineDialog
      v-if="isMachine"
      v-model="newOpen"
      :api="api"
      @created="created"
    />
    <NewUserDialog
      v-else
      v-model="newOpen"
      :api="api"
      @created="created"
    />
  </section>
</template>
