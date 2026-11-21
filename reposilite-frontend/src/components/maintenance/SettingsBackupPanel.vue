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
/*
 * The settings file actions of the whole configuration, on the Settings backup tab of Maintenance. The handlers are the ones
 * Overview drew its header actions with: the file is exported from the saved state and encrypted, and an import goes into the
 * draft and is reviewed before anything is saved.
 */
import { computed, inject, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import download from 'downloadjs'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import MdButton from '../md/MdButton.vue'
import PasswordDialog from '../settings/PasswordDialog.vue'
import SettingsDownloadMenu from '../settings/SettingsDownloadMenu.vue'
import SettingsImportErrors from '../settings/SettingsImportErrors.vue'
import SettingsReviewSheet from '../settings/SettingsReviewSheet.vue'
import UnsavedChangesBar from '../settings/UnsavedChangesBar.vue'
import { diffSettings, revertChange as revertDraftChange } from '../settings/settingsDiff'
import { serializeSettings, settingsByDomain, settingsClockKey, settingsFileName, systemClock } from '../settings/settingsExport'
import { isEncryptedExport } from '../settings/settingsEncryption'
import { overlayImport, parseSettingsFile, validateImport } from '../settings/settingsImport'
import type { ImportError, SettingsFile } from '../settings/settingsImport'
import type { SettingsChange } from '../settings/settingsDiff'

const { store: sharedStore } = useConfiguration()
const store = inject<typeof sharedStore | null>(configurationStoreKey, null) ?? sharedStore

const {
  domains,
  configurations,
  savedSettings,
  loadError,
  saving,
  hasUnsavedChanges,
  changeCount,
  invalidDomains,
  secretsHidden,
  conflict
} = store

// The page is opened by an administrator who may edit the settings, so the domains are read here when Overview did not read them.
onMounted(() => {
  if (domains.value.length === 0) store.fetchConfiguration()
})

/*
 * The file is named after the day it is made and holds the saved state, which the server reads. It is encrypted
 * with a password the user types, so a copy of the file does not carry the secrets in clear. The draft is never
 * downloaded: it is not stored on the server, and it may hold a secret the browser has not seen in clear.
 */
const clock = inject(settingsClockKey, systemClock)
const hasDomains = computed(() => domains.value.length > 0)

/* The password dialog: an export or the opening of an import file. `exported` holds the file while it waits for its password. */
interface PasswordDialogState {
  open: boolean
  mode: 'export' | 'import'
  busy: boolean
  error: string
  exported: SettingsFile | null
}

const passwordDialog = ref<PasswordDialogState>({ open: false, mode: 'export', busy: false, error: '', exported: null })

const downloadSaved = () => {
  passwordDialog.value = { open: true, mode: 'export', busy: false, error: '', exported: null }
}
// Not reachable: the menu disables the draft, because the draft is not part of the file.
const downloadDraft = () => {}

const cancelPassword = () => {
  passwordDialog.value = { open: false, mode: 'export', busy: false, error: '', exported: null }
}

const submitPassword = async (password: string) => {
  const dialog = passwordDialog.value
  passwordDialog.value = { ...dialog, busy: true, error: '' }
  try {
    if (dialog.mode === 'export') {
      const exported = await store.exportSaved(password)
      download(serializeSettings(exported), settingsFileName(clock()), 'application/json')
      cancelPassword()
      return
    }

    // The import mode is only opened with the file in `exported`, so it is set here.
    const document = await store.openExport(password, dialog.exported as SettingsFile)
    cancelPassword()
    applyImportedDocument(document)
  } catch (error) {
    // The client raises an axios error; any other value has no response, so the fallback text is shown.
    const response = (error as { response?: { data?: { message?: string } } } | null | undefined)?.response
    passwordDialog.value = {
      ...dialog,
      busy: false,
      error: response?.data?.message ?? 'The settings file could not be opened.'
    }
  }
}

/*
 * A settings file goes into the draft only when every domain in it is valid. Then the review opens
 * with the imported changes, so the user checks them before anything is saved. Otherwise the errors
 * are listed and the draft stays as it was.
 */
const importInput = ref<HTMLInputElement | null>(null)
const importErrors = ref<ImportError[]>([])

const importFile = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  const parsed = parseSettingsFile(await file.text())
  // A parsed file always carries its value; the guard only narrows the type for the code below.
  if (!parsed.ok || !parsed.value) {
    importErrors.value = [{ domain: '', label: '', message: parsed.error }]
    return
  }
  // An export is opened with its password first. A plain file is applied as it always was.
  if (isEncryptedExport(parsed.value)) {
    passwordDialog.value = { open: true, mode: 'import', busy: false, error: '', exported: parsed.value }
    return
  }
  applyImportedDocument(parsed.value)
}

