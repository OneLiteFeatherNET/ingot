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
import {provide, ref, toRaw, watch} from 'vue'
import {JsonForms} from '@jsonforms/vue'
import {Tabs, Tab, TabPanels, TabPanel} from 'vue3-tabs'
import { useConfiguration } from '../../store/configuration'
import download from 'downloadjs'
import FactoryResetModal from './FactoryResetModal.vue'
import { property } from '../../helpers/vue-extensions'

const props = defineProps({
  selectedTab: property(String, true)
})

const {
  fetchConfiguration,
  updateConfiguration,
  renderers,
  configurationStyles,
  configurationValidator,
  domains,
  configurations,
  schemas,
  selectedDomain
} = useConfiguration()

/*
 * 'styles' is the injection key useStyles() of @dzikoysk/vue-vanilla looks up, so every
 * renderer below this component takes its class names from here.
 */
provide('styles', configurationStyles)

const isValid = ref(true)
const hasChanged = ref(false)

const executeIfValid = (callback) => { 
  if (isValid.value) callback() 
}

const updateFormsConfiguration = (domain, event) => {
  if (!hasChanged.value) {
    hasChanged.value = configurations.value[domain] != event.data
  }
  configurations.value[domain] = event.data
  isValid.value = event.errors.length == 0
  event.errors.forEach(error => {
    console.log(error)
  })
}

const reload = (action) => action().then(() => hasChanged.value = false)

watch(
  () => props.selectedTab,
  (selectedTab, prev) => {
    /* Fetch configuration only when user opens the configuration tab  */
    if (selectedTab === 'Settings' && prev == undefined && domains.value.length == 0)
      fetchConfiguration()
  },
  { immediate: true }
)

const downloadSettings = () => {
  download(
    JSON.stringify(toRaw(configurations.value)), 
    'shared.configuration.json', 
    'application/json'
  )
}

const factoryReset = () => {
  const emptyConfiguration = {}
  domains.value.forEach(domain => emptyConfiguration[domain] = {})
  configurations.value = emptyConfiguration
  updateConfiguration()
}

/* JsonForms configuration */
const formsConfiguration = {
  showUnfocusedDescription: true
}
</script>

<template>
  <div class="settings-view container mx-auto pt-7 px-15 pb-12">
    <div class="flex justify-between pb-3 flex-col">
      <div>
        <p>Modify configuration shared between all instances.</p>
        <p><strong>Remember</strong>: Configuration propagation can take up to 10 seconds on all your instances.</p>
      </div>
      <div id="configuration-state" class="flex flex-row pt-8">
        <button 
          @click.prevent="executeIfValid(downloadSettings)" 
          class="bg-gray-800 dark:bg-gray-600"
          :class="{ forbidden: !isValid }"
          v-if="!hasChanged"
        >
          Download as JSON
        </button>
        <FactoryResetModal :callback="factoryReset">
            <template v-slot:button>
                <button class="bg-gray-800 dark:bg-gray-600">Factory reset</button>
            </template>
        </FactoryResetModal>
        <button 
          @click.prevent="reload(updateConfiguration)"
          class="bg-gray-500 dark:bg-gray-800 cursor-not-allowed"
          :class="{ changed: hasChanged, forbidden: !isValid }"
          :disabled="!isValid || !hasChanged"
          v-if="hasChanged"
        >
          Update and reload
        </button>
        <button 
          @click.prevent="reload(fetchConfiguration)"
          class="bg-gray-500 dark:bg-gray-800 cursor-not-allowed"
          :class="{ changed: hasChanged }"
          :disabled="!isValid || !hasChanged"
        >
          Reset changes
        </button>
      </div>
    </div>
    <Tabs v-model="selectedDomain">
      <Tab v-for="domain in domains"
        class="item"
        :key="`config:${domain}`"
        :val="domain"
        :label="schemas[domain]?.title"
        :indicator="true"
      />
    </Tabs>
    <TabPanels v-model="selectedDomain">
      <TabPanel 
        v-for="domain in domains" 
        :val="domain" 
        :key="`config_tab:${domain}`" 
        class="border-1 rounded dark:border-gray-700 p-4"
      >
        <JsonForms
          v-if="configurations[domain]"
          :config="formsConfiguration"
          :data="configurations[domain]"
          :schema="schemas[domain]"
          :renderers="renderers"
          :ajv="configurationValidator"
          @change="updateFormsConfiguration(domain, $event)"
        />
      </TabPanel>
    </TabPanels>
  </div>
</template>

<!--suppress CssInvalidAtRule -->
<style scoped>
@reference "../../style.css";
#configuration-state button {
  @apply mx-2 rounded text-sm px-4 text-white py-2;
}
#configuration-state .changed {
  @apply bg-blue-700! cursor-pointer!;
}
#configuration-state .forbidden {
  @apply bg-gray-500! cursor-not-allowed!;
}
.item {
  @apply pb-1;
  @apply pt-1.5;
  @apply cursor-pointer;
  @apply text-gray-600 dark:text-gray-300;
  @apply bg-gray-100 dark:bg-black;
}
.tabs .item:hover {
  @apply bg-gray-150 dark:bg-gray-900;
  transition: background-color 0.5s;
}
</style>

<!--suppress CssInvalidAtRule -->
<style>
@reference "../../style.css";
/*
 * Everything the renderer styles object can carry now lives in src/store/configuration.js.
 * What is left below stays here because each rule selects on something no key of that
 * object names.
 */

/*
 * `container` has to stay an @apply. Written into the class attribute it would also match
 * the project's own `.container` rule from style.css, which adds the page gutter.
 */
.vertical-layout {
  @apply container mx-auto;
}
/*
 * A checkbox and a text field share one class name, styles.control.input, so the split
 * between the two only exists as an attribute selector.
 */
.control .input:not([type=checkbox]) {
  @apply text-sm h-9 px-4 text-black;
}
.control .input[type="checkbox"] {
  @apply h-5 w-5;
}
/*
 * `label` matches an element rather than a class, and the enum array renderer prints
 * labels that pass through no style key at all.
 */
.label, label {
  padding-bottom: 0.5em;
  padding-left: 0.45em;
  display: inline-block;
  font-weight: bold;
}
/*
 * Descendants of the control wrapper. The "Enabled" caption beside a checkbox is a bare
 * <p>; the width is deliberately tied to the wrapper, because the enum array renderer
 * emits its checkboxes outside of one; and `:read-only` is a state the object cannot
 * express. The last two rules also have to stay together: both are important, and an
 * important utility would outrank the more specific of the two instead of losing to it.
 */
.wrapper p {
  @apply px-2 text-sm;
}
.wrapper input {
  @apply w-1/2;
}
.wrapper input, .wrapper select {
  @apply dark:bg-gray-800! dark:text-white!;
}
.wrapper input:not([type=checkbox]):read-only {
  @apply bg-gray-200! dark:bg-gray-800! text-gray-500!;
}
/*
 * vue3-tabs wraps the array and one-of renderers, and none of its markup takes class names
 * from the styles object.
 */
.one-of-container .active, .tab-panel .array-list .tab-panel .array-list .active {
  @apply bg-gray-125 dark:bg-gray-900;
}
.one-of-container .tab-panel, .tab-panel .array-list .tab-panel .array-list .tab-panel {
  @apply bg-gray-125 dark:bg-gray-900;
  border-radius: 0.25rem;
  padding-left: 17px;
}
.tabs > div {
  @apply rounded-t-lg;
}
.settings-view .tab-panel {
  @apply h-full;
  @apply border rounded-md px-6 py-2 dark:border-gray-600;
}
</style>
