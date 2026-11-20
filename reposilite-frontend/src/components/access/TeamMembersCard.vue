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
import { computed, ref, type PropType } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import TokenTag from '../tokens/TokenTag.vue'
import AddMemberDialog from './AddMemberDialog.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import { manualMembers, type CommitMembers, type MembersOutcome, type TeamDetail, type TeamMember } from './accessTeams'
import type { PrincipalSummary } from './accessPrincipals'

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
    type: Object as PropType<TeamDetail>,
    required: true
  },
  principals: {
    type: Array as PropType<PrincipalSummary[]>,
    default: () => []
  },
  principalsError: {
    type: String,
    default: ''
  },
  commit: {
    type: Function as PropType<CommitMembers>,
    required: true
  }
})

const addOpen = ref(false)
const removing = ref<TeamMember | null>(null)
const removeOpen = ref(false)
const busy = ref(false)
const failure = ref('')

const disabledNames = computed(() =>
  new Set(props.principals.filter(principal => principal.disabled).map(principal => principal.name)))

const run = async (names: string[]): Promise<MembersOutcome> => {
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

const add = async (names: string[]) => {
  const result = await run([...manualMembers(props.team), ...names])
  if (result.ok || result.conflict) addOpen.value = false
}

const askRemove = (member: TeamMember) => {
  failure.value = ''
  removing.value = member
  removeOpen.value = true
}

const remove = async () => {
  const name = removing.value!.principal
  const result = await run(manualMembers(props.team).filter(entry => entry !== name))
  if (result.ok || result.conflict) removeOpen.value = false
}

const openAdd = () => {
  failure.value = ''
  addOpen.value = true
}
</script>

<template>
  <MdCard
    variant="low"
    data-test="members"
  >
    <section
      class="flex flex-col gap-3"
      aria-labelledby="team-members-title"
      data-ingot-component="team-members-card"
    >
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h2
          id="team-members-title"
          class="md-title-medium text-on-surface"
        >
          Members
        </h2>
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
        class="flex flex-col gap-1"
      >
        <li
          v-for="member in team.members"
          :key="member.principal"
          data-test="member"
          class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-corner-medium px-2 py-2"
        >
          <router-link
            :to="{ name: member.kind === 'MACHINE' ? 'access-machine' : 'access-user', params: { name: member.principal } }"
            class="md-title-small min-w-0 flex-1 text-on-surface break-all"
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
            <TokenTag data-test="source-chip">{{ member.source === 'SYNC' ? 'Synced' : 'Manual' }}</TokenTag>
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
      <MdStatePanel
        v-else
        state="empty"
        title="This team has no members yet."
      />

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
        {{ removing?.principal }} will no longer get the permissions of {{ team.name }}.
      </ConfirmActionDialog>
    </section>
  </MdCard>
</template>
