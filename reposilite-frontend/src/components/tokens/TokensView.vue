<!--
  - Copyright (c) 2023 dzikoysk
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
import { ref, computed, watch } from 'vue'
import { createErrorToast, createWarningToast } from '../../helpers/toast'
import { useTokens } from '../../store/tokens'
import { property } from '../../helpers/vue-extensions'
import PencilIcon from '../icons/PencilIcon.vue'
import TrashIcon from '../icons/TrashIcon.vue'
import ActionButton from './ActionButton.vue'
import IconButton from './IconButton.vue'
import RouteEditor from './RouteEditor.vue'
import TokenCreateForm from './TokenCreateForm.vue'
import TokenMetaForm from './TokenMetaForm.vue'
import TokenRow from './TokenRow.vue'
import TokenRowActions from './TokenRowActions.vue'
import TokenSummary from './TokenSummary.vue'

const props = defineProps({
  selectedTab: property(String, true)
})

const { tokens, fetchTokens, createToken, saveTokenMeta, saveRoute, removeRoute, deleteToken, regenerateSecret, groupRoutes, tokenIsManager, toMs, errorMessage } = useTokens()

watch(
  () => props.selectedTab,
  (selectedTab, prev) => {
    if (selectedTab === 'Tokens' && prev === undefined)
      fetchTokens()
  },
  { immediate: true }
)

const query = ref('')
const editing = ref(null)
const draft = ref({})
const secret = ref(null)
const confirming = ref(null)

const tid = (token) => token.name
const isOpen = (key) => editing.value === key
const close = () => { editing.value = null }

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return tokens.value
  return tokens.value.filter(token =>
    (token.name + ' ' + (token.description || '')).toLowerCase().includes(q) ||
    (token.routes || []).some(route => route.path.toLowerCase().includes(q)))
})

const toDateInput = (value) => {
  if (!value) return ''
  const date = new Date(toMs(value))
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}
const minExpiry = () => toDateInput(Date.now())
const routeLabel = (route) => [route.read && 'read', route.write && 'write'].filter(Boolean).join(' · ')

const editToken = (token) => {
  if (editing.value === `token:${tid(token)}`) { close(); return }
  confirming.value = null
  draft.value = { description: token.description || '', manager: tokenIsManager(token), expiresAt: toDateInput(token.expiresAt) }
  editing.value = `token:${tid(token)}`
}
const saveToken = (token) =>
  saveTokenMeta(token, draft.value).then(ok => { if (ok) close() })

const editRoute = (token, route) => { confirming.value = null; editing.value = `route:${tid(token)}:${route.path}`; draft.value = { path: route.path, read: route.read, write: route.write, original: route.path } }
const addRoute = (token) => { confirming.value = null; editing.value = `newroute:${tid(token)}`; draft.value = { path: '', read: true, write: false } }
const persistRoute = (token) => {
  let path = (draft.value.path || '').trim()
  if (!path) return
  if (!path.startsWith('/')) path = '/' + path
  if (!draft.value.read && !draft.value.write) { createWarningToast('Select read and/or write'); return }
  saveRoute(token, { ...draft.value, path }, draft.value.original).then(ok => { if (ok) close() })
}

const startCreate = () => { confirming.value = null; editing.value = 'newtoken'; draft.value = { name: '', type: 'PERSISTENT' } }
const create = () => {
  const name = (draft.value.name || '').trim()
  if (name === '') { createWarningToast('Token name is required'); return }
  if (/[:/]/.test(name)) { createWarningToast("Token name cannot contain ':' or '/'"); return }
  if (tokens.value.some(token => token.name === name)) { createWarningToast(`A token named '${name}' already exists`); return }
  createToken(name, { type: draft.value.type })
    .then(response => { secret.value = { name, value: response.secret }; close() })
    .catch(error => createErrorToast(errorMessage(error)))
}

const ask = (token, action) => { editing.value = null; confirming.value = { id: tid(token), action } }
const isConfirming = (token) => confirming.value?.id === tid(token)
const runConfirm = (token) => {
  const { action } = confirming.value
  confirming.value = null
  if (action === 'revoke') deleteToken(token.name)
  else regenerateSecret(token.name)
    .then(value => { secret.value = { name: token.name, value } })
    .catch(error => createErrorToast(errorMessage(error)))
}
</script>

