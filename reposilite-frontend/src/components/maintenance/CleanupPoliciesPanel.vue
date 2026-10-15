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
import { ref } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { useSession } from '../../store/session'
import { errorMessage, createErrorToast } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdSwitch from '../md/MdSwitch.vue'
import PencilIcon from '../icons/PencilIcon.vue'
import TrashIcon from '../icons/TrashIcon.vue'
import { SCRIM_STYLE } from '../settings/dialog'
import DeletePolicyDialog from './DeletePolicyDialog.vue'
import DryRunResult from './DryRunResult.vue'
import PolicyDialog from './PolicyDialog.vue'
import RunHistory from './RunHistory.vue'
import RunNowDialog from './RunNowDialog.vue'
import { lastRunOf, normalizeDryRun, normalizeRun, statusLabel } from './cleanup'
import { formatDateTime } from './format'
import { normalizePolicy, retentionSummary, scheduleLabel, scopeLabel } from './policy'

/*
 * The cleanup policies: the list, their editor, and the actions that run them. Saving writes the
 * whole cleanup domain, so every change first reads the list the server holds now and then writes
 * it back with the one policy changed. Nothing here deletes an artifact except a run.
 */
const emit = defineEmits(['ran'])

const { client } = useSession()

const policies = ref([])
const runs = ref([])
const repositoryIds = ref([])
const loaded = ref(false)
const loadError = ref('')
const saving = ref(false)
const historyPolicy = ref('')

// The editor: `policy` is null for a new policy.
const editor = ref({ open: false, policy: null })
// The policy whose delete is being confirmed, or null.
const deleting = ref(null)
const preview = ref({ open: false, policyId: '', result: null, error: '' })
const runDialog = ref({ open: false, policyId: '', running: false, result: null, error: '' })

async function loadRuns() {
  const { data } = await client.value.cleanup.runs()
  runs.value = data.map(normalizeRun)
}

async function load() {
  try {
    const [cleanup, maven] = await Promise.all([
      client.value.settings.fetch('cleanup'),
      client.value.settings.fetch('maven')
    ])
    policies.value = (cleanup.data.policies ?? []).map(normalizePolicy)
    repositoryIds.value = (maven.data.repositories ?? []).map(repository => repository.id)
    await loadRuns()
    loadError.value = ''
  } catch (error) {
    loadError.value = `Could not load the cleanup policies: ${errorMessage(error)}`
  } finally {
    loaded.value = true
  }
}

/*
 * Reads the list the server holds, applies `change` to it and writes the result. A failure is
 * shown as the server's message; the list on screen is then left as it was.
 */
async function persist(change) {
  saving.value = true
  try {
    const { data } = await client.value.settings.fetch('cleanup')
    const next = change((data.policies ?? []).map(normalizePolicy))
    await client.value.settings.update('cleanup', { policies: next })
    policies.value = next
    return true
  } catch (error) {
    createErrorToast(errorMessage(error))
    return false
  } finally {
    saving.value = false
  }
}

const openEditor = (policy) => {
  editor.value = { open: true, policy }
}

// The dialog's draft is the policy as the user left it; the stored fields it does not show, such
// as any the dashboard does not know, are kept from the copy the server holds.
async function savePolicy(wire) {
  const editing = editor.value.policy
  const saved = await persist((current) => {
    const original = editing ? current.find(policy => policy.id === editing.id) : undefined
    if (!original) return [...current, { ...wire }]
    return current.map(policy => (policy === original ? { ...original, ...wire } : policy))
  })
  if (saved) editor.value = { open: false, policy: null }
}

const setEnabled = (policy, enabled) =>
  persist(current => current.map(entry => (entry.id === policy.id ? { ...entry, enabled } : entry)))

async function removePolicy() {
  const id = deleting.value
  const removed = await persist(current => current.filter(policy => policy.id !== id))
  if (removed) deleting.value = null
}

async function openPreview(policy) {
  preview.value = { open: true, policyId: policy.id, result: null, error: '' }
  try {
    const { data } = await client.value.cleanup.dryRun(policy.id)
    preview.value.result = normalizeDryRun(data)
  } catch (error) {
    preview.value.error = errorMessage(error)
  }
}

const openRun = (policy) => {
  runDialog.value = { open: true, policyId: policy.id, running: false, result: null, error: '' }
}

async function runNow() {
  runDialog.value.running = true
  runDialog.value.error = ''
  try {
    const { data } = await client.value.cleanup.run(runDialog.value.policyId)
    runDialog.value.result = normalizeRun(data)
    await loadRuns()
    emit('ran')
  } catch (error) {
    runDialog.value.error = errorMessage(error)
  } finally {
    runDialog.value.running = false
  }
}

const lastRunText = (id) => {
  const run = lastRunOf(runs.value, id)
  return run ? `Last run: ${statusLabel(run.status)}, ${formatDateTime(run.finishedAt ?? run.startedAt)}` : 'Never run'
}

const repositoriesOf = (policy) =>
  policy.repositories.length ? policy.repositories.join(', ') : 'no repository'

