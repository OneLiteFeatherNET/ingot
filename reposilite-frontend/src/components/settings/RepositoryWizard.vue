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
import { computed, inject, nextTick, reactive, ref, shallowRef, unref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { VueFinalModal } from 'vue-final-modal'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { tokenStoreKey, useTokens } from '../../store/tokens'
import usePlaceholders from '../../store/placeholders'
import MdButton from '../md/MdButton.vue'
import MdCheckbox from '../md/MdCheckbox.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import CopiedIcon from '../icons/CopiedIcon.vue'
import CopyIcon from '../icons/CopyIcon.vue'
import { copyToClipboard } from '../card/copySnippet'
import { idText } from './collections'
import { SCRIM_STYLE } from './dialog'
import {
  STEPS,
  VISIBILITY_OPTIONS,
  buildActions,
  createProgress,
  emptyDraft,
  failureNote,
  firstInvalidStep,
  optionalFieldsOf,
  repositoryUrl,
  runActions,
  snippetsFor,
  summaryRows,
  tokenNameOf,
  validateStep
} from './repositoryWizard'

/*
 * The guided way to a new Maven repository: four steps, then one review that performs the
 * changes. Only the decisions live in repositoryWizard.js; this file draws them and calls the
 * stores. Full screen below 600px, a dialog of at most 560px above it.
 *
 * Nothing is written before Create. The create sequence runs one action at a time, and a failed
 * action keeps the ones before it: a saved repository is never saved again, and a created token is
 * never created again. The one-time token secret is shown on the success step, and the route
 * opens the repository only when the user presses Done, so the secret is not lost to a redirect.
 */

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const configuration = inject(configurationStoreKey, null) ?? useConfiguration().store
const tokenStore = inject(tokenStoreKey, null) ?? useTokens()
const router = useRouter()
const { productionUrl } = usePlaceholders()

const STORAGE_OPTIONS = [
  {
    value: 'fs',
    label: 'Filesystem',
    who: "Files are kept on the server's disk. The quota caps how much the repository may hold."
  },
  {
    value: 's3',
    label: 'S3 bucket',
    who: 'Files are kept in an S3 bucket, or in a service that speaks the S3 API.'
  }
]

const ACCESS_OPTIONS = [
  {
    value: 'create',
    label: 'Create a deploy token for this repository',
    who: 'A new token that can read and write it. Its secret is shown once, after the repository is created.'
  },
  {
    value: 'grant',
    label: 'Grant an existing token',
    who: 'Adds access to this repository to a token you already have.'
  },
  {
    value: 'none',
    label: 'Skip for now',
    who: 'Create the repository without a token. Add access later from the tokens page.'
  }
]

const draft = reactive(emptyDraft())
const stepIndex = ref(0)
// Errors are shown only after the user has tried to leave a step, so a fresh step does not start red.
const attempted = ref(false)
// Set when Create runs for the first time. Its actions keep their state across retries.
const plan = shallowRef(null)
const progress = reactive({})
const running = ref(false)
const succeeded = ref(false)
const copied = ref('')
const heading = ref(null)

const stepKey = computed(() => STEPS[stepIndex.value].key)
const itemSchema = computed(() => configuration.schemas.value.maven?.properties?.repositories?.items)
const optional = computed(() => optionalFieldsOf(itemSchema.value))
const existingIds = computed(() => (configuration.configurations.value.maven?.repositories ?? []).map(idText))
const tokens = computed(() => tokenStore.tokens.value)
const context = computed(() => ({ existingIds: existingIds.value, tokens: tokens.value }))
const stepErrors = computed(() => validateStep(stepKey.value, draft, context.value))
const shownErrors = computed(() => (attempted.value ? stepErrors.value : {}))
const summary = computed(() => summaryRows(draft, { optional: optional.value }))
const tokenOptions = computed(() => [
  { value: '', label: 'Choose a token' },
  ...tokens.value.map(token => ({ value: token.name, label: token.name }))
])
// The labels of the actions the review shows before anything runs. The ports are never called for it.
const planLabels = computed(() => buildActions(draft, { optional: optional.value }, {}).actions.map(action => action.label))
const unsavedNote = computed(() => {
  const titles = unref(configuration.dirtyDomains).map(domain => configuration.titleOf(domain))
  return titles.length > 0 ? `There are unsaved changes in ${titles.join(', ')}. Creating the repository saves them too.` : ''
})
const dismissable = computed(() => !running.value && !succeeded.value)

// The only place the stores are touched. Each one throws on failure, which the runner reports per step.
const ports = {
  addEntry: (entry) => configuration.addEntry('maven', 'repositories', entry),
  saveSettings: async () => {
    const invalid = unref(configuration.invalidDomains)
    if (invalid.length > 0) {
      throw new Error(`Fix the invalid settings first: ${invalid.map(domain => configuration.titleOf(domain)).join(', ')}.`)
    }
    if (!(await configuration.saveConfiguration())) {
      throw new Error(unref(configuration.saveError) || 'The settings were not saved.')
    }
  },
  createToken: async (name) => {
    const created = await tokenStore.createToken(name, { type: 'PERSISTENT' })
    // The token store shows every new secret on the tokens page too. This wizard shows it, so it is dropped there.
    tokenStore.dismissSecret()
    return created.secret
  },
  grantRoute: async (name, route) => {
    const findToken = () => tokenStore.tokens.value.find(token => token.name === name)
    let token = findToken()
    if (!token) {
      await tokenStore.fetchTokens()
      token = findToken()
    }
    if (!token) throw new Error(`The token ${name} was not found.`)
    if (!(await tokenStore.saveRoute(token, route))) throw new Error(`The route ${route.path} was not saved.`)
  }
}

const reset = () => {
  Object.assign(draft, emptyDraft())
  stepIndex.value = 0
  attempted.value = false
  plan.value = null
  Object.keys(progress).forEach(key => delete progress[key])
  running.value = false
  succeeded.value = false
  copied.value = ''
}

watch(() => props.modelValue, (open) => {
  if (open) reset()
})

watch(stepKey, (key) => {
  if (key === 'access' && !tokenStore.loaded.value) tokenStore.fetchTokens()
})

const focusHeading = () => nextTick(() => heading.value?.focus())

const next = () => {
  attempted.value = true
  if (Object.keys(stepErrors.value).length > 0) return
  attempted.value = false
  stepIndex.value += 1
  focusHeading()
}

const back = () => {
  attempted.value = false
  stepIndex.value -= 1
  focusHeading()
}

const onSubmit = () => (stepKey.value === 'review' ? create() : next())

const create = async () => {
  if (running.value || succeeded.value) return
  // Checked once, before the first run. Afterwards the repository is in the draft list itself, so checking the
  // id again would refuse the retry as a duplicate of the repository this wizard just saved.
  if (!plan.value) {
    const invalid = firstInvalidStep(draft, context.value)
    if (invalid) {
      stepIndex.value = STEPS.findIndex(step => step.key === invalid)
      attempted.value = true
      focusHeading()
      return
    }
    plan.value = { ...buildActions(draft, { optional: optional.value }, ports), mode: draft.access.mode }
    Object.assign(progress, createProgress(plan.value.actions))
  }
  running.value = true
  const ok = await runActions(plan.value.actions, progress)
  running.value = false
  succeeded.value = ok
  if (ok) focusHeading()
}

const stateText = (key) => ({
  pending: 'Waiting',
  running: 'Working',
  done: 'Done',
  failed: 'Failed'
})[progress[key]?.state] ?? ''

const failureFor = (key) => failureNote(key, plan.value?.mode)

const snippets = computed(() => {
  if (!succeeded.value || !plan.value) return null
  const id = draft.basics.id
  return snippetsFor({
    id,
    url: repositoryUrl({ base: productionUrl, id }),
    tokenName: plan.value.result.tokenName
  })
})

const SNIPPET_TITLES = [
  { key: 'maven-settings', label: 'Maven settings.xml, server entry', text: (snippet) => snippet.maven.settings },
  { key: 'maven-distribution', label: 'Maven pom.xml, distributionManagement', text: (snippet) => snippet.maven.distributionManagement },
  { key: 'gradle', label: 'Gradle (Kotlin DSL), publishing', text: (snippet) => snippet.gradle },
  { key: 'environment', label: 'Environment variables', text: (snippet) => snippet.environment }
]

const copyText = async (key, text) => {
  if (await copyToClipboard(text, globalThis.navigator?.clipboard)) copied.value = key
}

/*
 * A repository whose save failed is still in the unsaved settings. Cancelling takes it back out, so
 * the settings bar does not offer to save a repository the user has given up on. A saved one stays.
 */
const discardUnsavedEntry = () => {
  if (!plan.value || progress.settings?.state === 'done') return
  const entries = configuration.configurations.value.maven?.repositories ?? []
  const index = entries.findIndex(entry => entry.id === draft.basics.id)
  if (index >= 0) configuration.removeEntry('maven', 'repositories', index)
}

const cancel = () => {
  if (running.value) return
  discardUnsavedEntry()
  emit('update:modelValue', false)
}

// Done is the only way out after success: the repository page is where the user goes next.
const done = () => {
  const id = draft.basics.id
  emit('update:modelValue', false)
  router.push({ name: 'settings-entry', params: { domain: 'maven', collection: 'repositories', entry: id } })
}

const onModalUpdate = (open) => {
  if (!open) cancel()
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    :click-to-close="dismissable"
    :esc-to-close="dismissable"
    class="flex min-h-full items-center justify-center max-[599px]:p-0 min-[600px]:p-6"
    @update:model-value="onModalUpdate"
  >
    <form
      id="repository-wizard"
      class="flex w-full min-w-[280px] flex-col overflow-hidden bg-surface-container-high text-on-surface max-[599px]:h-dvh max-[599px]:rounded-none min-[600px]:h-auto min-[600px]:max-h-[min(44rem,90dvh)] min-[600px]:max-w-[560px] min-[600px]:rounded-corner-extra-large min-[600px]:shadow-elevation-3"
      role="dialog"
      aria-modal="true"
      aria-labelledby="wizard-title"
      novalidate
      @submit.prevent="onSubmit"
    >
      <header class="flex flex-col gap-4 px-6 pt-6 max-[599px]:px-4 max-[599px]:pt-4">
        <h2
          id="wizard-title"
          class="md-headline-small text-on-surface"
        >
          New repository
        </h2>
        <ol
          aria-label="Steps"
          class="grid grid-cols-4 gap-2"
        >
          <li
            v-for="(step, index) in STEPS"
            :key="step.key"
            class="flex min-w-0 flex-col items-center gap-1 text-center"
            :aria-current="index === stepIndex ? 'step' : undefined"
          >
            <span
              class="flex size-8 items-center justify-center rounded-corner-full md-label-large"
              :class="index === stepIndex ? 'bg-primary text-on-primary' : index < stepIndex ? 'bg-secondary-container text-on-secondary-container' : 'bg-surface-container-highest text-on-surface-variant'"
            >{{ index + 1 }}</span>
            <span
              class="max-w-full truncate md-label-medium"
              :class="index === stepIndex ? 'text-on-surface' : 'text-on-surface-variant'"
            >{{ step.label }}</span>
          </li>
        </ol>
        <h3
          id="wizard-step-title"
          ref="heading"
          tabindex="-1"
          class="md-title-large text-on-surface focus:outline-none"
        >
          {{ STEPS[stepIndex].label }}
        </h3>
      </header>

      <div class="min-h-0 flex-1 overflow-y-auto px-6 py-4 max-[599px]:px-4">
        <!-- Basics -->
        <div
          v-if="stepKey === 'basics'"
          class="flex flex-col gap-6"
        >
          <MdTextField
            id="wizard-repository-id"
            v-model="draft.basics.id"
            label="Id"
            variant="outlined"
            autocomplete="off"
            required
            supporting-text="Letters, digits, dots, underscores and hyphens. The id is the address of the repository."
            :error="Boolean(shownErrors.id)"
            :error-text="shownErrors.id ?? ''"
          />
          <fieldset class="flex flex-col gap-2">
            <legend class="pb-2 md-title-small text-on-surface">
              Who can read it
            </legend>
            <label
              v-for="option in VISIBILITY_OPTIONS"
              :key="option.value"
              class="md-state-layer relative flex cursor-pointer items-start gap-3 rounded-corner-medium border p-3"
              :class="draft.basics.visibility === option.value ? 'border-primary bg-secondary-container text-on-secondary-container' : 'border-outline-variant text-on-surface'"
            >
              <input
                v-model="draft.basics.visibility"
                type="radio"
                name="visibility"
                :value="option.value"
                class="mt-1 size-4 shrink-0 accent-primary"
              >
              <span class="flex min-w-0 flex-col gap-0.5">
                <span class="md-title-small">{{ option.label }}</span>
                <span class="md-body-medium">{{ option.who }}</span>
              </span>
            </label>
            <p
              v-if="shownErrors.visibility"
              class="md-body-small text-error"
            >
              {{ shownErrors.visibility }}
            </p>
          </fieldset>
          <MdTextField
            v-if="optional.description"
            id="wizard-description"
            v-model="draft.basics.description"
            label="Description"
            variant="outlined"
            supporting-text="Plain text. Shown with the repository."
          />
          <MdTextField
            v-if="optional.tags"
            id="wizard-tags"
            v-model="draft.basics.tags"
            label="Tags"
            variant="outlined"
            supporting-text="Separate tags with commas."
          />
        </div>

        <!-- Storage -->
        <div
          v-else-if="stepKey === 'storage'"
          class="flex flex-col gap-6"
        >
          <fieldset class="flex flex-col gap-2">
            <legend class="pb-2 md-title-small text-on-surface">
              Where the files are kept
            </legend>
            <label
              v-for="option in STORAGE_OPTIONS"
              :key="option.value"
              class="md-state-layer relative flex cursor-pointer items-start gap-3 rounded-corner-medium border p-3"
              :class="draft.storage.kind === option.value ? 'border-primary bg-secondary-container text-on-secondary-container' : 'border-outline-variant text-on-surface'"
            >
              <input
                v-model="draft.storage.kind"
                type="radio"
                name="storage-kind"
                :value="option.value"
                class="mt-1 size-4 shrink-0 accent-primary"
              >
              <span class="flex min-w-0 flex-col gap-0.5">
                <span class="md-title-small">{{ option.label }}</span>
                <span class="md-body-medium">{{ option.who }}</span>
              </span>
            </label>
          </fieldset>

          <MdTextField
            v-if="draft.storage.kind === 'fs'"
            id="wizard-quota"
            v-model="draft.storage.quota"
            label="Quota"
            variant="outlined"
            supporting-text="For example 500MB, 10GB or 90%. The default is 100%."
            :error="Boolean(shownErrors.quota)"
            :error-text="shownErrors.quota ?? ''"
          />
          <template v-else>
            <MdTextField
              id="wizard-bucket"
              v-model="draft.storage.bucketName"
              label="Bucket"
              variant="outlined"
              autocomplete="off"
              required
              supporting-text="The bucket must exist already."
              :error="Boolean(shownErrors.bucketName)"
              :error-text="shownErrors.bucketName ?? ''"
            />
            <MdTextField
              id="wizard-region"
              v-model="draft.storage.region"
              label="Region"
              variant="outlined"
              autocomplete="off"
              supporting-text="Optional. Empty uses the server's region."
            />
            <MdTextField
              id="wizard-endpoint"
              v-model="draft.storage.endpoint"
              label="Endpoint"
              variant="outlined"
              autocomplete="off"
              supporting-text="Optional. Only for services that are not Amazon S3."
            />
            <MdTextField
              id="wizard-access-key"
              v-model="draft.storage.accessKey"
              type="password"
              label="Access key"
              variant="outlined"
              autocomplete="new-password"
              supporting-text="Optional. Masked here and in the settings."
            />
            <MdTextField
              id="wizard-secret-key"
              v-model="draft.storage.secretKey"
              type="password"
              label="Secret key"
              variant="outlined"
              autocomplete="new-password"
              supporting-text="Optional. Masked here and in the settings."
            />
          </template>
        </div>

        <!-- Access -->
        <div
          v-else-if="stepKey === 'access'"
          class="flex flex-col gap-6"
        >
          <fieldset class="flex flex-col gap-2">
            <legend class="pb-2 md-title-small text-on-surface">
              Access for this repository
            </legend>
            <label
              v-for="option in ACCESS_OPTIONS"
              :key="option.value"
              class="md-state-layer relative flex cursor-pointer items-start gap-3 rounded-corner-medium border p-3"
              :class="draft.access.mode === option.value ? 'border-primary bg-secondary-container text-on-secondary-container' : 'border-outline-variant text-on-surface'"
            >
              <input
                v-model="draft.access.mode"
                type="radio"
                name="access-mode"
                :value="option.value"
                class="mt-1 size-4 shrink-0 accent-primary"
              >
              <span class="flex min-w-0 flex-col gap-0.5">
                <span class="md-title-small">{{ option.label }}</span>
                <span class="md-body-medium">{{ option.who }}</span>
              </span>
            </label>
          </fieldset>

          <MdTextField
            v-if="draft.access.mode === 'create'"
            id="wizard-token-name"
            :model-value="tokenNameOf(draft)"
            label="Token name"
            variant="outlined"
            autocomplete="off"
            supporting-text="Suggested from the repository id. Change it if you like."
            :error="Boolean(shownErrors.tokenName)"
            :error-text="shownErrors.tokenName ?? ''"
            @update:model-value="draft.access.tokenName = $event"
          />
          <MdSelect
            v-if="draft.access.mode === 'grant'"
            id="wizard-existing-token"
            :model-value="draft.access.existingToken"
            label="Token"
            :options="tokenOptions"
            :supporting-text="tokens.length > 0 ? 'The route is added to this token. Its other routes stay as they are.' : 'There are no tokens yet. Create one from the tokens page first, or pick another option.'"
            :error="Boolean(shownErrors.existingToken)"
            :error-text="shownErrors.existingToken ?? ''"
            @update:model-value="draft.access.existingToken = $event"
          />
          <fieldset
            v-if="draft.access.mode !== 'none'"
            class="flex flex-col gap-2"
          >
            <legend class="pb-2 md-title-small text-on-surface">
              Permissions on /{{ draft.basics.id }}
            </legend>
            <div class="flex flex-wrap gap-6">
              <MdCheckbox
                id="wizard-read"
                v-model="draft.access.read"
              >
                Read
              </MdCheckbox>
              <MdCheckbox
                id="wizard-write"
                v-model="draft.access.write"
              >
                Write
              </MdCheckbox>
            </div>
            <p
              v-if="shownErrors.permissions"
              class="md-body-small text-error"
            >
              {{ shownErrors.permissions }}
            </p>
          </fieldset>
        </div>

        <!-- Review, and the create sequence -->
        <div
          v-else
          class="flex flex-col gap-6"
        >
          <template v-if="!succeeded">
            <dl class="flex flex-col gap-3 rounded-corner-medium bg-surface-container p-4">
              <div
                v-for="row in summary"
                :key="row.label"
                class="flex flex-col gap-0.5"
              >
                <dt class="md-label-large text-on-surface-variant">
                  {{ row.label }}
                </dt>
                <dd class="m-0 break-words md-body-large text-on-surface">
                  {{ row.value }}
                </dd>
              </div>
            </dl>

            <p
              v-if="unsavedNote && !plan"
              class="md-body-medium text-warning"
              role="status"
            >
              {{ unsavedNote }}
            </p>

            <section
              v-if="!plan"
              aria-labelledby="wizard-plan-title"
              class="flex flex-col gap-2"
            >
              <h3
                id="wizard-plan-title"
                class="md-title-small text-on-surface"
              >
                What Create does
              </h3>
              <ol class="flex list-decimal flex-col gap-1 pl-5 md-body-medium text-on-surface-variant">
                <li
                  v-for="label in planLabels"
                  :key="label"
                >
                  {{ label }}
                </li>
              </ol>
            </section>

            <ol
              v-else
              aria-label="Progress"
              class="flex flex-col gap-3"
            >
              <li
                v-for="action in plan.actions"
                :key="action.key"
                class="flex flex-col gap-1 rounded-corner-medium bg-surface-container p-4"
                :data-step="action.key"
                :data-state="progress[action.key].state"
              >
                <span class="md-title-small text-on-surface">{{ action.label }}</span>
                <span class="md-body-medium text-on-surface-variant">{{ stateText(action.key) }}</span>
                <template v-if="progress[action.key].state === 'failed'">
                  <p
                    role="alert"
                    class="md-body-medium text-error"
                  >
                    {{ failureFor(action.key) }}
                  </p>
                  <p class="md-body-small text-on-surface-variant break-words">
                    {{ progress[action.key].error }}
                  </p>
                  <div>
                    <MdButton
                      data-test="retry"
                      variant="tonal"
                      type="button"
                      :disabled="running"
                      @click="create"
                    >
                      Retry this step
                    </MdButton>
                  </div>
                </template>
              </li>
            </ol>
          </template>

          <!-- Success: the one-time secret, and the snippets that use the repository -->
          <div
            v-else
            class="flex flex-col gap-6"
            role="status"
          >
            <p class="md-title-medium text-on-surface">
              Repository {{ draft.basics.id }} is ready.
            </p>

            <section
              v-if="plan.result.secret"
              aria-labelledby="wizard-secret-title"
              class="flex flex-col gap-3 rounded-corner-medium bg-primary-container p-4 text-on-primary-container"
            >
              <h3
                id="wizard-secret-title"
                class="md-title-small"
              >
                Secret for {{ plan.result.tokenName }}
              </h3>
              <div class="flex flex-wrap items-center gap-2">
                <code
                  data-test="secret"
                  class="min-w-0 flex-1 break-all rounded-corner-small bg-surface px-3 py-2 font-mono md-body-large text-on-surface"
                >{{ plan.result.secret }}</code>
                <MdIconButton
                  :aria-label="`Copy the secret of token ${plan.result.tokenName}`"
                  :title="`Copy the secret of token ${plan.result.tokenName}`"
                  @click="copyText('secret', plan.result.secret)"
                >
                  <CopiedIcon v-if="copied === 'secret'" />
                  <CopyIcon v-else />
                </MdIconButton>
              </div>
              <p class="md-body-medium">
                You won't see this secret again. Copy it now and keep it somewhere safe.
              </p>
            </section>
            <p
              v-else-if="plan.mode === 'grant'"
              class="md-body-medium text-on-surface-variant"
            >
              The route was added to {{ plan.result.tokenName }}. Its secret did not change.
            </p>
            <p
              v-else-if="plan.mode === 'none'"
              class="md-body-medium text-on-surface-variant"
            >
              No token was created. The snippets below need a token before they can publish.
            </p>

            <section
              aria-labelledby="wizard-snippets-title"
              class="flex flex-col gap-4"
            >
              <h3
                id="wizard-snippets-title"
                class="md-title-small text-on-surface"
              >
                Use the repository
              </h3>
              <p class="md-body-medium text-on-surface-variant">
                The credentials are read from environment variables, so no secret is written into a file.
              </p>
              <div
                v-for="snippet in SNIPPET_TITLES"
                :key="snippet.key"
                class="flex flex-col gap-1"
              >
                <div class="flex items-center justify-between gap-2">
                  <span class="md-label-large text-on-surface">{{ snippet.label }}</span>
                  <MdIconButton
                    :aria-label="`Copy the ${snippet.label} snippet`"
                    :title="`Copy the ${snippet.label} snippet`"
                    @click="copyText(snippet.key, snippet.text(snippets))"
                  >
                    <CopiedIcon v-if="copied === snippet.key" />
                    <CopyIcon v-else />
                  </MdIconButton>
                </div>
                <pre
                  :data-snippet="snippet.key"
                  class="whitespace-pre-wrap [overflow-wrap:anywhere] rounded-corner-small bg-surface-container-lowest p-3 font-mono md-body-small text-on-surface"
                >{{ snippet.text(snippets) }}</pre>
              </div>
            </section>
          </div>
        </div>
      </div>

      <footer class="flex flex-wrap items-center justify-end gap-2 px-6 pb-6 pt-2 max-[599px]:px-4 max-[599px]:pb-4">
        <MdButton
          v-if="!succeeded"
          id="wizard-cancel"
          variant="text"
          type="button"
          :disabled="running"
          @click="cancel"
        >
          Cancel
        </MdButton>
        <MdButton
          v-if="!succeeded && stepIndex > 0 && !plan"
          id="wizard-back"
          variant="text"
          type="button"
          @click="back"
        >
          Back
        </MdButton>
        <MdButton
          v-if="!succeeded && stepKey !== 'review'"
          id="wizard-next"
          variant="filled"
          type="submit"
        >
          Next
        </MdButton>
        <MdButton
          v-if="stepKey === 'review' && !plan"
          id="wizard-create"
          variant="filled"
          type="submit"
        >
          Create
        </MdButton>
        <MdButton
          v-if="succeeded"
          id="wizard-done"
          variant="filled"
          type="button"
          @click="done"
        >
          Done
        </MdButton>
      </footer>
    </form>
  </VueFinalModal>
</template>
