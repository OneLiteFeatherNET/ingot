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
import { createErrorToast } from '../../helpers/toast'
import { useTokens, checkRouteDraft, validateTokenName, isDraftDirty } from '../../store/tokens'
import { property } from '../../helpers/vue-extensions'
import PencilIcon from '../icons/PencilIcon.vue'
import TrashIcon from '../icons/TrashIcon.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
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

const {
  tokens, loaded, loading, loadError, secret, dismissSecret,
  fetchTokens, createToken, saveTokenMeta, saveRoute, removeRoute, deleteToken, regenerateSecret,
  groupRoutes, tokenIsManager, toMs, errorMessage
} = useTokens()

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
const baseline = ref({})
const routeError = ref(null)
const createError = ref('')
const confirming = ref(null)
// The action the user asked for while a draft still had unsaved changes. Null when nothing waits.
const pendingAction = ref(null)

const tid = (token) => token.name
const isOpen = (key) => editing.value === key

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

const close = () => {
  editing.value = null
  routeError.value = null
  createError.value = ''
}

const startEditor = (key, initial) => {
  confirming.value = null
  routeError.value = null
  createError.value = ''
  editing.value = key
  draft.value = { ...initial }
  baseline.value = { ...initial }
}

// Opening another editor, or asking for a confirmation, throws away the open draft. With
// unsaved changes the action waits until the user says so in the inline notice.
const guard = (action) => {
  const hasUnsaved = editing.value !== null && isDraftDirty(draft.value, baseline.value)
  if (hasUnsaved) pendingAction.value = action
  else action()
}
const discardAndContinue = () => {
  const action = pendingAction.value
  pendingAction.value = null
  close()
  action()
}
const keepEditing = () => { pendingAction.value = null }

const editToken = (token) => {
  const key = `token:${tid(token)}`
  if (isOpen(key)) { guard(close); return }
  guard(() => startEditor(key, {
    description: token.description || '',
    manager: tokenIsManager(token),
    expiresAt: toDateInput(token.expiresAt)
  }))
}
const saveToken = (token) =>
  saveTokenMeta(token, draft.value).then(ok => { if (ok) close() })

const openRoute = (token, route) => startEditor(`route:${tid(token)}:${route.path}`, {
  path: route.path, read: route.read, write: route.write, original: route.path
})
const editRoute = (token, route) => guard(() => openRoute(token, route))
const addRoute = (token) => guard(() => startEditor(`newroute:${tid(token)}`, { path: '', read: true, write: false }))

const persistRoute = (token) => {
  const check = checkRouteDraft(token, draft.value)
  if (check.error === 'empty') {
    routeError.value = { message: 'Enter a route path' }
    return
  }
  if (check.error === 'duplicate') {
    routeError.value = { message: `A route for ${check.existing.path} already exists.`, existing: check.existing }
    return
  }
  if (!draft.value.read && !draft.value.write) {
    routeError.value = { message: 'Select read, write or both' }
    return
  }
  saveRoute(token, { path: check.path, read: draft.value.read, write: draft.value.write }, draft.value.original)
    .then(ok => { if (ok) close() })
}

const startCreate = () => guard(() => startEditor('newtoken', { name: '', type: 'PERSISTENT' }))

const nameError = (name) => {
  switch (validateTokenName(name, tokens.value)) {
    case 'empty': return 'Token name is required'
    case 'invalid': return "Token name cannot contain ':' or '/'"
    case 'duplicate': return `A token named '${name.trim()}' already exists`
    default: return ''
  }
}

const create = () => {
  const name = (draft.value.name || '').trim()
  createError.value = nameError(draft.value.name || '')
  if (createError.value) return
  createToken(name, { type: draft.value.type })
    .then(() => close())
    .catch(error => createErrorToast(errorMessage(error)))
}

