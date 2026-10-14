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
import {computed, provide, watch} from 'vue'
import {JsonForms} from '@jsonforms/vue'
import {Tabs, Tab, TabPanels, TabPanel} from 'vue3-tabs'
import { useConfiguration } from '../../store/configuration'
import download from 'downloadjs'
import FactoryResetModal from './FactoryResetModal.vue'
import MdButton from '../md/MdButton.vue'
import { property } from '../../helpers/vue-extensions'

const props = defineProps({
  selectedTab: property(String, true)
})

const { store, renderers, configurationStyles } = useConfiguration()
const {
  domains,
  schemas,
  configurations,
  selectedDomain,
  hasChanges,
  invalidDomains,
  dirtyDomains,
  saving,
  saveError,
  loadError
} = store

/*
 * 'styles' is the injection key useStyles() of @dzikoysk/vue-vanilla looks up, so every
 * renderer below this component takes its class names from here.
 */
provide('styles', configurationStyles)

const namesOf = (domains) => domains.map(domain => store.titleOf(domain)).join(', ')
const invalidNames = computed(() => namesOf(invalidDomains.value))
const dirtyNames = computed(() => namesOf(dirtyDomains.value))

const canSave = computed(() => hasChanges.value && invalidDomains.value.length === 0 && !saving.value)
const canDownload = computed(() => invalidDomains.value.length === 0 && domains.value.length > 0)

/* One line next to the actions. Errors take precedence, so a failed save is never hidden. */
const status = computed(() => {
  if (saveError.value) return { text: saveError.value, error: true }
  if (saving.value) return { text: 'Saving', error: false }
  if (invalidDomains.value.length > 0) {
    return { text: `Fix the errors in ${invalidNames.value} to save or download.`, error: true }
  }
  if (hasChanges.value) return { text: `Unsaved changes in ${dirtyNames.value}.`, error: false }
  return null
})

const downloadHint = computed(() =>
  canDownload.value ? null : `Download is unavailable until the errors in ${invalidNames.value} are fixed.`
)

