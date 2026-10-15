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
import { ref, computed, inject, watch } from 'vue'
import { createErrorToast } from '../../helpers/toast'
import { useTokens, checkRouteDraft, validateTokenName, isDraftDirty } from '../../store/tokens'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { property } from '../../helpers/vue-extensions'
import { editorFromToken, tokenFromEditor } from '../../router/url-state'
import PencilIcon from '../icons/PencilIcon.vue'
import TrashIcon from '../icons/TrashIcon.vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import MdTextField from '../md/MdTextField.vue'
import RouteEditor from './RouteEditor.vue'
import TokenCreateForm from './TokenCreateForm.vue'
import TokenMetaForm from './TokenMetaForm.vue'
import TokenRow from './TokenRow.vue'
import TokenRowActions from './TokenRowActions.vue'
import TokenSecretBanner from './TokenSecretBanner.vue'
import TokenSummary from './TokenSummary.vue'
import ConflictDialog from '../settings/ConflictDialog.vue'

const props = defineProps({
  selectedTab: property(String, true),
  /**
   * The search text, as the address holds it. The view reports a change through update:search.
   */
  search: {
    type: String,
    default: ''
  },
  /**
   * The token whose editor the address names: a token name, `new` for the create form, or null
   * for the list. Reported through update:openToken when the editor opens or closes.
   */
  openToken: {
    type: String,
    default: null
  },
  /**
   * The reference time in epoch milliseconds, used to judge expiry dates. The view leaves it out and
   * gets the current time; tests pass a fixed value so that the result does not depend on the day it runs.
   */
  now: {
    type: Number,
    default: undefined
  }
})

const emit = defineEmits(['update:search', 'update:openToken'])

const currentMs = () => props.now ?? Date.now()

const {
  tokens, loaded, loading, loadError, secret, dismissSecret,
  conflict, reloadTokenConflict, overwriteTokenConflict, dismissTokenConflict,
  fetchTokens, createToken, saveTokenMeta, saveRoute, removeRoute, deleteToken, regenerateSecret,
  groupRoutes, tokenIsManager, toMs, errorMessage
} = useTokens()

const configuration = inject(configurationStoreKey, null) ?? useConfiguration().store

/*
 * The repositories the route picker offers: the ones the server has saved, so a route never points at
 * a repository that only exists as a draft. Null until the settings have loaded.
 */
const repositoryIds = computed(() => {
  const saved = configuration.savedConfiguration('maven')
  return saved ? (saved.repositories ?? []).map(repository => repository.id) : null
})

// The saved settings are fetched the first time a route is edited. Once they are loaded they are not
// fetched again here, so an edit in progress in the settings page is never replaced.
const loadRepositories = () => {
  if (configuration.domains.value.length === 0) configuration.fetchConfiguration()
}

watch(
  () => props.selectedTab,
  (selectedTab, prev) => {
    if (selectedTab === 'Tokens' && prev === undefined)
      fetchTokens()
  },
  { immediate: true }
)

// The search box keeps its own text while it is typed in, and reports every change upwards.
// The address catches up through the router; the box follows the address when it moves on its own.
const query = ref(props.search)
watch(() => props.search, (search) => {
  if (search !== query.value) query.value = search
})
watch(query, (text) => {
  if (text !== props.search) emit('update:search', text)
})

const editing = ref(null)
const draft = ref({})
const baseline = ref({})
const routeError = ref(null)
const createError = ref('')
const expiryError = ref('')
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
const routeLabel = (route) => [route.read && 'read', route.write && 'write'].filter(Boolean).join(' · ')

const close = () => {
  editing.value = null
  routeError.value = null
  createError.value = ''
  expiryError.value = ''
}

