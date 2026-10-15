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
import { computed, inject, provide, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { JsonForms } from '@jsonforms/vue'
import download from 'downloadjs'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import FactoryResetModal from './FactoryResetModal.vue'
import MdButton from '../md/MdButton.vue'
import SettingsTabs from './SettingsTabs.vue'
import CollectionEditor from './CollectionEditor.vue'
import UnsavedChangesBar from './UnsavedChangesBar.vue'
import { collectionsOf, entryLayout } from './collections'
import { property } from '../../helpers/vue-extensions'

/*
 * The routes are `settings` (/settings/:domain?) and `settings-entry` (/settings/:domain/:collection/:entry).
 * When they exist, the address decides what is shown and each selection is pushed or replaced
 * through the router. Until they exist, the view keeps the selection itself, so the page works
 * under the current router too.
 */
const props = defineProps({
  selectedTab: property(String, true),
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

const { store: sharedStore, renderers, configurationStyles } = useConfiguration()
const store = inject(configurationStoreKey, null) ?? sharedStore
const router = useRouter()

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
  invalidDomains
} = store

const hasRoute = (name) => Boolean(router?.hasRoute?.(name))
const routed = computed(() => hasRoute('settings-entry'))

/* Selection kept by the view itself, used only while the address does not carry one. */
const localSelection = reactive({})

const activeDomain = computed(() => {
  const requested = routed.value ? props.domain : store.selectedDomain.value
  if (domains.value.includes(requested)) return requested
  return domains.value[0]
})

const domainTabs = computed(() => domains.value.map(domain => ({ key: domain, label: store.titleOf(domain) })))

const collectionsFor = (domain) => collectionsOf(store.schemas.value[domain])
const activeCollections = computed(() => (activeDomain.value ? collectionsFor(activeDomain.value) : []))

const entriesOf = (domain, collection) => configurations.value[domain]?.[collection.key] ?? []

const selectedIdFor = (collection) => {
  if (routed.value) return props.collection === collection.key ? (props.entry ?? null) : null
  return localSelection[collection.key] ?? null
}

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

const selectDomain = (domain) => {
  store.selectedDomain.value = domain
  if (hasRoute('settings')) router.push({ name: 'settings', params: { domain } })
}

const selectEntry = (collection, id) => {
  localSelection[collection.key] = id
  if (hasRoute('settings-entry')) {
    router.push({ name: 'settings-entry', params: { domain: activeDomain.value, collection: collection.key, entry: id } })
  }
}

const backToList = (collection) => {
  localSelection[collection.key] = null
  if (hasRoute('settings')) router.push({ name: 'settings', params: { domain: activeDomain.value } })
}

const createEntry = (collection, id) => {
  const domain = activeDomain.value
  store.addEntry(domain, collection.key, { id })
  localSelection[collection.key] = id
  if (hasRoute('settings-entry')) {
    router.replace({ name: 'settings-entry', params: { domain, collection: collection.key, entry: id } })
  }
}

const updateEntry = (collection, { index, entry }) => {
  store.setEntry(activeDomain.value, collection.key, index, entry)
}

const deleteEntry = (collection, index) => {
  store.removeEntry(activeDomain.value, collection.key, index)
  localSelection[collection.key] = null
  if (hasRoute('settings')) router.replace({ name: 'settings', params: { domain: activeDomain.value } })
}

const onDomainChange = (domain, event) => {
  store.setDomainData(domain, event.data)
  store.validateDomain(domain)
}

const canDownload = computed(() => invalidDomains.value.length === 0 && domains.value.length > 0)

const downloadSettings = () => {
  if (!canDownload.value) return
  download(
    JSON.stringify(configurations.value),
    'shared.configuration.json',
    'application/json'
  )
}

const save = () => store.saveConfiguration()
const discard = () => store.discardChanges()
const factoryReset = () => store.factoryReset()

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
  <div class="settings-view container mx-auto pt-7 pb-4">
    <header class="flex flex-col gap-4 pb-6 md:flex-row md:items-end md:justify-between">
      <div class="flex min-w-0 flex-col gap-1">
        <h1 class="md-headline-medium text-on-surface">
          Settings
        </h1>
        <p class="md-body-medium text-on-surface-variant">
          Shared configuration for all instances. Changes can take up to 10 seconds to reach every instance.
        </p>
      </div>
      <MdButton
        variant="outlined"
        type="button"
        class="shrink-0"
        :disabled="!canDownload"
        :title="canDownload ? undefined : 'Download is unavailable until the errors are fixed.'"
        @click="downloadSettings"
      >
        Download as JSON
      </MdButton>
    </header>

    <p
      v-if="loadError"
      role="alert"
      class="md-body-medium text-error pb-4"
    >
      {{ loadError }}
    </p>

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
          :selected-id="selectedIdFor(list)"
          :renderers="renderers"
          :validator="store.configurationValidator"
          @select="selectEntry(list, $event)"
          @back="backToList(list)"
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

    <div
      v-if="hasUnsavedChanges"
      class="sticky bottom-0 z-10 -mx-4 mt-6 min-[600px]:-mx-6 max-[599px]:bottom-[calc(var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom,0px))]"
    >
      <UnsavedChangesBar
        :count="changeCount"
        :invalid="invalidDomains.length > 0"
        :saving="saving"
        @save="save"
        @discard="discard"
      />
    </div>
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