const ask = (token, action) => guard(() => {
  editing.value = null
  confirming.value = { id: tid(token), action }
})
const isConfirming = (token) => confirming.value?.id === tid(token)
const runConfirm = (token) => {
  const { action } = confirming.value
  confirming.value = null
  if (action === 'revoke') deleteToken(token.name)
  else regenerateSecret(token.name)
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

      <div v-if="pendingAction" role="group" aria-label="Unsaved changes" class="flex flex-wrap items-center gap-3 border-b border-gray-200 bg-gray-100 px-4.5 py-3 dark:border-gray-800 dark:bg-gray-900">
        <span>You have unsaved changes. Discard them to continue?</span>
        <ActionButton small @click="discardAndContinue">
          Discard changes
        </ActionButton>
        <ActionButton small @click="keepEditing">
          Keep editing
        </ActionButton>
      </div>

      <TokenCreateForm
        v-if="isOpen('newtoken')"
        v-model:name="draft.name"
        v-model:type="draft.type"
        :error="createError"
        @submit="create"
        @cancel="close"
      />

      <div v-if="secret" role="status" class="flex flex-wrap items-center gap-2 border-b border-gray-200 bg-primary-container px-4.5 py-3 text-on-primary-container dark:border-gray-800">
        New secret for <strong>{{ secret.name }}</strong>: <code class="px-1 font-mono">{{ secret.value }}</code>.
        You won't see this secret again.
        <button type="button" class="ml-2 underline" @click="dismissSecret">
          Dismiss
        </button>
      </div>

      <div v-if="loading" class="border-b border-gray-200 dark:border-gray-800">
        <MdLinearProgress indeterminate label="Loading tokens" />
      </div>

      <div v-if="loadError" role="alert" class="flex flex-wrap items-center gap-3 border-b border-gray-200 px-4.5 py-4 text-error dark:border-gray-800">
        <span>Could not load tokens: {{ loadError }}</span>
        <ActionButton small @click="fetchTokens">
          Retry
        </ActionButton>
      </div>

      <div v-else-if="!loaded" class="px-4.5 py-10 text-center text-gray-500 dark:text-gray-400">
        Loading tokens…
      </div>

      <template v-else>
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

          <TokenRow v-if="tokenIsManager(token)" indent :hoverable="false" class="text-gray-500 dark:text-gray-500">
            Full access to all repositories
          </TokenRow>
          <TokenRow v-if="tokenIsManager(token) && groupRoutes(token).length" indent :hoverable="false" class="text-gray-500 dark:text-gray-500">
            Inactive while the token has manager access. The routes below apply again once manager is turned off.
          </TokenRow>

          <template v-for="route in groupRoutes(token)" :key="route.path">
            <TokenRow
              indent
              reveal-actions-on-hover
              :highlighted="isOpen(`route:${tid(token)}:${route.path}`)"
              :class="tokenIsManager(token) ? 'text-gray-500 dark:text-gray-500' : ''"
            >
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
              :error="routeError"
              @submit="persistRoute(token)"
              @cancel="close"
              @edit-existing="openRoute(token, routeError.existing)"
            />
          </template>

          <TokenRow v-if="!isOpen(`newroute:${tid(token)}`)" indent>
            <button
              type="button"
              class="whitespace-nowrap text-primary hover:text-primary/80"
              @click="addRoute(token)"
            >
              + Add route
            </button>
          </TokenRow>
          <RouteEditor
            v-else
            v-model:path="draft.path"
            v-model:read="draft.read"
            v-model:write="draft.write"
            submit-label="Add"
            placeholder="/releases/com/example/artifact"
            :error="routeError"
            @submit="persistRoute(token)"
            @cancel="close"
            @edit-existing="openRoute(token, routeError.existing)"
          />
        </template>

        <div v-if="!filtered.length" class="px-4.5 py-10 text-center text-gray-500 dark:text-gray-400">
          {{ query ? `No tokens match “${query}”.` : 'No access tokens yet. Generate one to get started.' }}
        </div>
      </template>
    </div>
  </div>
</template>
