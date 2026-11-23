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
import { computed, inject, nextTick, reactive, ref, shallowRef, unref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { useSession } from '../../store/session'
import { errorMessage } from '../../helpers/toast'
import usePlaceholders from '../../store/placeholders'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdCheckbox from '../md/MdCheckbox.vue'
import MdDialog from '../md/MdDialog.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import CopiedIcon from '../icons/CopiedIcon.vue'
import CopyIcon from '../icons/CopyIcon.vue'
import { copyToClipboard } from '../card/copySnippet'
import { idText } from './collections'
import { ROLES, roleExplanation } from '../access/accessTeams'
import { createAccessPorts } from './repositoryAccessPorts'
import RepositoryMembersPicker from './RepositoryMembersPicker.vue'
import WizardChoiceCard from './wizard/WizardChoiceCard.vue'
import WizardFieldGroup from './wizard/WizardFieldGroup.vue'
import WizardProgressList from './wizard/WizardProgressList.vue'
import WizardRemoteFields from './wizard/WizardRemoteFields.vue'
import WizardReviewSummary from './wizard/WizardReviewSummary.vue'
import WizardStepIndicator from './wizard/WizardStepIndicator.vue'
import {
  ACCESS_MODES,
  MACHINE_SCOPES,
  STORAGE_POLICIES,
  TYPE_CHOICES,
  VISIBILITY_OPTIONS,
  buildActions,
  chooseType,
  createProgress,
  createdSoFar,
  emptyDraft,
  emptyRemote,
  firstInvalidStep,
  machineNameOf,
  newTeamNameOf,
  optionalFieldsOf,
  pluginFieldsOf,
  repositoryUrl,
  rolesFor,
  runActions,
  snippetsFor,
  stepsFor,
  storageVariantsOf,
  summaryRows,
  typeLabelOf,
  validateStep
} from './repositoryWizard'
import type { ProgressEntry, Remote, WizardPorts } from './repositoryWizard'
import type { MirrorEntry } from './groupMembers'

/*
 * The guided way to a new Maven repository: a type, the basics, the part that depends on the type
 * (storage, remote repositories or members), access, then one review that performs the changes.
 * Only the decisions live in repositoryWizard.js; this file draws them and calls the stores. Full
 * screen below 600px, a dialog of at most 560px above it.
 *
 * Nothing is written before Create. The create sequence runs one action at a time, and a failed
 * action keeps the ones before it: a saved repository is never saved again, and a created team or
 * machine account is never created again. Nothing is rolled back; the failed step shows what exists.
 * Access is given through a team (see repositoryAccessPorts.js). The one-time secret of the
 * machine account's token is shown on the success step, and the route opens the repository only
 * when the user presses Done, so the secret is not lost to a redirect.
 */

const props = withDefaults(defineProps<{
  modelValue?: boolean
}>(), {
  modelValue: false
})

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
}>()

const configuration = inject(configurationStoreKey, null) ?? useConfiguration().store
const { client } = useSession()
const router = useRouter()
const { productionUrl } = usePlaceholders()

const ROLE_LABELS = ROLES.map(role => ({ value: role.value, label: role.label }))
const SCOPE_OPTIONS = MACHINE_SCOPES.map(scope => ({ value: scope.value, label: scope.label }))

const draft = reactive(emptyDraft())
const stepIndex = ref(0)
// Errors are shown only after the user has tried to leave a step, so a fresh step does not start red.
const attempted = ref(false)
// Set when Create runs for the first time. Its actions keep their state across retries.
const plan = shallowRef<(ReturnType<typeof buildActions> & { mode: string }) | null>(null)
const progress = reactive<Record<string, ProgressEntry>>({})
const running = ref(false)
const succeeded = ref(false)
const copied = ref('')
const heading = ref<HTMLElement | null>(null)