load()
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="cleanup-policies-title"
  >
    <MdCard
      variant="filled"
      class="flex flex-col gap-4"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex min-w-0 flex-col gap-1">
          <h3
            id="cleanup-policies-title"
            class="md-title-large text-on-surface"
          >
            Cleanup policies
          </h3>
          <p class="md-body-medium text-on-surface-variant">
            A policy deletes artifacts only when it runs, by hand or on its schedule.
          </p>
        </div>
        <MdButton
          variant="filled"
          @click="openEditor(null)"
        >
          New policy
        </MdButton>
      </div>

      <p
        v-if="loadError"
        class="md-body-medium text-error"
        role="alert"
      >
        {{ loadError }}
      </p>
      <p
        v-else-if="!loaded"
        class="md-body-medium text-on-surface-variant"
      >
        Loading policies…
      </p>
      <p
        v-else-if="!policies.length"
        class="md-body-medium text-on-surface-variant"
      >
        No cleanup policies yet. Create one to delete artifacts by rule.
      </p>

      <ul
        v-else
        class="flex flex-col gap-3"
        aria-label="Cleanup policies"
      >
        <li
          v-for="policy in policies"
          :key="policy.id"
          :data-policy="policy.id"
          class="flex flex-col gap-3 rounded-corner-medium bg-surface-container-low p-4"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="flex min-w-0 flex-col gap-1">
              <h4 class="font-mono md-title-medium text-on-surface break-all">
                {{ policy.id }}
              </h4>
              <p class="md-body-medium text-on-surface-variant">
                {{ scopeLabel(policy.scope) }}, {{ repositoriesOf(policy) }}, {{ scheduleLabel(policy.schedule) }}
              </p>
              <p class="md-body-medium text-on-surface-variant">
                {{ retentionSummary(policy) }}
              </p>
              <p
                v-if="policy.groupFilter"
                class="font-mono md-body-small text-on-surface-variant break-all"
              >
                {{ policy.groupFilter }}
              </p>
              <p class="md-body-small text-on-surface-variant">
                {{ lastRunText(policy.id) }}
              </p>
              <p
                v-if="!policy.repositories.length"
                class="md-body-small text-warning"
              >
                This policy affects no repository.
              </p>
            </div>
            <MdSwitch
              :id="`policy-enabled-${policy.id}`"
              :model-value="policy.enabled"
              :disabled="saving"
              @update:model-value="setEnabled(policy, $event)"
            >
              Enabled
            </MdSwitch>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <MdButton
              variant="tonal"
              :aria-label="`Preview ${policy.id}`"
              @click="openPreview(policy)"
            >
              Preview
            </MdButton>
            <MdButton
              variant="outlined"
              :aria-label="`Run ${policy.id} now`"
              @click="openRun(policy)"
            >
              Run now
            </MdButton>
            <MdIconButton
              :aria-label="`Edit policy ${policy.id}`"
              @click="openEditor(policy)"
            >
              <PencilIcon />
            </MdIconButton>
            <MdIconButton
              variant="error"
              :aria-label="`Delete policy ${policy.id}`"
              @click="deleting = policy.id"
            >
              <TrashIcon />
            </MdIconButton>
          </div>
        </li>
      </ul>
    </MdCard>

    <MdCard
      variant="filled"
      class="flex flex-col gap-4"
    >
      <RunHistory
        :runs="runs"
        :policy-ids="policies.map(policy => policy.id)"
        :policy="historyPolicy"
        @update:policy="historyPolicy = $event"
      />
    </MdCard>

    <PolicyDialog
      v-model="editor.open"
      :policy="editor.policy"
      :existing-ids="policies.map(policy => policy.id)"
      :repository-ids="repositoryIds"
      :saving="saving"
      @submit="savePolicy"
    />

    <DeletePolicyDialog
      :model-value="deleting !== null"
      :policy-id="deleting ?? ''"
      @update:model-value="deleting = null"
      @confirm="removePolicy"
    />

    <RunNowDialog
      v-model="runDialog.open"
      :policy-id="runDialog.policyId"
      :running="runDialog.running"
      :result="runDialog.result"
      :error="runDialog.error || null"
      @confirm="runNow"
    />

    <VueFinalModal
      v-model="preview.open"
      teleport-to="body"
      :overlay-style="SCRIM_STYLE"
      class="flex min-h-full items-center justify-center p-4"
    >
      <div
        class="w-[calc(100vw-2rem)] max-w-3xl max-h-[90dvh] overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
        role="dialog"
        aria-modal="true"
        aria-labelledby="preview-title"
      >
        <h2
          id="preview-title"
          class="md-headline-small text-on-surface"
        >
          Preview {{ preview.policyId }}
        </h2>
        <p class="mt-2 md-body-medium text-on-surface-variant">
          A preview deletes nothing. It lists what a run of this policy would delete now.
        </p>
        <div class="mt-5">
          <p
            v-if="preview.error"
            class="md-body-medium text-error"
            role="alert"
          >
            {{ preview.error }}
          </p>
          <p
            v-else-if="!preview.result"
            class="md-body-medium text-on-surface-variant"
          >
            Computing the preview…
          </p>
          <DryRunResult
            v-else
            :preview="preview.result"
          />
        </div>
        <div class="mt-6 flex justify-end">
          <MdButton
            variant="text"
            type="button"
            @click="preview.open = false"
          >
            Close
          </MdButton>
        </div>
      </div>
    </VueFinalModal>
  </section>
</template>
