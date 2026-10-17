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
import { onMounted, ref } from 'vue'
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import TokenTag from '../tokens/TokenTag.vue'
import CreateTokenDialog from './CreateTokenDialog.vue'
import RevokeTokenDialog from './RevokeTokenDialog.vue'
import RotateTokenDialog from './RotateTokenDialog.vue'
import SecretOnceDialog from './SecretOnceDialog.vue'
import { instantToDate, localDateToDate } from '../../helpers/apiTime'
import { formatDate, formatDateTime, formatRelative } from './identityTokens'

/*
 * A card with the tokens of one principal and everything that changes them: the list, the
 * create dialog, the confirmations for rotating and revoking, and the dialog that shows a new
 * secret once. The self service page and the admin pages use it with their own endpoints.
 *
 * Props:
 *   api          { list, create, rotate?, revoke }, each returning the axios promise.
 *                list resolves to { data: [TokenDto] }; create and rotate resolve to
 *                { data: { token, secret } }. Without rotate the row has no Rotate action.
 *   title        the card heading
 *   description  the line under the heading
 *   emptyText    the sentence of the empty state
 *
 * The secret of a new or rotated token is kept in this component's memory only, and is
 * dropped when its dialog closes.
 */
const props = defineProps({
  api: {
    type: Object,
    required: true
  },
  title: {
    type: String,
    default: 'My tokens'
  },
  description: {
    type: String,
    default: 'Tokens sign in tools and scripts as you.'
  },
  emptyText: {
    type: String,
    default: 'You have no tokens yet. Create one to sign in a build tool or a script.'
  }
})

const tokens = ref([])
const loaded = ref(false)
const loading = ref(false)
const loadError = ref(null)

const createOpen = ref(false)
const rotating = ref(null)
const revoking = ref(null)
const rotateOpen = ref(false)
const revokeOpen = ref(false)

const secretOpen = ref(false)
const shownSecret = ref({ name: '', value: '' })

let latestRequest = 0

