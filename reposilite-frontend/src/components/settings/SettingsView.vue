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
import { computed, inject, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { JsonForms } from '@jsonforms/vue'
import download from 'downloadjs'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import FactoryResetModal from './FactoryResetModal.vue'
import MdButton from '../md/MdButton.vue'
import SettingsTabs from './SettingsTabs.vue'
import CollectionEditor from './CollectionEditor.vue'
import UnsavedChangesBar from './UnsavedChangesBar.vue'
import RepositoryWizard from './RepositoryWizard.vue'
import { collectionsOf, entryLayout, idText, validateNewId } from './collections'
import { barClearance } from './barClearance'
import SettingsDownloadMenu from './SettingsDownloadMenu.vue'
import { serializeSettings, settingsByDomain, settingsClockKey, settingsFileName, systemClock } from './settingsExport'
import SettingsReviewSheet from './SettingsReviewSheet.vue'
import { diffSettings, revertChange as revertDraftChange } from './settingsDiff'
import { parseSettingsFile, validateImport } from './settingsImport'
import SettingsImportErrors from './SettingsImportErrors.vue'
import { property } from '../../helpers/vue-extensions'

/*
 * The address decides what is shown. `settings` (/settings/:domain?) names the domain, and
 * `settings-entry` (/settings/:domain/:collection/:entry) also names an entry of one collection.
 * The page passes the route params in as props; the view keeps no selection of its own.
 * Selecting an entry, or going back to the list, is pushed, so the back button returns to the
 * previous selection. A new entry replaces the address, because the one just created is the place
 * to be. The domain on screen is reported with update:domain, and the page writes it to the address.
 */
const props = defineProps({
  selectedTab: property(String, true),
  // The address: the domain on screen, and for an entry the collection and the entry in it.
  domain: {
    type: String,
    default: undefined
  },
  collection: {
    type: String,
    default: undefined
  },
  entry: {
    type: String,
    default: undefined
  }
})

const emit = defineEmits(['update:domain'])

const { store: sharedStore, renderers, configurationStyles } = useConfiguration()
const store = inject(configurationStoreKey, null) ?? sharedStore
const router = useRouter()
const route = useRoute()

/*
 * 'styles' is the injection key useStyles() of @dzikoysk/vue-vanilla looks up, so every
 * renderer below this component takes its class names from here.
 */
provide('styles', configurationStyles)

const {
  domains,
  configurations,
  loadError,
  saving,
  hasUnsavedChanges,
  changeCount,
  invalidDomains,
  snapshots,
  secretsHidden
} = store

// The domain on screen is the one the store holds. The address sets it, and the tabs set it;
// the watchers below keep the address and the store in step, so there is one place it lives.
const activeDomain = computed(() => {
  if (domains.value.includes(store.selectedDomain.value)) return store.selectedDomain.value
  return domains.value[0]
})

const domainTabs = computed(() => domains.value.map(domain => ({ key: domain, label: store.titleOf(domain) })))

const collectionsFor = (domain) => collectionsOf(store.schemas.value[domain])
const activeCollections = computed(() => (activeDomain.value ? collectionsFor(activeDomain.value) : []))

const entriesOf = (domain, collection) => configurations.value[domain]?.[collection.key] ?? []

const selectedIdFor = (collection) => (props.collection === collection.key ? (props.entry ?? null) : null)

/*
 * The fields of a domain that are not collections, as one generated form. Most domains have none
 * of these, and then no form is drawn for them.
 */
const domainForm = computed(() => {
  const domain = activeDomain.value
  const schema = store.schemas.value[domain]
  if (!domain || !schema?.properties) return null
  const collectionKeys = collectionsFor(domain).map(collection => collection.key)
  const fields = Object.fromEntries(
    Object.entries(schema.properties).filter(([key]) => !collectionKeys.includes(key))
  )
  if (Object.keys(fields).length === 0) return null
  const fieldSchema = { ...schema, properties: fields }
  return { schema: fieldSchema, layout: entryLayout(fieldSchema, { exclude: [] }) }
})

// The address names the domain on screen. A domain that is not in the address is written there.
watch(
  () => props.domain,
  (domain) => {
    if (domain) store.selectedDomain.value = domain
  },
  { immediate: true }
)

// Reports the domain on screen whenever it differs from the address: when a tab is picked, when the
// address names none or one the server does not have, or once the list has arrived. The page answers
// with a replace, after which the two agree and nothing is reported again, so there is no loop.
watch([activeDomain, () => props.domain], ([domain, requested]) => {
  if (domain && domain !== requested) emit('update:domain', domain)
}, { immediate: true })

const selectDomain = (domain) => {
  store.selectedDomain.value = domain
}

const selectEntry = (collection, id) => {
  router.push({ name: 'settings-entry', params: { domain: activeDomain.value, collection: collection.key, entry: id } })
}

const backToList = () => {
  router.push({ name: 'settings', params: { domain: activeDomain.value } })
}

/*
 * The maven repositories are created through the guided wizard instead of the id dialog. The
 * wizard is opened by "New repository", or by a link with ?new=repository, such as the checklist
 * on the dashboard. That link is consumed once the wizard opens, so closing it does not reopen it.
 */
const wizardOpen = ref(false)
const openRepositoryWizard = () => { wizardOpen.value = true }
const guidedCreateFor = (collection) => (activeDomain.value === 'maven' && collection.key === 'repositories'
  ? openRepositoryWizard
  : null)

watch([() => route.query.new, activeDomain], ([requested, domain]) => {
  if (requested !== 'repository' || domain !== 'maven') return
  const query = { ...route.query }
  delete query.new
  router.replace({ name: route.name, params: route.params, query })
  openRepositoryWizard()
}, { immediate: true })

const createEntry = (collection, id) => {
  const domain = activeDomain.value
  store.addEntry(domain, collection.key, { id })
  router.replace({ name: 'settings-entry', params: { domain, collection: collection.key, entry: id } })
}

/*
 * An edit is stored at once. The address of the open entry follows its id, but only once the id is
 * usable: until then the address keeps the old id and the entry stays open, so typing a new id does
 * not close the pane.
 */
const updateEntry = (collection, { index, entry }) => {
  const domain = activeDomain.value
  store.setEntry(domain, collection.key, index, entry)
  if (props.collection !== collection.key) return
  const others = (configurations.value[domain]?.[collection.key] ?? [])
    .filter((_, other) => other !== index)
    .map(idText)
  const id = idText(entry)
  if (id === props.entry || validateNewId(id, others, collection.noun) !== '') return
  router.replace({ name: 'settings-entry', params: { domain, collection: collection.key, entry: id } })
}

const deleteEntry = (collection, index) => {
  store.removeEntry(activeDomain.value, collection.key, index)
  router.replace({ name: 'settings', params: { domain: activeDomain.value } })
}

// The not-found pane of an entry links to the list of its domain. It is a real link, so it can be
// opened in a new tab; a plain click is taken by the view and pushed through the router instead.
const backHref = computed(() => (activeDomain.value
  ? router.resolve({ name: 'settings', params: { domain: activeDomain.value } }).href
  : undefined))

const onDomainChange = (domain, event) => {
  store.setDomainData(domain, event.data)
  store.validateDomain(domain)
}

/*
 * The file is named after the day it is made, and holds the saved state unless the user asks for the
 * draft. The saved state is what the server confirmed last, so it is valid whatever the draft holds.
 */
const clock = inject(settingsClockKey, systemClock)
const hasDomains = computed(() => domains.value.length > 0)

const downloadSettingsOf = (source) => {
  const settings = settingsByDomain(domains.value, source)
  download(serializeSettings(settings), settingsFileName(clock()), 'application/json')
}

const downloadSaved = () => downloadSettingsOf(snapshots.value)
const downloadDraft = () => {
  if (invalidDomains.value.length === 0) downloadSettingsOf(configurations.value)
}

/*
 * The bar is sticky at the bottom of the window, so the last card would scroll under it. While it is
 * shown the page keeps the bar's height free below its content, and on phones also the height of the
 * bottom navigation, which the bar sits above. The height is measured, because a wrapped line adds to it.
 */
const barElement = ref(null)
const barHeight = ref(0)
const barObserver = typeof ResizeObserver === 'undefined'
  ? null
  : new ResizeObserver(() => { barHeight.value = barElement.value?.offsetHeight ?? 0 })

watch(barElement, (element, previous) => {
  if (previous) barObserver?.unobserve(previous)
  if (element) barObserver?.observe(element)
  barHeight.value = element?.offsetHeight ?? 0
})

onBeforeUnmount(() => barObserver?.disconnect())

const pageClearance = computed(() => ({
  paddingBottom: `calc(${barClearance(barHeight.value, hasUnsavedChanges.value)}px + var(--app-bottom-bar-height, 0px) + env(safe-area-inset-bottom, 0px))`
}))

const save = () => store.saveConfiguration()
const discard = () => store.discardChanges()
const factoryReset = () => store.factoryReset()

/*
 * The review lists the same changes the bar counts: the saved state against the draft, per domain.
 * Revert puts one change back to its saved value in the draft, and nothing reaches the server until Save.
 */
const reviewOpen = ref(false)
const changes = computed(() => diffSettings(
  settingsByDomain(domains.value, snapshots.value),
  settingsByDomain(domains.value, configurations.value),
  store.schemas.value
))
const countOfChanges = computed(() => (changes.value.length > 0 ? changes.value.length : changeCount.value))

const revertChange = (change) => {
  const domain = change.domain
  store.setDomainData(domain, revertDraftChange(configurations.value[domain], snapshots.value[domain], change))
  store.validateDomain(domain)
}

const discardAll = () => {
  discard()
  reviewOpen.value = false
}

// The sheet closes only when the save went through; a refused save keeps the changes on screen.
const saveFromReview = async () => {
  if (await store.saveConfiguration()) reviewOpen.value = false
}

/*
 * A settings file goes into the draft only when every domain in it is valid. Then the review opens
 * with the imported changes, so the user checks them before anything is saved. Otherwise the errors
 * are listed and the draft stays as it was.
 */
const importInput = ref(null)
const importErrors = ref([])

const importFile = async (event) => {
  const input = event.target
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  const parsed = parseSettingsFile(await file.text())
  if (!parsed.ok) {
    importErrors.value = [{ domain: '', label: '', message: parsed.error }]
    return
  }
  const result = validateImport(parsed.value, {
    domains: domains.value,
    schemas: store.schemas.value,
    validator: store.configurationValidator
  })
  if (!result.ok) {
    importErrors.value = result.errors
    return
  }
  importErrors.value = []
  store.importDomains(result.domains)
  reviewOpen.value = true
}

/* JsonForms configuration */
const formsConfiguration = {
  showUnfocusedDescription: true
}

watch(
  () => props.selectedTab,
  (selectedTab, prev) => {
    /* Fetch configuration only when user opens the configuration tab  */
    if (selectedTab === 'Settings' && prev == undefined && domains.value.length == 0)
      store.fetchConfiguration()
  },
  { immediate: true }
)
</script>

<template>
  <div
    class="settings-view container mx-auto pt-7 pb-4"
    :style="pageClearance"
  >
    <header class="flex flex-col gap-4 pb-6 md:flex-row md:items-end md:justify-between">
      <div class="flex min-w-0 flex-col gap-1">
        <h1 class="md-headline-medium text-on-surface">
          Settings
        </h1>
        <p class="md-body-medium text-on-surface-variant">
          Shared configuration for all instances. Changes can take up to 10 seconds to reach every instance.
        </p>
      </div>
      <div class="flex shrink-0 flex-wrap items-center gap-2">
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
        <SettingsDownloadMenu
          :disabled="!hasDomains"
          :has-unsaved="hasUnsavedChanges"
          :can-include-unsaved="invalidDomains.length === 0"
          :secrets-hidden="secretsHidden"
          @saved="downloadSaved"
          @draft="downloadDraft"
        />
      </div>
    </header>

    <p
      v-if="loadError"
      role="alert"
      class="md-body-medium text-error pb-4"
    >
      {{ loadError }}
    </p>

    <SettingsImportErrors
      v-if="importErrors.length > 0"
      :errors="importErrors"
      @dismiss="importErrors = []"
    />

    <template v-if="activeDomain">
      <SettingsTabs
        v-if="domains.length > 1"
        :model-value="activeDomain"
        label="Configuration domains"
        variant="secondary"
        :tabs="domainTabs"
        @update:model-value="selectDomain"
      />
      <h2
        v-else
        class="md-title-large text-on-surface"
      >
        {{ store.titleOf(activeDomain) }}
      </h2>

      <div
        role="tabpanel"
        class="mt-6 flex flex-col gap-8"
      >
        <JsonForms
          v-if="domainForm && configurations[activeDomain]"
          :key="`domain-fields:${activeDomain}`"
          :config="formsConfiguration"
          :data="configurations[activeDomain]"
          :schema="domainForm.schema"
          :uischema="domainForm.layout"
          :renderers="renderers"
          :ajv="store.configurationValidator"
          @change="onDomainChange(activeDomain, $event)"
        />

        <CollectionEditor
          v-for="list in activeCollections"
          :key="`${activeDomain}:${list.key}`"
          :collection="list"
          :entries="entriesOf(activeDomain, list)"
          :keys="store.entryKeysOf(activeDomain, list.key)"
          :selected-id="selectedIdFor(list)"
          :back-href="backHref"
          :renderers="renderers"
          :validator="store.configurationValidator"
          :guided-create="guidedCreateFor(list)"
          @select="selectEntry(list, $event)"
          @back="backToList"
          @create="createEntry(list, $event)"
          @change="updateEntry(list, $event)"
          @delete="deleteEntry(list, $event)"
        />
      </div>
    </template>
    <p
      v-else-if="!loadError"
      class="mt-6 md-body-large text-on-surface-variant"
    >
      Loading configuration
    </p>

    <section
      aria-labelledby="settings-danger-title"
      class="mt-10 flex flex-col gap-4 rounded-corner-large border border-error p-4 md:flex-row md:items-center md:justify-between md:p-6"
    >
      <div class="flex min-w-0 flex-col gap-1">
        <h2
          id="settings-danger-title"
          class="md-title-large text-error"
        >
          Danger zone
        </h2>
        <p class="md-body-medium text-on-surface-variant">
          A factory reset sets every setting back to its default and saves it to all instances at once.
        </p>
      </div>
      <FactoryResetModal :callback="factoryReset">
        <template #button>
          <MdButton
            variant="outlined"
            type="button"
            error
            class="shrink-0"
          >
            Factory reset
          </MdButton>
        </template>
      </FactoryResetModal>
    </section>

    <RepositoryWizard
      v-if="activeDomain === 'maven'"
      v-model="wizardOpen"
    />

    <div
      v-if="hasUnsavedChanges"
      ref="barElement"
      class="sticky bottom-0 z-10 mt-6 max-[599px]:bottom-[calc(var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom,0px))]"
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

    <SettingsReviewSheet
      :open="reviewOpen"
      :changes="changes"
      :invalid="invalidDomains.length > 0"
      :saving="saving"
      @update:open="reviewOpen = $event"
      @revert="revertChange"
      @discard="discardAll"
      @save="saveFromReview"
    />
  </div>
</template>

<!--suppress CssInvalidAtRule -->
<style>
@reference "../../style.css";
/*
 * Read-only inputs look like fields that cannot be typed in. Every rule is scoped to .settings-view,
 * so none of it reaches the header or another view.
 */
.settings-view input:not([type="checkbox"]):read-only {
  @apply bg-surface-container text-on-surface-variant;
}
</style>
