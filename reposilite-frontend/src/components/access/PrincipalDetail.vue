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
import MdChip from '../md/MdChip.vue'
import TokenList from '../identity/TokenList.vue'
import { instantToDate } from '../../helpers/apiTime'
import { formatDate } from '../identity/identityTokens'
import TokenTag from '../tokens/TokenTag.vue'
import LegacyGrantsPanel from './LegacyGrantsPanel.vue'
import PermissionNodesCard from './PermissionNodesCard.vue'
import PrincipalActionsMenu from './PrincipalActionsMenu.vue'
import { detailOf, isInstanceAdmin, legacyGrants, newestLastUsed, seenLabel, usedLabel } from './accessPrincipals'

/*
 * The detail of one user or machine account: the facts, the teams as chips that open the team,
 * the actions, the permission nodes (edited here) and for a machine account its tokens and the
 * routes it still has from a migrated access token (read only).
 *
 * Props:
 *   name  the principal to show
 *   kind  USER or MACHINE; it decides which facts and sections appear
 * Emits changed and deleted (after an action), and back (the link that closes the detail on a
 * narrow window, where the list and the detail do not fit side by side).
 */
const props = defineProps({
  name: {
    type: String,
    required: true
  },
  kind: {
    type: String,
    required: true,
    validator: (value) => ['USER', 'MACHINE'].includes(value)
  }
})

const emit = defineEmits(['changed', 'deleted', 'back'])

const { client } = useSession()
const api = computed(() => client.value.identity.principals)
const tokensApi = computed(() => client.value.identity.principalTokens(props.name))

const isMachine = computed(() => props.kind === 'MACHINE')
const noun = computed(() => (isMachine.value ? 'machine accounts' : 'users'))

const detail = ref(null)
const status = ref('loading')
const failure = ref('')
// The rights of a migrated access token. undefined while they are being read, null when the
// machine has no migrated token.
const legacy = ref(undefined)

let latest = 0

const loadLegacy = async (request) => {
  if (!isMachine.value) return
  try {
    const response = await client.value.tokens.get(props.name)
    if (request === latest) legacy.value = legacyGrants(response.data)
  } catch {
    // No migrated token of that name: its rights come from its teams.
    if (request === latest) legacy.value = null
  }
}

const load = async () => {
  const request = ++latest
  status.value = detail.value ? status.value : 'loading'
  failure.value = ''
  try {
    const response = await api.value.get(props.name)
    if (request !== latest) return
    detail.value = detailOf(response.data)
    status.value = 'ready'
  } catch (error) {
    if (request !== latest) return
    detail.value = null
    status.value = error?.response?.status === 404 ? 'missing' : 'error'
    failure.value = errorMessage(error)
    return
  }
  legacy.value = undefined
  await loadLegacy(request)
}

watch(() => props.name, () => {
  detail.value = null
  legacy.value = undefined
  load()
}, { immediate: true })

const changed = () => {
  load()
  emit('changed')
}

const title = computed(() => detail.value?.displayName || props.name)
const seen = computed(() => seenLabel(detail.value?.lastSeenAt ?? null))
const used = computed(() => usedLabel(newestLastUsed(detail.value?.tokens)))
// createdAt is an Instant on the wire: epoch seconds, read through the shared helper.
const createdText = computed(() => {
  const date = instantToDate(detail.value?.createdAt)
  return date ? formatDate(date) : 'Unknown'
})
const admin = computed(() => isInstanceAdmin(detail.value))
</script>

