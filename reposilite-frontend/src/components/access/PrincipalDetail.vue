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
import { computed, ref, watch, type PropType } from 'vue'
import type { AxiosError, AxiosResponse } from 'axios'
import { errorMessage } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdChip from '../md/MdChip.vue'
import MdKeyValueList from '../md/MdKeyValueList.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import TokenList from '../identity/TokenList.vue'
import { instantToDate } from '../../helpers/apiTime'
import { formatDate } from '../identity/identityTokens'
import TokenTag from '../tokens/TokenTag.vue'
import LegacyGrantsPanel from './LegacyGrantsPanel.vue'
import PermissionNodesCard from './PermissionNodesCard.vue'
import PrincipalActionsMenu from './PrincipalActionsMenu.vue'
import PrincipalFactorsCard from './PrincipalFactorsCard.vue'
import {
  detailOf,
  isInstanceAdmin,
  legacyGrants,
  newestLastUsed,
  seenLabel,
  usedLabel,
  type LegacyGrant,
  type LegacyToken,
  type PrincipalDetail,
  type PrincipalDetailAnswer,
  type PrincipalKind
} from './accessPrincipals'

/*
 * The detail of one user or machine account: the facts, the teams as chips that open the team,
 * the actions, the permission nodes (edited here) and for a machine account its tokens and the
 * routes it still has from a migrated access token (read only).
 *
 * Props:
 *   name  the principal to show
 *   kind  USER or MACHINE; it decides which facts and sections appear
 * Emits changed and deleted (after an action). The way back is the page frame's: its back arrow on a
 * narrow window, its list crumb on a wide one, so this component draws no back control of its own.
 */
const props = defineProps({
  name: {
    type: String,
    required: true
  },
  kind: {
    type: String as PropType<PrincipalKind>,
    required: true,
    validator: (value) => ['USER', 'MACHINE'].includes(value as string)
  }
})

const emit = defineEmits(['changed', 'deleted'])

const { client, instanceAdmin: viewerIsAdmin } = useSession()
const api = computed(() => client.value.identity.principals)
const tokensApi = computed(() => client.value.identity.principalTokens(props.name))

const isMachine = computed(() => props.kind === 'MACHINE')
// The second factors of a user are shown to an instance administrator only, and only for a user.
const factorsApi = computed(() => client.value.identity.principals.factors(props.name))
const canSeeFactors = computed(() => viewerIsAdmin?.value === true)

const detail = ref<PrincipalDetail | null>(null)
const status = ref<'loading' | 'ready' | 'missing' | 'error'>('loading')
const failure = ref('')
// The rights of a migrated access token. undefined while they are being read, null when the
// machine has no migrated token.
const legacy = ref<LegacyGrant[] | null | undefined>(undefined)

let latest = 0

const loadLegacy = async (request: number) => {
  if (!isMachine.value) return
  try {
    const response: AxiosResponse<LegacyToken> = await client.value.tokens.get(props.name)
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
    const response: AxiosResponse<PrincipalDetailAnswer> = await api.value.get(props.name)
    if (request !== latest) return
    detail.value = detailOf(response.data)
    status.value = 'ready'
  } catch (error) {
    if (request !== latest) return
    detail.value = null
    status.value = (error as AxiosError | undefined)?.response?.status === 404 ? 'missing' : 'error'
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

const seen = computed(() => seenLabel(detail.value?.lastSeenAt ?? null))
const used = computed(() => usedLabel(newestLastUsed(detail.value?.tokens)))
// createdAt is an Instant on the wire: epoch seconds, read through the shared helper.
const createdText = computed(() => {
  const date = instantToDate(detail.value?.createdAt)
  return date ? formatDate(date) : 'Unknown'
})
const admin = computed(() => isInstanceAdmin(detail.value))

// The plain facts, in order. The last sign-in (it carries a title) and the teams (chips) follow as slot rows.
const rows = computed(() => {
  const current = detail.value
  if (!current) return []
  return [
    { label: isMachine.value ? 'Name' : 'Username', value: current.name, testId: 'username' },
    { label: 'Display name', value: current.displayName || 'None', testId: 'principal-display-name' },
    { label: 'Status', value: current.disabled ? 'Disabled' : 'Active', testId: 'status' },
    ...(isMachine.value ? [] : [{ label: 'Instance admin', value: admin.value ? 'Yes' : 'No', testId: 'instance-admin' }]),
    { label: 'Created', value: createdText.value }
  ]
})
</script>

<template>
  <section
    class="flex min-w-0 flex-col gap-4"
    data-ingot-component="principal-detail"
  >
    <MdStatePanel
      v-if="status === 'loading'"
      data-ingot-state="loading"
      state="loading"
    />
    <div
      v-else-if="status === 'missing'"
      role="alert"
      class="md-body-large text-on-surface-variant"
      data-test="missing"
    >
      {{ name }} does not exist (any more).
    </div>
    <MdStatePanel
      v-else-if="status === 'error'"
      state="error"
      :title="`Could not load ${name}`"
      :description="failure"
      @retry="load()"
    />

    <template v-else-if="detail">
      <MdCard class="flex flex-col gap-4">
        <div
          class="flex flex-wrap items-start justify-between gap-2"
          data-test="heading-row"
        >
          <div class="flex min-w-0 flex-col gap-2">
            <h2 class="m-0 min-w-0 break-words md-title-large text-on-surface">
              {{ detail.displayName || detail.name }}
            </h2>
            <p
              v-if="detail.displayName && detail.displayName !== detail.name"
              data-test="heading-username"
              class="m-0 min-w-0 break-words md-body-medium text-on-surface-variant"
            >
              {{ detail.name }}
            </p>
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
          <div class="flex max-w-full flex-wrap items-center gap-2">
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

        <MdKeyValueList :items="rows">
          <dt class="md-label-large text-on-surface-variant">
            {{ isMachine ? 'Last used' : 'Last sign-in' }}
          </dt>
          <dd
            class="m-0 min-w-0 md-body-large"
            data-test="last-activity"
            :title="isMachine ? used.title : seen.title"
          >
            {{ isMachine ? used.text : seen.text }}
          </dd>
          <dt class="md-label-large text-on-surface-variant">
            Teams
          </dt>
          <dd class="m-0 flex min-w-0 flex-wrap items-center gap-2 md-body-large">
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
        </MdKeyValueList>
      </MdCard>

      <PrincipalFactorsCard
        v-if="!isMachine && canSeeFactors"
        :name="name"
        :api="factorsApi"
      />

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
        <MdCard
          v-else-if="legacy === null"
          variant="low"
        >
          <p
            data-test="team-rights-hint"
            class="md-body-medium text-on-surface-variant"
          >
            This token's rights come from the teams of this machine account.
          </p>
        </MdCard>
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
