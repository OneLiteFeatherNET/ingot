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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createErrorToast, errorMessage } from '../../../helpers/toast'
import { textFromParam, textParam } from '../../../router/url-state'
import { useSession } from '../../../store/session'
import MdButton from '../../md/MdButton.vue'
import MdSelect from '../../md/MdSelect.vue'
import MdTextField from '../../md/MdTextField.vue'
import SegmentedControl from '../../tokens/SegmentedControl.vue'
import SegmentedOption from '../../tokens/SegmentedOption.vue'
import { detailOf, filterPrincipals } from '../accessPrincipals'
import { ARTIFACT_ACTIONS, parseNode } from '../nodes/nodeGrammar'
import { suggestNodes } from '../nodes/nodeList'
import { permissionNodesApi } from '../nodes/permissionNodesApi'
import ComboboxField from './ComboboxField.vue'
import { artifactNodeFromPath } from './artifactPath'
import { decisionSummary, kindLabel, problemMessages, traceSteps } from './checkTrace'

/*
 * The permission check: pick a principal and a node (and optionally one of the principal's tokens),
 * and see whether the node is allowed, which rule decided, and the steps that led there.
 *
 * The question can also name an artifact by its directory: the path (repository optional, action
 * read, deploy or delete) becomes the artifact node the server decides. The address always holds the
 * node that is checked (`principal`, `node`, `token`), so a check can be linked. A route that arrives
 * with a principal and a node runs the check at once. Submitting writes the question into the address
 * with router.replace, so a repeated check adds no history entry.
 */
const route = useRoute()
const router = useRouter()
const { client } = useSession()
const identity = computed(() => client.value.identity)

const SUGGESTIONS = 12

const principal = ref('')
const node = ref('')
const token = ref('')

// 'node' asks the typed node; 'path' asks the node an artifact directory names.
const mode = ref('node')
const artifactRepository = ref('')
const artifactPath = ref('')
const artifactAction = ref('read')
const ACTION_OPTIONS = ARTIFACT_ACTIONS.map(action => ({ value: action, label: action.charAt(0).toUpperCase() + action.slice(1) }))

const principals = ref([])
const catalog = ref([])
const tokenNames = ref([])

// idle: nothing asked yet. loading: a check is on its way. ready: `result` answers `checked`.
const status = ref('idle')
const result = ref(null)
const checked = ref(null)

const nodeServerError = ref('')
const principalError = ref('')
const tokenError = ref('')

let latest = 0
let tokensRequest = 0
let tokensFor = ''

const keyOf = (principalName, nodeText, tokenName) => JSON.stringify([principalName, nodeText, tokenName])
const queryKey = computed(() => keyOf(
  textFromParam(route.query.principal),
  textFromParam(route.query.node),
  textFromParam(route.query.token)
))
let handledKey = queryKey.value

const trimmedPrincipal = computed(() => principal.value.trim())
const trimmedNode = computed(() => node.value.trim())

const derived = computed(() => artifactNodeFromPath({
  repository: artifactRepository.value,
  path: artifactPath.value,
  action: artifactAction.value
}))
// The path field shows its problem once something is typed there.
const pathProblem = computed(() => (mode.value === 'path' && artifactPath.value.trim() !== '' ? derived.value.problem : ''))
// The node that is checked: the typed one, or the one the artifact path names.
const checkedNode = computed(() => (mode.value === 'path' ? derived.value.node : trimmedNode.value))

const nodeGrammarError = computed(() => {
  if (mode.value === 'path' || trimmedNode.value === '') return ''
  const parsed = parseNode(trimmedNode.value)
  return parsed.valid ? '' : parsed.problems[0].message
})
const nodeError = computed(() => nodeGrammarError.value || nodeServerError.value)

const canCheck = computed(() =>
  status.value !== 'loading' && trimmedPrincipal.value !== '' && checkedNode.value !== '' &&
  nodeGrammarError.value === '' && pathProblem.value === '')

const principalOptions = computed(() =>
  filterPrincipals(principals.value, principal.value)
    .slice(0, SUGGESTIONS)
    .map(entry => ({
      value: entry.name,
      description: [entry.kind === 'MACHINE' ? 'Machine account' : 'User', entry.displayName && entry.displayName !== entry.name ? entry.displayName : '']
        .filter(Boolean)
        .join(', ')
    })))

const nodeOptions = computed(() =>
  suggestNodes(catalog.value, node.value, [], SUGGESTIONS).map(item => ({ value: item.node, description: item.description })))