// The steps follow the chosen type: a hosted repository has storage, a proxy remotes, a group members.
const steps = computed(() => stepsFor(draft.type))
const stepKey = computed(() => steps.value[stepIndex.value].key)
// A proxy or a group takes no uploads, so its roles and token scopes are read or admin, and read only.
const roleOptions = computed(() => ROLE_LABELS.filter(role => rolesFor(draft.type).some(entry => entry.value === role.value)))
const scopeOptions = computed(() => (draft.type === 'HOSTED' ? SCOPE_OPTIONS : SCOPE_OPTIONS.filter(scope => scope.value === 'READ')))
const addressOf = computed(() => repositoryUrl({ base: productionUrl, id: draft.basics.id }))
const itemSchema = computed(() => configuration.schemas.value.maven?.properties?.repositories?.items)
const optional = computed(() => optionalFieldsOf(itemSchema.value))
// The storage kinds the served schema offers: the built-in ones, then the plugin variants. The fields of the chosen plugin variant follow.
const storageVariants = computed(() => storageVariantsOf(itemSchema.value))
const storageFields = computed(() => pluginFieldsOf(itemSchema.value, draft.storage.kind))
const entryOptions = computed(() => ({ optional: optional.value, storageVariants: storageVariants.value }))
// Boundary cast: the repositories are loose JSON in the configuration; idText reads the id of any entry.
const existingIds = computed(() => ((configuration.configurations.value.maven?.repositories ?? []) as unknown[]).map(idText))
// The teams and the principals the access step offers and checks names against. Loaded when the step opens.
const teams = ref<{ name: string }[]>([])
const principals = ref<{ name: string }[]>([])
const accessLoadError = ref('')
const context = computed(() => ({ existingIds: existingIds.value, teams: teams.value, principals: principals.value, storageVariants: storageVariants.value }))
const stepErrors = computed(() => validateStep(stepKey.value, draft, context.value))
const shownErrors = computed(() => (attempted.value ? stepErrors.value : {}))
// The messages of the refused step, listed in the error summary that takes the focus when a step is refused.
const errorSummary = ref<HTMLElement | null>(null)
const errorList = computed(() => Object.values(shownErrors.value).filter(message => typeof message === 'string' && message !== ''))
const summary = computed(() => summaryRows(draft, entryOptions.value))
const addRemote = () => draft.upstream.remotes.push(emptyRemote())
const removeRemote = (index: number) => draft.upstream.remotes.splice(index, 1)
const editRemote = (index: number, field: keyof Remote, value: string) => { draft.upstream.remotes[index][field] = value }
// The members picker hands back the entries it was given, so each reference is the id of a repository.
const memberIds = (items: MirrorEntry[]) => items.map(item => item.reference as string)
const teamOptions = computed(() => [
  { value: '', label: 'Choose a team' },
  ...teams.value.map(team => ({ value: team.name, label: team.name }))
])
// The labels of the actions the review shows before anything runs. The ports are never called for it.
const planLabels = computed(() => buildActions(draft, entryOptions.value, {} as WizardPorts).actions.map(action => action.label))
const unsavedNote = computed(() => {
  const titles = unref(configuration.dirtyDomains).map(domain => configuration.titleOf(domain))
  return titles.length > 0 ? `There are unsaved changes in ${titles.join(', ')}. Creating the repository saves them too.` : ''
})
const dismissable = computed(() => !running.value && !succeeded.value)

// The only place the stores and the identity client are touched. Each one throws on failure, which the runner reports per step.
const ports: WizardPorts = {
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
  createTeam: (name) => createAccessPorts(client.value.identity).createTeam(name),
  grantTeam: (team, grant) => createAccessPorts(client.value.identity).grantTeam(team, grant),
  createMachine: (name) => createAccessPorts(client.value.identity).createMachine(name),
  addMachineToTeam: (team, machine) => createAccessPorts(client.value.identity).addMachineToTeam(team, machine),
  createMachineToken: (machine, scopes) => createAccessPorts(client.value.identity).createMachineToken(machine, scopes)
}

