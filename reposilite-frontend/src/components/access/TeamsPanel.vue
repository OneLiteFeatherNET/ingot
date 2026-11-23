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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdIcon from '../md/MdIcon.vue'
import MdList from '../md/MdList.vue'
import MdListItem from '../md/MdListItem.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import MdTextField from '../md/MdTextField.vue'
import TokenTag from '../tokens/TokenTag.vue'
import NewTeamDialog from './NewTeamDialog.vue'
import TeamDetail from './TeamDetail.vue'
import { filterTeams } from './accessTeams'
import { selectedKey } from './rowSelection'
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

const count = (value: number | null | undefined, noun: string) => `${value ?? 0} ${noun}${value === 1 ? '' : 's'}`

// The list decides the selected row here, once, and each row only reads its flag.
const rows = computed(() => {
  const visible = filterTeams(teams.value, query.value)
  const current = selectedKey(visible, team => team.name, props.name)
  return visible.map(team => ({
    team,
    admin: isInstanceAdmin(team),
    counts: `${count(team.memberCount, 'member')}, ${count(team.nodeCount, 'node')}`,
    to: { name: 'access-team', params: { name: team.name } },
    selected: team.name === current
  }))
})

const emptyText = computed(() =>
  teams.value.length === 0 ? 'There are no teams yet.' : `Nothing matches "${query.value.trim()}".`)

const open = (name: string) => router.push({ name: 'access-team', params: { name } })
const close = () => router.push({ name: 'access-teams' })

const created = (name: string) => {
  load()
  open(name)
}

const deleted = (name: string) => {
  load()
  if (name === props.name) close()
}
</script>

<template>
  <section
    aria-label="Teams"
    data-test="teams"
    class="grid overflow-hidden expanded:rounded-corner-large expanded:border expanded:border-outline-variant expanded:grid-cols-[minmax(0,380px)_minmax(0,1fr)]"
    data-ingot-component="teams-panel"
  >
    <div
      class="flex min-w-0 flex-col gap-3 expanded:border-r expanded:border-outline-variant expanded:p-3"
      :class="name ? 'max-expanded:hidden' : ''"
    >
      <div class="flex flex-wrap gap-2">
        <MdButton
          data-test="new-team"
          variant="tonal"
          @click="newOpen = true"
        >
          New team
        </MdButton>
        <router-link
          v-if="!name"
          v-slot="{ href, navigate }"
          :to="{ name: 'access-check' }"
          custom
        >
          <MdButton
            variant="tonal"
            :href="href"
            data-test="check-list"
            @click="navigate"
          >
            <template #icon>
              <MdIcon
                name="shield-check"
                size="md"
              />
            </template>
            Check a permission
          </MdButton>
        </router-link>
      </div>

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

      <MdStatePanel
        v-if="loadError"
        state="error"
        data-test="list-error"
        title="Could not load the list"
        :description="loadError"
        @retry="load()"
      />
      <MdStatePanel
        v-else-if="!loaded"
        state="loading"
        data-test="list-loading"
      />
      <MdList v-else-if="rows.length > 0">
        <MdListItem
          v-for="row in rows"
          :key="row.team.name"
          data-test="row"
          :to="row.to"
          :selected="row.selected"
        >
          <span class="flex flex-col gap-1">
            <span
              class="md-title-small break-all"
              data-test="team-name"
            >{{ row.team.name }}</span>
            <span
              v-if="row.team.description"
              class="md-body-medium break-words"
              :class="row.selected ? 'text-on-secondary-container' : 'text-on-surface-variant'"
              data-test="team-description"
            >{{ row.team.description }}</span>
            <span
              class="md-body-small"
              :class="row.selected ? 'text-on-secondary-container' : 'text-on-surface-variant'"
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
          </span>
        </MdListItem>
      </MdList>
      <MdStatePanel
        v-else
        state="empty"
        data-test="list-empty"
        :title="emptyText"
      />
    </div>

    <div class="min-w-0 p-4 expanded:p-6">
      <TeamDetail
        v-if="name"
        :key="name"
        :name="name"
        @changed="load()"
        @deleted="deleted"
      />
      <p
        v-else
        class="md-body-large text-on-surface-variant max-expanded:hidden"
      >
        Select a team to see its members and permission nodes.
      </p>
    </div>

    <NewTeamDialog
      v-model="newOpen"
      :api="api"
      @created="created"
    />
  </section>
</template>