<template>
  <section
    class="flex min-w-0 flex-col gap-4"
    aria-labelledby="principal-title"
  >
    <MdButton
      variant="text"
      class="self-start min-[840px]:hidden"
      data-test="back"
      @click="emit('back')"
    >
      Back to {{ noun }}
    </MdButton>

    <p
      v-if="status === 'loading'"
      role="status"
      class="md-body-medium text-on-surface-variant"
    >
      Loading...
    </p>
    <div
      v-else-if="status === 'missing'"
      role="alert"
      class="md-body-large text-on-surface-variant"
      data-test="missing"
    >
      {{ name }} does not exist (any more).
    </div>
    <div
      v-else-if="status === 'error'"
      role="alert"
      class="flex flex-wrap items-center gap-3"
    >
      <span class="md-body-medium text-error">Could not load {{ name }}: {{ failure }}</span>
      <MdButton
        variant="text"
        @click="load()"
      >
        Retry
      </MdButton>
    </div>

    <template v-else-if="detail">
      <div class="flex flex-col gap-4 rounded-corner-large bg-surface-container-low px-4 py-4">
        <div class="flex items-start justify-between gap-2">
          <div class="flex min-w-0 flex-col gap-2">
            <h3
              id="principal-title"
              class="md-title-large break-words text-on-surface"
            >
              {{ title }}
            </h3>
            <div class="flex flex-wrap items-center gap-2">
              <TokenTag
                v-if="detail.disabled"
                data-test="disabled-badge"
              >
                Disabled
              </TokenTag>
              <TokenTag
                v-if="admin"
                accent
                data-test="admin-badge"
              >
                Instance admin
              </TokenTag>
            </div>
          </div>
          <div class="flex shrink-0 items-center gap-2">
            <router-link
              v-slot="{ href, navigate }"
              :to="{ name: 'access-check', query: { principal: detail.name } }"
              custom
            >
              <MdButton
                variant="outlined"
                :href="href"
                data-test="check-permission"
                @click="navigate"
              >
                Check permission
              </MdButton>
            </router-link>
            <PrincipalActionsMenu
              :principal="detail"
              :instance-admin="admin"
              :api="api"
              @changed="changed()"
              @deleted="emit('deleted', detail)"
            />
          </div>
        </div>

        <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 md-body-medium">
          <dt class="text-on-surface-variant">
            {{ isMachine ? 'Name' : 'Username' }}
          </dt>
          <dd
            class="m-0 min-w-0 break-words text-on-surface"
            data-test="username"
          >
            {{ detail.name }}
          </dd>
          <dt class="text-on-surface-variant">
            Display name
          </dt>
          <dd class="m-0 min-w-0 break-words text-on-surface">
            {{ detail.displayName || 'None' }}
          </dd>
          <dt class="text-on-surface-variant">
            Status
          </dt>
          <dd
            class="m-0 text-on-surface"
            data-test="status"
          >
            {{ detail.disabled ? 'Disabled' : 'Active' }}
          </dd>
          <template v-if="!isMachine">
            <dt class="text-on-surface-variant">
              Instance admin
            </dt>
            <dd
              class="m-0 text-on-surface"
              data-test="instance-admin"
            >
              {{ admin ? 'Yes' : 'No' }}
            </dd>
          </template>
          <dt class="text-on-surface-variant">
            Created
          </dt>
          <dd class="m-0 text-on-surface">
            {{ createdText }}
          </dd>
          <dt class="text-on-surface-variant">
            {{ isMachine ? 'Last used' : 'Last sign-in' }}
          </dt>
          <dd
            class="m-0 text-on-surface"
            data-test="last-activity"
            :title="isMachine ? used.title : seen.title"
          >
            {{ isMachine ? used.text : seen.text }}
          </dd>
          <dt class="text-on-surface-variant">
            Teams
          </dt>
          <dd class="m-0 flex min-w-0 flex-wrap items-center gap-2 text-on-surface">
            <template v-if="detail.teams.length > 0">
              <router-link
                v-for="team in detail.teams"
                :key="team"
                v-slot="{ href, navigate }"
                :to="{ name: 'access-team', params: { name: team } }"
                custom
              >
                <MdChip
                  data-test="team"
                  :href="href"
                  @click="navigate"
                >
                  {{ team }}
                </MdChip>
              </router-link>
            </template>
            <span
              v-else
              class="text-on-surface-variant"
            >
              No teams
            </span>
          </dd>
        </dl>
      </div>

      <PermissionNodesCard
        subject-kind="principal"
        :name="name"
        :inherit-from="detail.teams"
        :inherit-depth="0"
        @saved="changed()"
      />

      <template v-if="isMachine">
        <LegacyGrantsPanel
          v-if="legacy"
          :grants="legacy"
        />
        <p
          v-else-if="legacy === null"
          data-test="team-rights-hint"
          class="rounded-corner-large bg-surface-container-low px-4 py-3 md-body-medium text-on-surface-variant"
        >
          This token's rights come from the teams of this machine account.
        </p>
        <TokenList
          :key="name"
          :api="tokensApi"
          title="Tokens"
          description="Tokens sign in builds and scripts as this machine account."
          empty-text="This machine account has no tokens yet."
        />
      </template>
    </template>
  </section>
</template>