const loadAccessData = async () => {
  accessLoadError.value = ''
  try {
    const [teamList, principalList] = await Promise.all([
      client.value.identity.teams.list(),
      client.value.identity.principals.list()
    ])
    teams.value = teamList.data ?? []
    principals.value = principalList.data ?? []
  } catch (error) {
    accessLoadError.value = errorMessage(error)
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
  if (key === 'access') loadAccessData()
})

const focusHeading = () => nextTick(() => heading.value?.focus())
const focusErrorSummary = () => nextTick(() => errorSummary.value?.focus())

const next = () => {
  attempted.value = true
  if (Object.keys(stepErrors.value).length > 0) {
    focusErrorSummary()
    return
  }
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
      stepIndex.value = steps.value.findIndex(step => step.key === invalid)
      attempted.value = true
      focusErrorSummary()
      return
    }
    plan.value = { ...buildActions(draft, entryOptions.value, ports), mode: draft.access.mode }
    Object.assign(progress, createProgress(plan.value.actions))
  }
  running.value = true
  const ok = await runActions(plan.value.actions, progress)
  running.value = false
  succeeded.value = ok
  if (ok) focusHeading()
}

const created = computed(() => (plan.value ? createdSoFar(plan.value.actions, progress) : []))

const snippets = computed(() => {
  if (!succeeded.value || !plan.value) return null
  const id = draft.basics.id
  return snippetsFor({
    id,
    url: repositoryUrl({ base: productionUrl, id }),
    tokenName: plan.value.result.tokenName
  })
})

type Snippets = ReturnType<typeof snippetsFor>

const SNIPPET_TITLES = [
  { key: 'maven-settings', label: 'Maven settings.xml, server entry', text: (snippet: Snippets) => snippet.maven.settings },
  { key: 'maven-distribution', label: 'Maven pom.xml, distributionManagement', text: (snippet: Snippets) => snippet.maven.distributionManagement },
  { key: 'gradle', label: 'Gradle (Kotlin DSL), publishing', text: (snippet: Snippets) => snippet.gradle },
  { key: 'environment', label: 'Environment variables', text: (snippet: Snippets) => snippet.environment }
]

const copyText = async (key: string, text: string) => {
  if (await copyToClipboard(text, globalThis.navigator?.clipboard)) copied.value = key
}

/*
 * A repository whose save failed is still in the unsaved settings. Cancelling takes it back out, so
 * the settings bar does not offer to save a repository the user has given up on. A saved one stays.
 */
const discardUnsavedEntry = () => {
  if (!plan.value || progress.settings?.state === 'done') return
  const entries = (configuration.configurations.value.maven?.repositories ?? []) as { id?: unknown }[]
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
  router.push({ name: 'settings-repository', params: { id } })
}

const onModalUpdate = (open: boolean) => {
  if (!open) cancel()
}
</script>