const startEditor = (key, initial) => {
  confirming.value = null
  routeError.value = null
  createError.value = ''
  expiryError.value = ''
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

const tokenDraft = (token) => ({
  description: token.description || '',
  manager: tokenIsManager(token),
  expiresAt: toDateInput(token.expiresAt)
})

const editToken = (token) => {
  const key = `token:${tid(token)}`
  if (isOpen(key)) { guard(close); return }
  guard(() => startEditor(key, tokenDraft(token)))
}

const saveToken = (token) => {
  const expiryChanged = draft.value.expiresAt !== baseline.value.expiresAt
  if (expiryChanged && draft.value.expiresAt && draft.value.expiresAt < toDateInput(currentMs())) {
    expiryError.value = 'Choose today or a later date, or clear the expiry.'
    return
  }
  saveTokenMeta(token, draft.value).then(ok => { if (ok) close() })
}

const openRoute = (token, route) => startEditor(`route:${tid(token)}:${route.path}`, {
  path: route.path, read: route.read, write: route.write, original: route.path
})
const editRoute = (token, route) => {
  loadRepositories()
  guard(() => openRoute(token, route))
}
const addRoute = (token) => {
  loadRepositories()
  guard(() => startEditor(`newroute:${tid(token)}`, { path: '', read: true, write: false }))
}

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
    routeError.value = { message: 'Select read, write or both', field: 'access' }
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

/*
 * The address and the open editor agree. A change of the editor is reported as the token it
 * belongs to, and a change of the address opens that token's editor. A route editor belongs to
 * its token, so the address keeps the token while the route is edited.
 */
watch(editing, (key) => {
  const name = tokenFromEditor(key)
  if (name !== props.openToken) emit('update:openToken', name)
})

// The editor for the token the address names. A token can only be opened once the list has
// arrived, so an address that is opened before the list waits for it. A name that matches no
// token opens nothing and leaves the list as it is.
const openFromAddress = () => {
  const wanted = props.openToken
  if (tokenFromEditor(editing.value) === wanted) return
  if (wanted === null) {
    close()
    return
  }
  if (wanted === 'new') {
    startEditor(editorFromToken('new'), { name: '', type: 'PERSISTENT' })
    return
  }
  const token = tokens.value.find(entry => entry.name === wanted)
  if (token) {
    startEditor(editorFromToken(wanted), tokenDraft(token))
  } else if (loaded.value) {
    close()
  }
}

watch([loaded, () => props.openToken], openFromAddress, { immediate: true })
</script>

<template>
  <div class="container mx-auto pb-12 pt-7">
    <ConflictDialog
      v-if="conflict"
      :conflict="conflict"
      @reload="reloadTokenConflict"
      @overwrite="overwriteTokenConflict"
      @dismiss="dismissTokenConflict"
    />
    <div class="flex flex-col gap-1 pb-7">
      <p class="md-body-large text-on-surface-variant">
        Generate and revoke access tokens used to authenticate with this Ingot instance.
      </p>
      <p class="md-body-medium text-on-surface-variant">
        A token's secret is shown only once, at the moment it is generated.
      </p>
    </div>

    <div class="overflow-hidden rounded-corner-large bg-surface-container-low [&>*:last-child]:border-b-0">
      <div class="flex flex-wrap items-center gap-3 border-b border-outline-variant px-4 py-4">
        <div class="min-w-60 flex-1">
          <MdTextField
            v-model="query"
            label="Search tokens and routes"
            variant="outlined"
          >
            <template #leading-icon>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                aria-hidden="true"
                focusable="false"
              >
                <path d="M21 21l-4.3-4.3m1.3-5.2a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </template>
          </MdTextField>
        </div>
        <MdButton
          variant="filled"
          @click="startCreate"
        >
          + Generate token
        </MdButton>
      </div>

      <div
        v-if="pendingAction"
        role="group"
        aria-label="Unsaved changes"
        class="flex flex-wrap items-center gap-3 border-b border-outline-variant bg-surface-container-high px-4 py-3"
      >
        <span class="md-body-medium text-on-surface">You have unsaved changes. Discard them to continue?</span>
        <MdButton
          variant="tonal"
          @click="keepEditing"
        >
          Keep editing
        </MdButton>
        <MdButton
          variant="text"
          error
          @click="discardAndContinue"
        >
          Discard changes
        </MdButton>
      </div>

      <TokenCreateForm
        v-if="isOpen('newtoken')"
        v-model:name="draft.name"
        v-model:type="draft.type"
        :error="createError"
        @submit="create"
        @cancel="close"
      />

      <TokenSecretBanner
        v-if="secret"
        :name="secret.name"
        :value="secret.value"
        @dismiss="dismissSecret"
      />

      <MdLinearProgress
        v-if="loading"
        indeterminate
        label="Loading tokens"
      />

      <div
        v-if="loadError"
        role="alert"
        class="flex flex-wrap items-center gap-3 border-b border-outline-variant px-4 py-4"
      >
        <span class="md-body-medium text-error">Could not load tokens: {{ loadError }}</span>
        <MdButton
          variant="tonal"
          @click="fetchTokens"
        >
          Retry
        </MdButton>
      </div>

      <div
        v-else-if="!loaded"
        class="md-body-medium px-4 py-12 text-center text-on-surface-variant"
      >
        Loading tokens…
      </div>

      <template v-else>
        <template
          v-for="token in filtered"
          :key="tid(token)"
        >
          <TokenRow :highlighted="isOpen(`token:${tid(token)}`)">
            <TokenSummary
              :token="token"
              :now="now"
            />
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
            :expiry-error="expiryError"
            :expiry-min="toDateInput(currentMs())"
            @submit="saveToken(token)"
            @cancel="close"
          />

          <TokenRow
            v-if="tokenIsManager(token)"
            indent
            :hoverable="false"
          >
            <span class="md-body-small text-on-surface-variant">Full access to all repositories</span>
          </TokenRow>
          <TokenRow
            v-if="tokenIsManager(token) && groupRoutes(token).length"
            indent
            :hoverable="false"
          >
            <span class="md-body-small text-on-surface-variant">
              Inactive while the token has manager access. The routes below apply again once manager is turned off.
            </span>
          </TokenRow>

          <template
            v-for="route in groupRoutes(token)"
            :key="route.path"
          >
            <TokenRow
              indent
              reveal-actions-on-hover
              :highlighted="isOpen(`route:${tid(token)}:${route.path}`)"
            >
              <span
                class="whitespace-nowrap font-mono md-body-large"
                :class="tokenIsManager(token) ? 'text-on-surface-variant' : 'text-on-surface'"
              >{{ route.path }}</span>
              <span class="md-body-small truncate text-on-surface-variant">{{ routeLabel(route) }}</span>
              <template #actions>
                <MdIconButton
                  :aria-label="`Edit route ${route.path}`"
                  :title="`Edit route ${route.path}`"
                  @click="editRoute(token, route)"
                >
                  <PencilIcon />
                </MdIconButton>
                <MdIconButton
                  variant="error"
                  :aria-label="`Remove route ${route.path}`"
                  :title="`Remove route ${route.path}`"
                  @click="removeRoute(token, route.path)"
                >
                  <TrashIcon />
                </MdIconButton>
              </template>
            </TokenRow>
            <RouteEditor
              v-if="isOpen(`route:${tid(token)}:${route.path}`)"
              v-model:path="draft.path"
              v-model:read="draft.read"
              v-model:write="draft.write"
              submit-label="Save"
              :repositories="repositoryIds"
              :error="routeError"
              @submit="persistRoute(token)"
              @cancel="close"
              @edit-existing="openRoute(token, routeError.existing)"
            />
          </template>

          <TokenRow
            v-if="!isOpen(`newroute:${tid(token)}`)"
            indent
          >
            <MdButton
              variant="text"
              @click="addRoute(token)"
            >
              + Add route
            </MdButton>
          </TokenRow>
          <RouteEditor
            v-else
            v-model:path="draft.path"
            v-model:read="draft.read"
            v-model:write="draft.write"
            submit-label="Add"
            :repositories="repositoryIds"
            :error="routeError"
            @submit="persistRoute(token)"
            @cancel="close"
            @edit-existing="openRoute(token, routeError.existing)"
          />
        </template>

        <div
          v-if="!filtered.length"
          class="md-body-large px-4 py-12 text-center text-on-surface-variant"
        >
          {{ query ? `No tokens match “${query}”.` : 'No access tokens yet. Generate one to get started.' }}
        </div>
      </template>
    </div>
  </div>
</template>
