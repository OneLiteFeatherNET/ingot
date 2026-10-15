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
import { computed, ref, watch } from 'vue'
import { errorMessage } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import TokenTag from '../tokens/TokenTag.vue'
import { ROLES, pathPrefixLabel } from '../access/accessTeams'

/*
 * Who can reach one repository and through which team: the answer of
 * GET /api/identity/repositories/{repository}/access. It sits below the repository settings, not
 * in their form, so it never touches the form's unsaved changes.
 *
 * The endpoint needs an instance admin, so the card draws nothing and asks for nothing for anyone
 * else. A team named machine:* is hidden from the team pages; its grants read "Machine account
 * grant" and are not linked.
 *
 * Props:
 *   repository  the id of the repository
 */
const props = defineProps({
  repository: {
    type: String,
    required: true
  }
})

const { client, isManager } = useSession()
const allowed = computed(() => isManager?.value === true)

const VISIBILITY_LABELS = { PUBLIC: 'Public', HIDDEN: 'Hidden', PRIVATE: 'Private' }
const KIND_LABELS = { USER: 'User', MACHINE: 'Machine' }

const access = ref(null)
const status = ref('loading')
const failure = ref('')

let latest = 0

const load = async () => {
  const request = ++latest
  status.value = 'loading'
  failure.value = ''
  try {
    const response = await client.value.identity.repositoryAccess(props.repository)
    if (request !== latest) return
    access.value = response.data
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

const isMachineTeam = (team) => String(team).startsWith('machine:')
const hasTeam = (via) => via?.team != null && via.team !== ''
const teamlessLabel = (via) => (via?.source === 'INSTANCE_ROLE' ? 'Instance role' : 'Instance admin')
const viaOf = (entry) => (Array.isArray(entry?.via) ? entry.via : [])
const hasTeamlessVia = (entry) => viaOf(entry).some(via => !hasTeam(via))
const roleLabel = (role) => ROLES.find(entry => entry.value === role)?.label ?? role

const visibility = computed(() => VISIBILITY_LABELS[access.value?.visibility] ?? access.value?.visibility ?? '')
const entries = computed(() => access.value?.entries ?? [])
</script>

<template>
  <section
    v-if="allowed"
    data-test="repository-access"
    aria-labelledby="repository-access-title"
    class="flex flex-col gap-4 rounded-corner-medium bg-surface-container-low p-4"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h4
        id="repository-access-title"
        class="md-title-medium text-on-surface"
      >
        Who has access
      </h4>
      <TokenTag
        v-if="status === 'ready'"
        data-test="visibility"
      >
        {{ visibility }}
      </TokenTag>
    </div>

    <p
      v-if="status === 'loading'"
      data-test="loading"
      role="status"
      class="md-body-medium text-on-surface-variant"
    >
      Loading...
    </p>
    <div
      v-else-if="status === 'error'"
      role="alert"
      class="flex flex-wrap items-center gap-3"
    >
      <span class="md-body-medium text-error">Could not load who has access: {{ failure }}</span>
      <MdButton
        data-test="retry"
        variant="tonal"
        type="button"
        @click="load"
      >
        Retry
      </MdButton>
    </div>
    <p
      v-else-if="entries.length === 0"
      data-test="empty"
      class="md-body-medium text-on-surface-variant"
    >
      No one has access through identity grants yet.
    </p>
    <ul
      v-else
      class="m-0 flex list-none flex-col gap-3 p-0"
    >
      <li
        v-for="entry in entries"
        :key="entry.principal"
        data-test="access-row"
        :data-principal="entry.principal"
        class="flex flex-col gap-2 rounded-corner-medium bg-surface-container p-3"
      >
        <div class="flex flex-wrap items-center gap-2">
          <span
            data-test="principal"
            class="min-w-0 break-all md-title-small text-on-surface"
          >{{ entry.principal }}</span>
          <TokenTag data-test="kind">
            {{ KIND_LABELS[entry.kind] ?? entry.kind }}
          </TokenTag>
          <TokenTag
            v-if="entry.disabled"
            data-test="disabled"
          >
            Disabled
          </TokenTag>
          <TokenTag
            v-if="entry.instanceAdmin"
            accent
            data-test="instance-admin"
          >
            Instance admin
          </TokenTag>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <TokenTag
            v-for="action in entry.effective"
            :key="action"
            data-test="action"
          >
            {{ action }}
          </TokenTag>
        </div>

        <ul class="m-0 flex list-none flex-col gap-1 p-0 md-body-medium text-on-surface-variant">
          <li
            v-if="entry.instanceAdmin && !hasTeamlessVia(entry)"
            data-test="via-instance-admin"
          >
            Via instance admin
          </li>
          <li
            v-for="(via, index) in viaOf(entry)"
            :key="`${via.team ?? via.source ?? ''}:${via.role ?? ''}:${via.pathPrefix ?? ''}:${index}`"
            data-test="via"
            class="flex flex-wrap items-center gap-x-2"
          >
            <span
              v-if="!hasTeam(via)"
              data-test="via-team"
            >{{ teamlessLabel(via) }}</span>
            <span
              v-else-if="isMachineTeam(via.team)"
              data-test="via-team"
            >Machine account grant</span>
            <router-link
              v-else
              data-test="via-team"
              class="text-primary underline"
              :to="{ name: 'access-team', params: { name: via.team } }"
            >
              {{ via.team }}
            </router-link>
            <span
              v-if="hasTeam(via)"
              data-test="via-role"
            >{{ roleLabel(via.role) }}</span>
            <span
              v-if="hasTeam(via)"
              data-test="via-prefix"
              class="break-all"
            >{{ pathPrefixLabel(via.pathPrefix) }}</span>
          </li>
        </ul>
      </li>
    </ul>
  </section>
</template>
