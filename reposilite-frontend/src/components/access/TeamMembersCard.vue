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
import { computed, ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import TokenTag from '../tokens/TokenTag.vue'
import AddMemberDialog from './AddMemberDialog.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import { manualMembers } from './accessTeams'

/*
 * The members of a team. A MANUAL member can be removed and more can be added; a SYNC member
 * comes from the identity provider and is read-only. Every change is the whole list of MANUAL
 * members after the change, handed to `commit`; the SYNC members never go into it.
 *
 * Props:
 *   team          the TeamDetailDto
 *   principals    [{ name, displayName, kind, disabled }] for the disabled chip and the picker
 *   principalsError  why the principals could not be read, or an empty string
 *   commit        async (names) => { ok, message } makes the request (PUT members, with If-Match)
 */
const props = defineProps({
  team: {
    type: Object,
    required: true
  },
  principals: {
    type: Array,
    default: () => []
  },
  principalsError: {
    type: String,
    default: ''
  },
  commit: {
    type: Function,
    required: true
  }
})

const addOpen = ref(false)
const removing = ref(null)
const removeOpen = ref(false)
const busy = ref(false)
const failure = ref('')

const disabledNames = computed(() =>
  new Set(props.principals.filter(principal => principal.disabled).map(principal => principal.name)))

const run = async (names) => {
  busy.value = true
  failure.value = ''
  try {
    const result = await props.commit(names)
    if (!result.ok) failure.value = result.message ?? ''
    return result
  } finally {
    busy.value = false
  }
}

const add = async (names) => {
  const result = await run([...manualMembers(props.team), ...names])
  if (result.ok || result.conflict) addOpen.value = false
}

const askRemove = (member) => {
  failure.value = ''
  removing.value = member
  removeOpen.value = true
}

const remove = async () => {
  const name = removing.value.principal
  const result = await run(manualMembers(props.team).filter(entry => entry !== name))
  if (result.ok || result.conflict) removeOpen.value = false
}

const openAdd = () => {
  failure.value = ''
  addOpen.value = true
}
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-large bg-surface-container-low px-4 py-4"
    aria-labelledby="team-members-title"
    data-test="members"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h4
        id="team-members-title"
        class="md-title-medium text-on-surface"
      >
        Members
      </h4>
      <MdButton
        variant="tonal"
        data-test="add-member"
        @click="openAdd()"
      >
        Add member
      </MdButton>
    </div>

    <ul
      v-if="team.members.length > 0"
      class="m-0 flex list-none flex-col gap-1 p-0"
    >
      <li
        v-for="member in team.members"
        :key="member.principal"
        data-test="member"
        class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-corner-medium px-2 py-2"
      >
        <router-link
          :to="{ name: member.kind === 'MACHINE' ? 'access-machine' : 'access-user', params: { name: member.principal } }"
          class="md-title-small min-w-0 flex-1 text-on-surface [overflow-wrap:anywhere]"
          data-test="member-name"
        >
          {{ member.principal }}
        </router-link>
        <span class="flex flex-wrap items-center gap-2">
          <TokenTag data-test="kind-chip">{{ member.kind === 'MACHINE' ? 'Machine' : 'User' }}</TokenTag>
          <TokenTag
            v-if="disabledNames.has(member.principal)"
            data-test="disabled-chip"
          >Disabled</TokenTag>
          <TokenTag data-test="source-chip">{{ member.source === 'SYNC' ? 'Sync' : 'Manual' }}</TokenTag>
        </span>
        <MdButton
          v-if="member.source !== 'SYNC'"
          variant="text"
          error
          data-test="remove-member"
          :aria-label="`Remove ${member.principal} from ${team.name}`"
          @click="askRemove(member)"
        >
          Remove
        </MdButton>
        <span
          v-else
          class="md-body-small text-on-surface-variant"
          data-test="sync-note"
        >Synced from the identity provider</span>
      </li>
    </ul>
    <p
      v-else
      class="md-body-medium text-on-surface-variant"
    >
      This team has no members yet.
    </p>

    <AddMemberDialog
      v-model="addOpen"
      :team-name="team.name"
      :principals="principals"
      :members="team.members"
      :load-error="principalsError"
      :busy="busy"
      :failure="failure"
      @add="add"
    />
    <ConfirmActionDialog
      v-model="removeOpen"
      title="Remove member"
      confirm-label="Remove"
      danger
      :busy="busy"
      :failure="failure"
      @confirm="remove()"
    >
      {{ removing?.principal }} will no longer get the grants of {{ team.name }}.
    </ConfirmActionDialog>
  </section>
</template>