const tokenOptions = computed(() => {
  const names = tokenNames.value.includes(token.value) || token.value === '' ? tokenNames.value : [...tokenNames.value, token.value]
  return [{ value: '', label: 'No token' }, ...names.map(name => ({ value: name, label: name }))]
})

const steps = computed(() => traceSteps(result.value?.trace))
const summary = computed(() => decisionSummary(result.value?.decision))
const summaryKind = computed(() => kindLabel(summary.value.decidingKind))
const allowed = computed(() => result.value?.allowed === true)

const loadPrincipals = async () => {
  try {
    const response = await identity.value.principals.list()
    principals.value = response.data ?? []
  } catch {
    // The picker is a convenience: the name can still be typed.
    principals.value = []
  }
}

const loadCatalog = async () => {
  try {
    catalog.value = await permissionNodesApi(client.value).catalog()
  } catch {
    // The picker is a convenience: the node can still be typed.
    catalog.value = []
  }
}

const loadTokens = async (name) => {
  const request = ++tokensRequest
  if (name === '') {
    tokenNames.value = []
    return
  }
  try {
    const response = await identity.value.principals.get(name)
    if (request === tokensRequest) tokenNames.value = detailOf(response.data).tokens.map(entry => entry.name)
  } catch {
    if (request === tokensRequest) tokenNames.value = []
  }
}

// The token select follows the principal: another principal has other tokens, so the choice resets.
const followTokens = (name, keepToken) => {
  if (name === tokensFor) return
  tokensFor = name
  if (!keepToken) token.value = ''
  loadTokens(name)
}

const onPrincipal = (value) => {
  principal.value = value
  principalError.value = ''
  tokenError.value = ''
  const name = value.trim()
  followTokens(principals.value.some(entry => entry.name === name) ? name : '', false)
}

const onNode = (value) => {
  node.value = value
  nodeServerError.value = ''
}

const onToken = (value) => {
  token.value = value
  tokenError.value = ''
}

const clearErrors = () => {
  nodeServerError.value = ''
  principalError.value = ''
  tokenError.value = ''
}

const fail = (error) => {
  const code = error?.response?.status
  if (code === 400) {
    nodeServerError.value = problemMessages(error).join(' ') || errorMessage(error)
  } else if (code === 404) {
    const message = errorMessage(error)
    if (/token/i.test(message)) tokenError.value = message
    else principalError.value = message
  } else {
    createErrorToast(errorMessage(error))
  }
}

const run = async () => {
  const request = ++latest
  const target = { principal: trimmedPrincipal.value, node: checkedNode.value, token: token.value || undefined }
  clearErrors()
  result.value = null
  status.value = 'loading'
  try {
    const response = await identity.value.check(target)
    if (request !== latest) return
    result.value = response.data
    checked.value = target
    status.value = 'ready'
  } catch (error) {
    if (request !== latest) return
    status.value = 'idle'
    fail(error)
  }
}

const chooseMode = (next) => {
  mode.value = next
  clearErrors()
}

const submit = async () => {
  if (!canCheck.value) return
  handledKey = keyOf(trimmedPrincipal.value, checkedNode.value, token.value)
  router.replace({
    name: 'access-check',
    query: { principal: textParam(trimmedPrincipal.value), node: textParam(checkedNode.value), token: textParam(token.value) }
  })
  await run()
}

// Takes the question from the address. With a principal and a node it is asked at once.
const applyQuery = () => {
  handledKey = queryKey.value
  principal.value = textFromParam(route.query.principal)
  node.value = textFromParam(route.query.node)
  token.value = textFromParam(route.query.token)
  followTokens(trimmedPrincipal.value, true)
  clearErrors()
  if (trimmedPrincipal.value !== '' && trimmedNode.value !== '') {
    return run()
  }
  latest++
  result.value = null
  status.value = 'idle'
}

watch(queryKey, (key) => {
  if (route.name === 'access-check' && key !== handledKey) applyQuery()
})

onMounted(() => {
  loadPrincipals()
  loadCatalog()
  applyQuery()
})
</script>