<template>
  <MdDialog
    :model-value="modelValue"
    labelledby="wizard-title"
    size="compact"
    :dismissable="dismissable"
    @update:model-value="onModalUpdate"
  >
    <!-- The form fills the full-screen frame below 600px, so the buttons sit at the bottom of a short step. -->
    <form
      id="repository-wizard"
      class="flex min-h-full flex-col"
      novalidate
      data-ingot-component="repository-wizard"
      @submit.prevent="onSubmit"
    >
      <header
        data-ingot-part="header"
        class="flex flex-col gap-4"
      >
        <h2
          id="wizard-title"
          class="md-headline-small text-on-surface"
        >
          New repository
        </h2>
        <WizardStepIndicator
          :steps="steps"
          :step-index="stepIndex"
        />
        <!-- The step title carries data-autofocus, so the dialog frame focuses it on open: Escape reaches the dialog and the step is announced. -->
        <h3
          id="wizard-step-title"
          ref="heading"
          tabindex="-1"
          data-autofocus
          class="md-title-large text-on-surface focus:outline-none"
        >
          {{ steps[stepIndex].label }}
        </h3>
      </header>

      <div class="flex-1 py-4">
        <div
          v-if="attempted && errorList.length > 0"
          ref="errorSummary"
          tabindex="-1"
          data-test="error-summary"
          class="mb-4 focus:outline-none"
        >
          <MdAlert
            tone="error"
            title="Fix the highlighted fields to continue."
          >
            <ul class="m-0 flex list-disc flex-col gap-1 pl-5">
              <li
                v-for="(message, position) in errorList"
                :key="position"
              >
                {{ message }}
              </li>
            </ul>
          </MdAlert>
        </div>
        <!-- Type -->
        <div
          v-if="stepKey === 'type'"
          class="flex flex-col gap-6"
        >
          <WizardFieldGroup legend="What kind of repository this is">
            <WizardChoiceCard
              v-for="choice in TYPE_CHOICES"
              :id="`wizard-type-${choice.value.toLowerCase()}`"
              :key="choice.value"
              name="repository-type"
              :value="choice.value"
              :model-value="draft.type"
              :label="choice.label"
              :who="choice.who"
              @update:model-value="chooseType(draft, $event)"
            />
          </WizardFieldGroup>
        </div>

        <!-- Basics -->
        <div
          v-else-if="stepKey === 'basics'"
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
          <WizardFieldGroup :legend="draft.type === 'PROXY' ? 'Where the cached files are kept' : 'Where the files are kept'">
            <WizardChoiceCard
              v-for="option in storageVariants"
              :key="option.value"
              v-model="draft.storage.kind"
              name="storage-kind"
              :value="option.value"
              :label="option.label"
              :who="option.who"
            />
          </WizardFieldGroup>

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
          <template v-else-if="draft.storage.kind === 's3'">
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
          <template v-else>
            <MdTextField
              v-for="field in storageFields"
              :id="`wizard-plugin-${field.name}`"
              :key="field.name"
              :model-value="draft.storage.plugin[field.name] ?? ''"
              :label="field.label"
              variant="outlined"
              autocomplete="off"
              :required="field.required"
              :supporting-text="field.required ? '' : 'Optional. Empty uses the server default.'"
              :error="Boolean(shownErrors[field.name])"
              :error-text="shownErrors[field.name] ?? ''"
              @update:model-value="draft.storage.plugin[field.name] = $event"
            />
          </template>
        </div>

        <!-- Remote repositories and cache, for a proxy -->
        <div
          v-else-if="stepKey === 'upstream'"
          class="flex flex-col gap-6"
        >
          <section
            aria-labelledby="wizard-remotes-title"
            class="flex flex-col gap-3"
          >
            <h3
              id="wizard-remotes-title"
              class="md-title-small text-on-surface"
            >
              Remote repositories
            </h3>
            <p class="md-body-medium text-on-surface-variant">
              The files are fetched from these addresses. Add a login only when a remote needs one.
            </p>
            <WizardRemoteFields
              v-for="(remote, index) in draft.upstream.remotes"
              :key="index"
              :remote="remote"
              :index="index"
              :errors="shownErrors"
              :removable="draft.upstream.remotes.length > 1"
              @edit="(field, value) => editRemote(index, field, value)"
              @remove="removeRemote(index)"
            />
            <MdAlert
              v-if="shownErrors.remote0 && draft.upstream.remotes.length === 0"
              tone="error"
            >
              {{ shownErrors.remote0 }}
            </MdAlert>
            <div>
              <MdButton
                data-test="add-remote"
                variant="tonal"
                type="button"
                @click="addRemote"
              >
                Add remote repository
              </MdButton>
            </div>
          </section>

          <section
            aria-labelledby="wizard-cache-title"
            class="flex flex-col gap-4"
          >
            <h3
              id="wizard-cache-title"
              class="md-title-small text-on-surface"
            >
              Cache
            </h3>
            <div class="flex flex-col gap-2">
              <MdCheckbox
                id="wizard-cache-store"
                v-model="draft.upstream.store"
              >
                Keep fetched files here
              </MdCheckbox>
              <p class="md-body-medium text-on-surface-variant">
                Ingot keeps a copy of what it fetches, which reduces the response time.
              </p>
            </div>
            <MdSelect
              id="wizard-storage-policy"
              :model-value="draft.upstream.storagePolicy"
              label="Storage policy"
              :options="STORAGE_POLICIES"
              supporting-text="Prefer upstream checks the remote for the latest metadata. Strict uses the cache first, for offline use."
              @update:model-value="draft.upstream.storagePolicy = $event"
            />
            <MdTextField
              id="wizard-metadata-max-age"
              :model-value="String(draft.upstream.metadataMaxAge)"
              label="Metadata max age, in seconds"
              variant="outlined"
              autocomplete="off"
              inputmode="numeric"
              supporting-text="Empty uses the server default."
              :error="Boolean(shownErrors.metadataMaxAge)"
              :error-text="shownErrors.metadataMaxAge ?? ''"
              @update:model-value="draft.upstream.metadataMaxAge = $event"
            />
            <MdTextField
              id="wizard-resolution-cache"
              :model-value="String(draft.upstream.resolutionCacheMaxEntries)"
              label="Resolution cache entries"
              variant="outlined"
              autocomplete="off"
              inputmode="numeric"
              supporting-text="Empty uses the server default. 0 turns the cache off."
              :error="Boolean(shownErrors.resolutionCacheMaxEntries)"
              :error-text="shownErrors.resolutionCacheMaxEntries ?? ''"
              @update:model-value="draft.upstream.resolutionCacheMaxEntries = $event"
            />
          </section>
        </div>

        <!-- Members, for a group -->
        <div
          v-else-if="stepKey === 'members'"
          class="flex flex-col gap-4"
        >
          <p class="md-body-medium text-on-surface-variant">
            The group lists these repositories, in this order. It has no files of its own, and nothing can be uploaded to it.
          </p>
          <RepositoryMembersPicker
            id-prefix="wizard-members"
            :model-value="draft.members.map(id => ({ reference: id }))"
            :candidates="existingIds"
            :error="shownErrors.members ?? ''"
            @update:model-value="draft.members = memberIds($event)"
          />
        </div>

        <!-- Access -->
        <div
          v-else-if="stepKey === 'access'"
          class="flex flex-col gap-6"
        >
          <WizardFieldGroup legend="Who can read it">
            <WizardChoiceCard
              v-for="option in VISIBILITY_OPTIONS"
              :key="option.value"
              v-model="draft.basics.visibility"
              name="visibility"
              :value="option.value"
              :label="option.label"
              :who="option.who"
            />
            <p
              v-if="shownErrors.visibility"
              class="md-body-small text-error"
            >
              {{ shownErrors.visibility }}
            </p>
          </WizardFieldGroup>
          <WizardFieldGroup legend="Who gets access to this repository">
            <WizardChoiceCard
              v-for="option in ACCESS_MODES"
              :key="option.value"
              v-model="draft.access.mode"
              name="access-mode"
              :value="option.value"
              :label="option.label"
              :who="option.who"
            />
          </WizardFieldGroup>

          <MdAlert
            v-if="accessLoadError"
            data-test="access-load-error"
            tone="error"
          >
            Could not load the teams: {{ accessLoadError }}
            <template #action>
              <MdButton
                data-test="reload-access"
                variant="text"
                type="button"
                @click="loadAccessData"
              >
                Retry
              </MdButton>
            </template>
          </MdAlert>

          <MdSelect
            v-if="draft.access.mode === 'existing'"
            id="wizard-team"
            :model-value="draft.access.team"
            label="Team"
            :options="teamOptions"
            :supporting-text="teams.length > 0 ? 'Everyone in the team gets the role below on this repository.' : 'There are no teams yet. Create a team for this repository instead.'"
            :error="Boolean(shownErrors.team)"
            :error-text="shownErrors.team ?? ''"
            @update:model-value="draft.access.team = $event"
          />
          <MdTextField
            v-if="draft.access.mode === 'new'"
            id="wizard-new-team"
            :model-value="newTeamNameOf(draft)"
            label="Team name"
            variant="outlined"
            autocomplete="off"
            supporting-text="Suggested from the repository id. Change it if you like."
            :error="Boolean(shownErrors.newTeam)"
            :error-text="shownErrors.newTeam ?? ''"
            @update:model-value="draft.access.newTeam = $event"
          />

          <template v-if="draft.access.mode !== 'none'">
            <MdSelect
              id="wizard-role"
              :model-value="draft.access.role"
              label="Role"
              :options="roleOptions"
              :supporting-text="roleExplanation(draft.access.role)"
              :error="Boolean(shownErrors.role)"
              :error-text="shownErrors.role ?? ''"
              @update:model-value="draft.access.role = $event"
            />

            <div class="flex flex-col gap-2">
              <MdCheckbox
                id="wizard-machine"
                v-model="draft.access.machine"
              >
                Also create a machine account with a token
              </MdCheckbox>
              <p class="md-body-medium text-on-surface-variant">
                For a build or a script that publishes here. The account joins the team above, because that is where its rights come from.
              </p>
            </div>

            <template v-if="draft.access.machine">
              <MdTextField
                id="wizard-machine-name"
                :model-value="machineNameOf(draft)"
                label="Machine account name"
                variant="outlined"
                autocomplete="off"
                supporting-text="Suggested from the repository id. The token has the same name, which is the user name for a login."
                :error="Boolean(shownErrors.machineName)"
                :error-text="shownErrors.machineName ?? ''"
                @update:model-value="draft.access.machineName = $event"
              />
              <MdSelect
                id="wizard-machine-scope"
                :model-value="draft.access.scope"
                label="What the token may do"
                :options="scopeOptions"
                supporting-text="The token can never do more than the role of the team allows."
                @update:model-value="draft.access.scope = $event"
              />
            </template>
          </template>
        </div>

        <!-- Review, and the create sequence -->
        <div
          v-else
          class="flex flex-col gap-6"
        >
          <template v-if="!succeeded">
            <WizardReviewSummary :rows="summary" />

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

            <WizardProgressList
              v-else
              :actions="plan.actions"
              :progress="progress"
              :created="created"
              :running="running"
              @retry="create"
            />
          </template>

          <!-- Success: the one-time secret, and the snippets that use the repository -->
          <div
            v-else-if="plan"
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
              v-else-if="plan.result.teamName"
              class="md-body-medium text-on-surface-variant"
            >
              Team {{ plan.result.teamName }} has access to this repository. No machine account was created, so the snippets below need a token from the Access page before they can publish.
            </p>
            <p
              v-else
              class="md-body-medium text-on-surface-variant"
            >
              No access was set up. The snippets below need a token before they can publish.
            </p>

            <section
              v-if="draft.type !== 'HOSTED'"
              aria-labelledby="wizard-address-title"
              class="flex flex-col gap-2"
            >
              <h3
                id="wizard-address-title"
                class="md-title-small text-on-surface"
              >
                Address
              </h3>
              <p class="md-body-medium text-on-surface-variant">
                A {{ typeLabelOf(draft.type).toLowerCase() }} repository takes no uploads, so there are no publishing snippets. Clients read from this address.
              </p>
              <code
                data-test="address"
                class="break-all rounded-corner-small bg-surface-container-lowest p-3 font-mono md-body-small text-on-surface"
              >{{ addressOf }}</code>
            </section>

            <section
              v-else-if="snippets"
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
                  class="whitespace-pre-wrap break-words rounded-corner-small bg-surface-container-lowest p-3 font-mono md-body-small text-on-surface"
                >{{ snippet.text(snippets) }}</pre>
              </div>
            </section>
          </div>
        </div>
      </div>

      <footer class="flex flex-wrap items-center justify-end gap-2 pt-2">
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
  </MdDialog>
</template>
