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
import NewTeamDialog from './NewTeamDialog.vue'
import TeamDetail from './TeamDetail.vue'
import { filterTeams } from './accessTeams'
import { useTeamList } from './useTeamList'

/*
 * The list-detail page of teams. The list pane has the search field, the button that opens the
 * create dialog, and one row per team with its description, its counts and the instance admin
 * chip. The detail pane shows the team named by the address. Below 840px only one of the two is
 * on screen.
 *
 * Props:
 *   name  the open team, or undefined for the list alone
 */
const props = defineProps({
  name: {
    type: String,
    default: undefined
  }
})

const router = useRouter()
const { client } = useSession()
const api = computed(() => client.value.identity.teams)

const { teams, loaded, loadError, load, isInstanceAdmin } = useTeamList(() => api.value)

const query = ref('')
const newOpen = ref(false)

onMounted(load)

const count = (value, noun) => `${value ?? 0} ${noun}${value === 1 ? '' : 's'}`

const rows = computed(() => filterTeams(teams.value, query.value).map(team => ({
  team,
  admin: isInstanceAdmin(team),
  counts: `${count(team.memberCount, 'member')}, ${count(team.grantCount, 'grant')}`,
  href: router.resolve({ name: 'access-team', params: { name: team.name } }).href
})))

const emptyText = computed(() =>
  teams.value.length === 0 ? 'There are no teams yet.' : `Nothing matches "${query.value.trim()}".`)

const open = (name) => router.push({ name: 'access-team', params: { name } })
const close = () => router.push({ name: 'access-teams' })

const created = (name) => {
  load()
  open(name)
}

const deleted = (name) => {
  load()
  if (name === props.name) close()
}
</script>

<template>
  <section
    aria-label="Teams"
    data-test="teams"
    class="grid overflow-hidden min-[840px]:rounded-corner-large min-[840px]:border min-[840px]:border-outline-variant min-[840px]:grid-cols-[minmax(0,380px)_minmax(0,1fr)]"
  >
    <div
      class="flex min-w-0 flex-col gap-3 min-[840px]:border-r min-[840px]:border-outline-variant min-[840px]:p-3"
      :class="name ? 'max-[839px]:hidden' : ''"
    >
      <MdButton
        data-test="new-team"
        variant="tonal"
        class="self-start"
        @click="newOpen = true"
      >
        New team
      </MdButton>

      <div role="search">
        <MdTextField
          v-model="query"
          data-test="search"
          label="Search by name or description"
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
          :key="row.team.name"
          data-test="row"
          class="md-state-layer relative flex items-start gap-1 rounded-corner-large"
          :class="row.team.name === name ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
        >
          <a
            :href="row.href"
            :aria-current="row.team.name === name ? 'true' : undefined"
            class="md-focus-ring-inset flex min-w-0 flex-1 flex-col gap-1 rounded-corner-large px-4 py-3 no-underline"
            :class="row.team.name === name ? 'text-on-secondary-container' : 'text-on-surface'"
            @click.prevent="open(row.team.name)"
          >
            <span
              class="md-title-small [overflow-wrap:anywhere]"
              data-test="team-name"
            >{{ row.team.name }}</span>
            <span
              v-if="row.team.description"
              class="md-body-medium [overflow-wrap:anywhere]"
              :class="row.team.name === name ? 'text-on-secondary-container' : 'text-on-surface-variant'"
              data-test="team-description"
            >{{ row.team.description }}</span>
            <span
              class="md-body-small"
              :class="row.team.name === name ? 'text-on-secondary-container' : 'text-on-surface-variant'"
              data-test="counts"
            >{{ row.counts }}</span>
            <span
              v-if="row.admin"
              class="flex flex-wrap gap-2"
            >
              <TokenTag
                accent
                data-test="admin-badge"
              >Instance admin</TokenTag>
            </span>
          </a>
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
      <TeamDetail
        v-if="name"
        :key="name"
        :name="name"
        @changed="load()"
        @deleted="deleted"
        @back="close()"
      />
      <p
        v-else
        class="md-body-large text-on-surface-variant"
      >
        Select a team to see its members and grants.
      </p>
    </div>

    <NewTeamDialog
      v-model="newOpen"
      :api="api"
      @created="created"
    />
  </section>
</template>