const onFormChange = (domain, event) => {
  store.setDomainData(domain, event.data)
  store.setValidity(domain, event.errors.length === 0)
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
</script>

<template>
  <div class="settings-view container mx-auto pt-7 px-15 pb-4">
    <header class="flex flex-col gap-4 pb-6 md:flex-row md:items-start md:justify-between">
      <div class="flex flex-col gap-1 max-w-prose">
        <p class="md-body-large text-on-surface-variant">
          Modify configuration shared between all instances.
        </p>
        <p class="md-body-medium text-on-surface-variant">
          <strong class="text-on-surface">Remember</strong>: Configuration propagation can take up to 10 seconds on all your instances.
        </p>
      </div>
      <FactoryResetModal :callback="factoryReset">
        <template #button>
          <button
            type="button"
            class="md-state-layer md-focus-ring relative inline-flex min-h-12 shrink-0 items-center justify-center rounded-corner-full px-6 md-label-large text-error"
          >
            Factory reset
          </button>
        </template>
      </FactoryResetModal>
    </header>

    <p
      v-if="loadError"
      role="alert"
      class="md-body-medium text-error pb-4"
    >
      {{ loadError }}
    </p>

    <Tabs v-model="selectedDomain">
      <Tab
        v-for="domain in domains"
        :key="`config:${domain}`"
        class="item"
        :val="domain"
        :label="schemas[domain]?.title"
        :indicator="true"
      />
    </Tabs>
    <TabPanels v-model="selectedDomain">
      <TabPanel
        v-for="domain in domains"
        :key="`config_tab:${domain}`"
        :val="domain"
        class="rounded-corner-large border border-outline-variant bg-surface-container-low p-4 md:p-6"
      >
        <JsonForms
          v-if="configurations[domain]"
          :config="formsConfiguration"
          :data="configurations[domain]"
          :schema="schemas[domain]"
          :renderers="renderers"
          :ajv="store.configurationValidator"
          @change="onFormChange(domain, $event)"
        />
      </TabPanel>
    </TabPanels>

    <div class="sticky bottom-0 z-10 mt-6 flex flex-col gap-3 border-t border-outline-variant bg-surface-container px-6 py-3 md:flex-row md:items-center md:justify-between">
      <p
        id="settings-status"
        class="md-body-medium min-w-0 flex-1"
        :class="status?.error ? 'text-error' : 'text-on-surface-variant'"
        :role="status?.error ? 'alert' : 'status'"
      >
        <template v-if="status">
          {{ status.text }}
        </template>
        <template v-else>
          No unsaved changes.
        </template>
      </p>
      <div class="flex flex-wrap items-center gap-2">
        <MdButton
          variant="outlined"
          :disabled="!canDownload"
          :aria-describedby="downloadHint ? 'settings-download-hint' : undefined"
          :title="downloadHint ?? undefined"
          @click="downloadSettings"
        >
          Download as JSON
        </MdButton>
        <span
          v-if="downloadHint"
          id="settings-download-hint"
          class="sr-only"
        >{{ downloadHint }}</span>
        <MdButton
          variant="text"
          :disabled="!hasChanges || saving"
          @click="discard"
        >
          Reset
        </MdButton>
        <MdButton
          variant="filled"
          :disabled="!canSave"
          @click="save"
        >
          Save
        </MdButton>
      </div>
    </div>
  </div>
</template>

<!--suppress CssInvalidAtRule -->
<style scoped>
@reference "../../style.css";
/*
 * The tabs are vue3-tabs markup, which takes no class names from the styles object. The
 * rules stay here, under .settings-view, so they reach nothing outside this view.
 */
.settings-view :deep(.tabs) {
  @apply flex gap-1 border-b border-outline-variant overflow-x-auto;
}
.settings-view :deep(.item) {
  @apply cursor-pointer px-4 py-3 md-label-large text-on-surface-variant;
  @apply transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard;
  border-top-left-radius: var(--md-sys-shape-corner-small);
  border-top-right-radius: var(--md-sys-shape-corner-small);
}
.settings-view :deep(.item.active) {
  @apply text-primary;
  box-shadow: inset 0 -3px 0 0 var(--md-sys-color-primary);
}
.settings-view :deep(.item:hover) {
  @apply text-on-surface;
}
</style>

<!--suppress CssInvalidAtRule -->
<style>
@reference "../../style.css";
/*
 * Everything the renderer styles object can carry lives in src/store/configuration.js. What is
 * left below stays here because each rule selects on something no key of that object names.
 * Every rule is scoped to .settings-view, so none of it reaches the header or another view.
 * Unlayered rules beat utilities, so the states that must win are written here, not in the object.
 */

/* `container` has to stay an @apply, because written into the class attribute it would also match the page gutter rule. */
.settings-view .vertical-layout {
  @apply container mx-auto;
}

.settings-view .control .input:not([type="checkbox"]) {
  @apply block w-full max-w-xl min-h-14 rounded-corner-extra-small border border-outline bg-surface px-4 text-on-surface;
}
.settings-view .control .input:not([type="checkbox"]):focus {
  @apply border-primary ring-1 ring-primary outline-none;
}

/* Labels: the control label has the `label` class; the enum array renderer prints bare labels that pass through no style key. */
.settings-view .label,
.settings-view label:not([class]) {
  @apply block px-2 pb-1 md-label-large text-on-surface-variant;
}

/* The caption beside a checkbox is a bare <p>, and read-only is a state the object cannot express. */
.settings-view .wrapper p {
  @apply px-2 md-body-medium text-on-surface;
}
.settings-view .wrapper input:not([type="checkbox"]):read-only {
  @apply bg-surface-container text-on-surface-variant;
}

/* vue3-tabs nests panels for the array and one-of renderers; each level needs a little air above it. */
.settings-view .tab-panel .tab-panel {
  @apply pt-4;
}
</style>
