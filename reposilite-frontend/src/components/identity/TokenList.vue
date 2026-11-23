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
import { onMounted, ref } from 'vue'
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdKeyValueList from '../md/MdKeyValueList.vue'
import MdList from '../md/MdList.vue'
import MdListItem from '../md/MdListItem.vue'
import MdSection from '../md/MdSection.vue'
import TokenTag from '../tokens/TokenTag.vue'
import CreateTokenDialog from './CreateTokenDialog.vue'
import RevokeTokenDialog from './RevokeTokenDialog.vue'
import RotateTokenDialog from './RotateTokenDialog.vue'
import SecretOnceDialog from './SecretOnceDialog.vue'
import { instantToDate, localDateToDate } from '../../helpers/apiTime'
import { formatDate, formatDateTime, formatRelative } from './identityTokens'
import type { IdentityToken, IdentityTokenApi, TokenSecret } from './identityTokens'

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
const props = withDefaults(defineProps<{
  api: IdentityTokenApi
  title?: string
  description?: string
  emptyText?: string
}>(), {
  title: 'My tokens',
  description: 'Tokens sign in tools and scripts as you.',
  emptyText: 'You have no tokens yet. Create one to sign in a build tool or a script.'
})

const tokens = ref<IdentityToken[]>([])
const loaded = ref(false)
const loading = ref(false)
const loadError = ref<string | null>(null)

const createOpen = ref(false)
const rotating = ref<IdentityToken | null>(null)
const revoking = ref<IdentityToken | null>(null)
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

const showSecret = ({ token, secret }: TokenSecret) => {
  shownSecret.value = { name: token?.name ?? '', value: secret }
  secretOpen.value = true
  load()
}

const closeSecret = (open: boolean) => {
  secretOpen.value = open
  if (!open) {
    shownSecret.value = { name: '', value: '' }
  }
}

const askRotate = (token: IdentityToken) => {
  rotating.value = token
  rotateOpen.value = true
}

const askRevoke = (token: IdentityToken) => {
  revoking.value = token
  revokeOpen.value = true
}

const revoked = (token: IdentityToken) => {
  createSuccessToast(`Token ${token.name} revoked`)
  load()
}

const scopeText = (token: IdentityToken): string[] | null =>
  token.scopes === null || token.scopes === undefined ? null : token.scopes

defineExpose({ reload: load })
// The wire values of a token: createdAt is a LocalDate, expiresAt and lastUsedAt are Instants.
const createdDay = (token: IdentityToken) => {
  const date = localDateToDate(token.createdAt)
  return date ? formatDate(date) : ''
}
const expiryDay = (token: IdentityToken) => {
  const date = instantToDate(token.expiresAt)
  return date ? formatDate(date) : ''
}
const lastUsedTitle = (token: IdentityToken) => {
  const date = instantToDate(token.lastUsedAt)
  return date ? formatDateTime(date) : undefined
}
const lastUsedText = (token: IdentityToken) => {
  const date = instantToDate(token.lastUsedAt)
  return date ? formatRelative(date) : 'Never used'
}
</script>

<template>
  <section
    aria-labelledby="token-list-title"
    data-ingot-component="token-list"
  >
    <MdCard>
      <MdSection
        :title="title"
        :description="description"
        :level="3"
        heading-id="token-list-title"
      >
        <template #actions>
          <MdButton
            variant="tonal"
            @click="createOpen = true"
          >
            Create token
          </MdButton>
        </template>

        <div v-if="loadError">
          <MdAlert
            tone="error"
            data-ingot-state="error"
          >
            Could not load the tokens: {{ loadError }}
            <template #action>
              <MdButton
                variant="text"
                @click="load()"
              >
                Retry
              </MdButton>
            </template>
          </MdAlert>
        </div>

        <div
          v-else-if="loaded && tokens.length === 0"
          class="flex flex-col items-center gap-4 py-8 text-center"
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
          class="md-body-medium text-on-surface-variant"
          role="status"
        >
          Loading tokens...
        </p>

        <MdList
          v-else
          divided
        >
          <MdListItem
            v-for="token in tokens"
            :key="token.name"
            data-test="token-row"
          >
            <div class="flex flex-col gap-3 py-1 medium:flex-row medium:items-start medium:justify-between">
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
                <MdKeyValueList>
                  <div class="contents">
                    <dt class="md-label-large text-on-surface-variant">
                      Created
                    </dt>
                    <dd class="m-0 min-w-0 md-body-large">
                      {{ createdDay(token) }}
                    </dd>
                  </div>
                  <div class="contents">
                    <dt class="md-label-large text-on-surface-variant">
                      Expires
                    </dt>
                    <dd class="m-0 min-w-0 md-body-large">
                      {{ token.expiresAt ? expiryDay(token) : 'Never' }}
                    </dd>
                  </div>
                  <div class="contents">
                    <dt class="md-label-large text-on-surface-variant">
                      Last used
                    </dt>
                    <dd
                      class="m-0 min-w-0 md-body-large"
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
                          class="min-w-0 truncate md-code-medium"
                          data-test="last-used-address"
                          :title="token.lastUsedIp"
                        >{{ token.lastUsedIp }}</span>
                      </span>
                    </dd>
                  </div>
                </MdKeyValueList>
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
            </div>
          </MdListItem>
        </MdList>
      </MdSection>
    </MdCard>

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