const load = async () => {
  const request = ++latestRequest
  loading.value = true
  loadError.value = null
  try {
    const response = await props.api.list()
    if (request !== latestRequest) return
    tokens.value = response.data ?? []
    loaded.value = true
  } catch (error) {
    if (request === latestRequest) loadError.value = errorMessage(error)
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

onMounted(load)

const showSecret = ({ token, secret }) => {
  shownSecret.value = { name: token?.name ?? '', value: secret }
  secretOpen.value = true
  load()
}

const closeSecret = (open) => {
  secretOpen.value = open
  if (!open) {
    shownSecret.value = { name: '', value: '' }
  }
}

const askRotate = (token) => {
  rotating.value = token
  rotateOpen.value = true
}

const askRevoke = (token) => {
  revoking.value = token
  revokeOpen.value = true
}

const revoked = (token) => {
  createSuccessToast(`Token ${token.name} revoked`)
  load()
}

const scopeText = (token) =>
  token.scopes === null || token.scopes === undefined ? null : token.scopes

defineExpose({ reload: load })
// The wire values of a token: createdAt is a LocalDate, expiresAt and lastUsedAt are Instants.
const createdDay = (token) => {
  const date = localDateToDate(token.createdAt)
  return date ? formatDate(date) : ''
}
const expiryDay = (token) => {
  const date = instantToDate(token.expiresAt)
  return date ? formatDate(date) : ''
}
const lastUsedTitle = (token) => {
  const date = instantToDate(token.lastUsedAt)
  return date ? formatDateTime(date) : undefined
}
const lastUsedText = (token) => {
  const date = instantToDate(token.lastUsedAt)
  return date ? formatRelative(date) : 'Never used'
}
</script>

<template>
  <section
    class="overflow-hidden rounded-corner-large bg-surface-container-low"
    aria-labelledby="token-list-title"
  >
    <div class="flex flex-wrap items-start justify-between gap-3 px-4 py-4">
      <div class="flex min-w-0 flex-col gap-1">
        <h3
          id="token-list-title"
          class="md-title-large text-on-surface"
        >
          {{ title }}
        </h3>
        <p class="md-body-medium text-on-surface-variant">
          {{ description }}
        </p>
      </div>
      <MdButton
        variant="tonal"
        @click="createOpen = true"
      >
        Create token
      </MdButton>
    </div>

    <div
      v-if="loadError"
      role="alert"
      class="flex flex-wrap items-center justify-between gap-3 border-t border-outline-variant px-4 py-3"
    >
      <span class="md-body-medium text-error">Could not load the tokens: {{ loadError }}</span>
      <MdButton
        variant="text"
        @click="load()"
      >
        Retry
      </MdButton>
    </div>

    <div
      v-else-if="loaded && tokens.length === 0"
      class="flex flex-col items-center gap-4 border-t border-outline-variant px-4 py-12 text-center"
    >
      <p class="md-body-large text-on-surface-variant">
        {{ emptyText }}
      </p>
      <MdButton
        variant="tonal"
        @click="createOpen = true"
      >
        Create your first token
      </MdButton>
    </div>

    <p
      v-else-if="!loaded"
      class="border-t border-outline-variant px-4 py-6 md-body-medium text-on-surface-variant"
      role="status"
    >
      Loading tokens...
    </p>

    <ul
      v-else
      class="m-0 list-none divide-y divide-outline-variant border-t border-outline-variant p-0"
    >
      <li
        v-for="token in tokens"
        :key="token.name"
        data-test="token-row"
        class="flex flex-col gap-3 px-4 py-4 min-[720px]:flex-row min-[720px]:items-start min-[720px]:justify-between"
      >
        <div class="flex min-w-0 flex-1 flex-col gap-2">
          <div class="flex min-w-0 flex-col">
            <span class="md-title-medium break-words text-on-surface">{{ token.name }}</span>
            <span
              v-if="token.description"
              class="md-body-medium break-words text-on-surface-variant"
            >
              {{ token.description }}
            </span>
          </div>
          <dl class="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 md-body-small text-on-surface-variant min-[480px]:grid-cols-[repeat(3,auto)] min-[480px]:justify-start">
            <div class="contents min-[480px]:flex min-[480px]:flex-col">
              <dt>Created</dt>
              <dd class="m-0 text-on-surface">
                {{ createdDay(token) }}
              </dd>
            </div>
            <div class="contents min-[480px]:flex min-[480px]:flex-col">
              <dt>Expires</dt>
              <dd class="m-0 text-on-surface">
                {{ token.expiresAt ? expiryDay(token) : 'Never' }}
              </dd>
            </div>
            <div class="contents min-[480px]:flex min-[480px]:min-w-0 min-[480px]:flex-col">
              <dt>Last used</dt>
              <dd
                class="m-0 min-w-0 text-on-surface"
                data-test="last-used"
                :title="lastUsedTitle(token)"
              >
                {{ lastUsedText(token) }}
                <span
                  v-if="token.lastUsedAt && token.lastUsedIp"
                  class="flex min-w-0 items-baseline gap-1 text-on-surface-variant"
                  data-test="last-used-ip"
                >
                  <span class="shrink-0">from</span>
                  <span
                    class="min-w-0 truncate font-mono"
                    data-test="last-used-address"
                    :title="token.lastUsedIp"
                  >{{ token.lastUsedIp }}</span>
                </span>
              </dd>
            </div>
          </dl>
          <div class="flex flex-wrap items-center gap-2">
            <template v-if="scopeText(token)">
              <TokenTag
                v-for="scope in scopeText(token)"
                :key="scope"
                data-test="scope"
              >
                {{ scope }}
              </TokenTag>
            </template>
            <span
              v-else
              class="md-body-small text-on-surface-variant"
            >
              All rights of your teams
            </span>
          </div>
        </div>
        <div class="flex shrink-0 gap-2">
          <MdButton
            v-if="api.rotate"
            variant="outlined"
            :aria-label="`Rotate ${token.name}`"
            @click="askRotate(token)"
          >
            Rotate
          </MdButton>
          <MdButton
            variant="text"
            error
            :aria-label="`Revoke ${token.name}`"
            @click="askRevoke(token)"
          >
            Revoke
          </MdButton>
        </div>
      </li>
    </ul>

    <CreateTokenDialog
      v-model="createOpen"
      :api="api"
      @created="showSecret"
    />
    <RotateTokenDialog
      v-model="rotateOpen"
      :token="rotating"
      :api="api"
      @rotated="showSecret"
    />
    <RevokeTokenDialog
      v-model="revokeOpen"
      :token="revoking"
      :api="api"
      @revoked="revoked"
    />
    <SecretOnceDialog
      :model-value="secretOpen"
      :secret="shownSecret.value"
      :token-name="shownSecret.name"
      @update:model-value="closeSecret"
    />
  </section>
</template>