const applyImportedDocument = (document: SettingsFile) => {
  const result = validateImport(overlayImport(document, configurations.value), {
    domains: domains.value,
    schemas: store.schemas.value,
    validator: store.configurationValidator
  })
  if (!result.ok) {
    importErrors.value = result.errors
    return
  }
  importErrors.value = []
  // The validated values are one object per domain, which is the shape the store holds.
  store.importDomains(result.domains as Record<string, Record<string, unknown>>)
  reviewOpen.value = true
}

/*
 * The review of an import lists the same changes the bar counts: the saved state against the draft, per domain. Revert puts
 * one change back to its saved value in the draft, and nothing reaches the server until Save.
 */
const reviewOpen = ref(false)
const changes = computed<SettingsChange[]>(() => [...diffSettings(
  settingsByDomain(domains.value, savedSettings.value),
  settingsByDomain(domains.value, configurations.value),
  store.schemas.value
)])
const countOfChanges = computed(() => (changes.value.length > 0 ? changes.value.length : changeCount.value))

// The sheet is modal and covers the page, so it closes once there is nothing left in it to review.
watch(() => changes.value.length, (count) => {
  if (count === 0) reviewOpen.value = false
})

const closeReview = () => {
  reviewOpen.value = false
}

const revertChange = (change: SettingsChange) => {
  const domain = change.domain
  // A revert of a domain that has a saved value always returns that domain's data.
  store.setDomainData(domain, revertDraftChange(configurations.value[domain], savedSettings.value[domain], change) as Record<string, unknown>)
  store.validateDomain(domain)
}

const discardAll = () => {
  store.discardChanges()
  closeReview()
}

// The sheet closes when the save went through, or when it was refused as a conflict. Any other refusal keeps the changes on screen with the error.
const saveFromReview = async () => {
  const saved = await store.saveConfiguration()
  if (saved || conflict.value) reviewOpen.value = false
}

const save = () => store.saveConfiguration()
const discard = () => store.discardChanges()
</script>

<template>
  <div
    class="flex flex-col gap-8"
    data-ingot-component="settings-backup-panel"
  >
    <section
      aria-labelledby="settings-backup-download"
      class="flex flex-col gap-3"
    >
      <h2
        id="settings-backup-download"
        class="md-title-medium text-on-surface"
      >
        Download
      </h2>
      <p class="md-body-medium text-on-surface-variant">
        Exports the saved settings as a JSON file, encrypted with a password of at least 12 characters, with the secrets inside the encrypted file; unsaved changes are left out.
      </p>
      <div>
        <SettingsDownloadMenu
          :disabled="!hasDomains"
          :has-unsaved="hasUnsavedChanges"
          :can-include-unsaved="false"
          :secrets-hidden="secretsHidden"
          @saved="downloadSaved"
          @draft="downloadDraft"
        />
      </div>
    </section>

    <section
      aria-labelledby="settings-backup-import"
      class="flex flex-col gap-3"
    >
      <h2
        id="settings-backup-import"
        class="md-title-medium text-on-surface"
      >
        Import
      </h2>
      <p class="md-body-medium text-on-surface-variant">
        Replaces the values named in a settings file in the draft and keeps the rest; nothing is stored until you save.
      </p>
      <div>
        <MdButton
          variant="outlined"
          type="button"
          :disabled="!hasDomains"
          @click="importInput?.click()"
        >
          Import settings
        </MdButton>
        <input
          ref="importInput"
          type="file"
          accept=".json,application/json"
          class="hidden"
          aria-label="Settings file to import"
          @change="importFile"
        >
      </div>
    </section>

    <p class="md-body-medium text-on-surface-variant">
      <RouterLink
        :to="{ name: 'system-info', query: { tab: 'overview' } }"
        class="text-primary underline"
      >
        To set every setting back to its default, use the factory reset on System and support
      </RouterLink>
    </p>

    <p
      v-if="loadError"
      role="alert"
      class="md-body-medium text-error"
    >
      {{ loadError }}
    </p>

    <SettingsImportErrors
      v-if="importErrors.length > 0"
      :errors="importErrors"
      @dismiss="importErrors = []"
    />

    <div
      v-if="hasUnsavedChanges"
      class="sticky bottom-0 z-(--z-index-md-sticky)"
    >
      <UnsavedChangesBar
        :count="countOfChanges"
        :invalid="invalidDomains.length > 0"
        :saving="saving"
        @save="save"
        @discard="discard"
        @review="reviewOpen = true"
      />
    </div>

    <PasswordDialog
      :open="passwordDialog.open"
      :mode="passwordDialog.mode"
      :busy="passwordDialog.busy"
      :error="passwordDialog.error"
      @submit="submitPassword"
      @cancel="cancelPassword"
    />

    <SettingsReviewSheet
      :open="reviewOpen"
      :changes="changes"
      :invalid="invalidDomains.length > 0"
      :saving="saving"
      @update:open="closeReview"
      @revert="revertChange"
      @discard="discardAll"
      @save="saveFromReview"
    />
  </div>
</template>