<template>
  <div class="container mx-auto px-15 pb-12 pt-7 max-sm:px-4">
    <div class="pb-7">
      <p>Generate and revoke access tokens used to authenticate with this Ingot instance.</p>
      <p class="text-sm text-gray-500">
        A token's secret is shown only once, at the moment it is generated.
      </p>
    </div>

    <!-- The card draws the closing line itself, so the last row inside it drops its own. -->
    <div class="overflow-hidden rounded-lg bg-white text-sm text-gray-600 [&>*:last-child]:border-b-0 dark:border dark:border-gray-800 dark:bg-transparent dark:text-gray-300">
      <div class="flex items-center gap-3 border-b border-gray-200 bg-white px-3.5 py-3.5 dark:border-gray-800 dark:bg-transparent">
        <div class="flex h-9 flex-1 items-center gap-2 rounded-md border border-gray-300 bg-white px-3 dark:border-gray-700 dark:bg-gray-800">
          <svg viewBox="0 0 24 24" class="h-4 w-4 flex-shrink-0 text-gray-400">
            <path
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              d="M21 21l-4.3-4.3m1.3-5.2a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <input
            v-model="query"
            aria-label="Search tokens and routes"
            placeholder="Search tokens, routes…"
            class="flex-1 bg-transparent text-gray-700 placeholder-gray-500 outline-none dark:text-gray-200 dark:placeholder-gray-400"
          >
        </div>
        <ActionButton primary @click="startCreate">
          + Generate token
        </ActionButton>
      </div>

      <TokenCreateForm
        v-if="isOpen('newtoken')"
        v-model:name="draft.name"
        v-model:type="draft.type"
        @submit="create"
        @cancel="close"
      />

      <div v-if="secret" class="border-b border-gray-200 bg-blue-50 px-4.5 py-3 text-blue-900 dark:border-gray-800 dark:bg-blue-900 dark:text-blue-100">
        New secret for <strong>{{ secret.name }}</strong>: <code class="px-1 font-mono">{{ secret.value }}</code> — copy it now.
        <button type="button" class="ml-2 underline" @click="secret = null">
          Dismiss
        </button>
      </div>

      <template v-for="token in filtered" :key="tid(token)">
        <TokenRow :highlighted="isOpen(`token:${tid(token)}`)">
          <TokenSummary :token="token" />
          <template #actions>
            <TokenRowActions
              :name="token.name"
              :confirm-action="isConfirming(token) ? confirming.action : null"
              @edit="editToken(token)"
              @regenerate="ask(token, 'regenerate')"
              @revoke="ask(token, 'revoke')"
              @confirm="runConfirm(token)"
              @cancel="confirming = null"
            />
          </template>
        </TokenRow>

        <TokenMetaForm
          v-if="isOpen(`token:${tid(token)}`)"
          v-model:description="draft.description"
          v-model:manager="draft.manager"
          v-model:expiry="draft.expiresAt"
          :min-expiry="minExpiry()"
          @submit="saveToken(token)"
          @cancel="close"
        />

        <template v-if="!tokenIsManager(token)">
          <template v-for="route in groupRoutes(token)" :key="route.path">
            <TokenRow indent reveal-actions-on-hover :highlighted="isOpen(`route:${tid(token)}:${route.path}`)">
              <span class="whitespace-nowrap font-mono text-gray-700 dark:text-gray-200">{{ route.path }}</span>
              <span class="truncate text-gray-500 dark:text-gray-500">{{ routeLabel(route) }}</span>
              <template #actions>
                <IconButton :label="`Edit route ${route.path}`" @click="editRoute(token, route)">
                  <PencilIcon />
                </IconButton>
                <IconButton destructive :label="`Remove route ${route.path}`" @click="removeRoute(token, route.path)">
                  <TrashIcon />
                </IconButton>
              </template>
            </TokenRow>
            <RouteEditor
              v-if="isOpen(`route:${tid(token)}:${route.path}`)"
              v-model:path="draft.path"
              v-model:read="draft.read"
              v-model:write="draft.write"
              submit-label="Save"
              @submit="persistRoute(token)"
              @cancel="close"
            />
          </template>

          <TokenRow
            v-if="!isOpen(`newroute:${tid(token)}`)"
            indent
            class="cursor-pointer text-blue-600 hover:text-blue-700 dark:text-blue-300 dark:hover:text-blue-200"
            @click="addRoute(token)"
          >
            <span class="whitespace-nowrap">+ Add route</span>
          </TokenRow>
          <RouteEditor
            v-else
            v-model:path="draft.path"
            v-model:read="draft.read"
            v-model:write="draft.write"
            submit-label="Add"
            placeholder="/releases/com/example/artifact"
            @submit="persistRoute(token)"
            @cancel="close"
          />
        </template>
        <TokenRow v-else indent :hoverable="false" class="text-gray-500 dark:text-gray-500">
          Full access to all repositories
        </TokenRow>
      </template>

      <div v-if="!filtered.length" class="px-4.5 py-10 text-center text-gray-500 dark:text-gray-400">
        {{ query ? `No tokens match “${query}”.` : 'No access tokens yet. Generate one to get started.' }}
      </div>
    </div>
  </div>
</template>