<template>
  <section
    class="flex min-w-0 flex-col gap-6"
    aria-labelledby="check-title"
  >
    <div class="flex flex-col gap-1">
      <h3
        id="check-title"
        class="md-title-large text-on-surface"
      >
        Check permission
      </h3>
      <p class="md-body-medium text-on-surface-variant">
        See whether a user or machine account may use a node, and which rule decides it.
      </p>
    </div>

    <form
      class="grid grid-cols-1 gap-4 rounded-corner-large bg-surface-container-low px-4 py-4 min-[840px]:grid-cols-2"
      novalidate
      data-test="check-form"
      @submit.prevent="submit"
    >
      <ComboboxField
        :model-value="principal"
        label="Principal"
        :options="principalOptions"
        listbox-label="Principal suggestions"
        supporting-text="A user or a machine account"
        :error="principalError !== ''"
        :error-text="principalError"
        data-test="principal-field"
        @update:model-value="onPrincipal"
      />
      <div
        class="min-[840px]:col-span-2"
      >
        <SegmentedControl label="Check by">
          <SegmentedOption
            :on="mode === 'node'"
            data-test="mode-node"
            @click="chooseMode('node')"
          >
            Node
          </SegmentedOption>
          <SegmentedOption
            :on="mode === 'path'"
            data-test="mode-path"
            @click="chooseMode('path')"
          >
            Artifact path
          </SegmentedOption>
        </SegmentedControl>
      </div>
      <ComboboxField
        v-if="mode === 'node'"
        :model-value="node"
        label="Node"
        mono
        :options="nodeOptions"
        listbox-label="Node suggestions"
        supporting-text="Type a node or choose one from the list"
        :error="nodeError !== ''"
        :error-text="nodeError"
        data-test="node-field"
        @update:model-value="onNode"
      />
      <template v-else>
        <div data-test="artifact-repository">
          <MdTextField
            v-model="artifactRepository"
            label="Repository (optional)"
            variant="outlined"
            autocomplete="off"
            spellcheck="false"
            supporting-text="Leave empty to check every repository"
          />
        </div>
        <div data-test="artifact-path">
          <MdTextField
            v-model="artifactPath"
            label="Artifact path"
            variant="outlined"
            autocomplete="off"
            spellcheck="false"
            :error="pathProblem !== ''"
            :error-text="pathProblem"
            supporting-text="The artifact directory, for example net/onelitefeather/mycelium-bom"
          />
        </div>
        <div data-test="artifact-action">
          <MdSelect
            v-model="artifactAction"
            label="Action"
            :options="ACTION_OPTIONS"
            supporting-text="Read, deploy or delete the artifact"
          />
        </div>
        <p
          v-if="derived.node"
          class="min-[840px]:col-span-2 m-0 break-all font-mono md-body-medium text-on-surface-variant"
          data-test="artifact-node"
        >
          {{ derived.node }}
        </p>
      </template>
      <div data-test="token-field">
        <MdSelect
          :model-value="token"
          label="Token"
          :options="tokenOptions"
          supporting-text="Optional. A token can only narrow what its principal may do"
          :error="tokenError !== ''"
          :error-text="tokenError"
          :disabled="tokenNames.length === 0 && token === ''"
          data-test="token-select"
          @update:model-value="onToken"
        />
      </div>
      <div class="flex items-start min-[840px]:col-span-2">
        <MdButton
          type="submit"
          variant="filled"
          :disabled="!canCheck"
          data-test="check"
        >
          Check
        </MdButton>
      </div>
    </form>

    <p
      v-if="status === 'loading'"
      role="status"
      class="md-body-medium text-on-surface-variant"
      data-test="loading"
    >
      Checking...
    </p>

    <div
      v-else-if="result"
      class="flex flex-col gap-6"
      data-test="result"
    >
      <div
        role="status"
        class="flex items-center gap-4 rounded-corner-large px-6 py-5"
        :class="allowed ? 'bg-primary-container text-on-primary-container' : 'bg-error-container text-on-error-container'"
        :data-allowed="allowed ? 'true' : 'false'"
        data-test="verdict"
      >
        <svg
          v-if="allowed"
          class="size-12 shrink-0"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
        </svg>
        <svg
          v-else
          class="size-12 shrink-0"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM4 12c0-4.42 3.58-8 8-8 1.85 0 3.55.63 4.9 1.69L5.69 16.9A7.902 7.902 0 014 12zm8 8c-1.85 0-3.55-.63-4.9-1.69L18.31 7.1A7.902 7.902 0 0120 12c0 4.42-3.58 8-8 8z" />
        </svg>
        <div class="flex min-w-0 flex-col gap-1">
          <p
            class="md-display-small"
            data-test="verdict-text"
          >
            {{ allowed ? 'Allowed' : 'Denied' }}
          </p>
          <p class="md-body-large break-words">
            <code
              class="font-mono"
              data-test="verdict-node"
            >{{ checked.node }}</code>
            for
            <strong data-test="verdict-principal">{{ checked.principal }}</strong>
            <template v-if="checked.token">
              with token
              <strong data-test="verdict-token">{{ checked.token }}</strong>
            </template>
          </p>
        </div>
      </div>

      <dl
        class="m-0 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 rounded-corner-large bg-surface-container-low px-4 py-4 md-body-medium"
        data-test="summary"
      >
        <dt class="text-on-surface-variant">
          Source
        </dt>
        <dd
          class="m-0 text-on-surface"
          data-test="summary-source"
        >
          {{ summary.sourceLabel }}
        </dd>
        <dt class="text-on-surface-variant">
          Subject
        </dt>
        <dd
          class="m-0 break-words text-on-surface"
          data-test="summary-subject"
        >
          {{ summary.subject ?? 'None' }}
        </dd>
        <dt class="text-on-surface-variant">
          Node
        </dt>
        <dd
          class="m-0 break-all font-mono text-on-surface"
          data-test="summary-node"
        >
          {{ summary.node ?? 'None' }}
        </dd>
        <dt class="text-on-surface-variant">
          Depth
        </dt>
        <dd
          class="m-0 text-on-surface"
          data-test="summary-depth"
        >
          {{ summary.depth ?? 'None' }}
        </dd>
        <dt class="text-on-surface-variant">
          Deciding node
        </dt>
        <dd
          class="m-0 break-all font-mono text-on-surface"
          data-test="summary-deciding-node"
        >
          {{ summary.decidingNode ?? 'None' }}
        </dd>
        <dt class="text-on-surface-variant">
          Kind
        </dt>
        <dd
          class="m-0 text-on-surface"
          data-test="summary-deciding-kind"
        >
          {{ summaryKind }}
        </dd>
      </dl>
      <p
        v-if="summary.explanation"
        class="m-0 md-body-large text-on-surface"
        data-test="explanation"
      >
        {{ summary.explanation }}
      </p>

      <div class="flex flex-col gap-3">
        <h4 class="md-title-medium text-on-surface">
          How it was derived
        </h4>
        <ol
          class="m-0 flex list-none flex-col p-0"
          aria-label="Derivation trace"
          data-test="trace"
        >
          <li
            v-for="(step, index) in steps"
            :key="step.position"
            class="relative flex gap-4 pb-5 last:pb-0"
            :data-matched="step.matched ? 'true' : 'false'"
            data-test="trace-step"
          >
            <span
              v-if="index < steps.length - 1"
              class="absolute bottom-0 left-4 top-8 w-px bg-outline-variant"
              aria-hidden="true"
            />
            <span
              class="relative z-10 flex size-8 shrink-0 items-center justify-center rounded-corner-full md-label-large"
              :class="step.muted ? 'border border-outline-variant bg-surface text-on-surface-variant' : 'bg-primary text-on-primary'"
              data-test="step-marker"
            >{{ step.position }}</span>
            <div
              class="flex min-w-0 flex-1 flex-col gap-1"
              :class="step.muted ? 'text-on-surface-variant' : 'text-on-surface'"
            >
              <p class="m-0 flex flex-wrap items-baseline gap-x-2 md-title-small">
                <span data-test="step-source">{{ step.sourceLabel }}</span>
                <span
                  v-if="step.subject"
                  class="md-body-medium"
                  data-test="step-subject"
                >{{ step.subject }}</span>
                <span
                  v-if="!step.muted"
                  class="md-label-medium text-primary"
                  data-test="step-decisive"
                >Decisive</span>
              </p>
              <p class="m-0 flex flex-wrap items-baseline gap-x-3 md-body-medium">
                <code
                  v-if="step.node"
                  class="break-all font-mono"
                  data-test="step-node"
                >{{ step.node }}</code>
                <span
                  v-else
                  data-test="step-node"
                >No node</span>
                <span
                  class="md-label-large"
                  :class="step.muted ? '' : step.allowed ? 'text-success' : 'text-error'"
                  data-test="step-effect"
                >{{ step.allowed ? 'Allows' : 'Denies' }}</span>
              </p>
              <p
                class="m-0 md-body-medium"
                data-test="step-reason"
              >
                {{ step.reason }}
              </p>
            </div>
          </li>
        </ol>
      </div>
    </div>

    <div
      v-else
      class="flex flex-col gap-1 rounded-corner-large bg-surface-container-low px-4 py-6"
      data-test="empty"
    >
      <p class="md-title-medium text-on-surface">
        Pick a principal and a node
      </p>
      <p class="md-body-medium text-on-surface-variant">
        The answer and the steps behind it appear here.
      </p>
    </div>
  </section>
</template>
