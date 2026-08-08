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
import { computed, ref, watchEffect, defineAsyncComponent } from 'vue'
import { useSession } from '../store/session'
import useQualifier from '../store/qualifier'
import DefaultHeader from '../components/header/DefaultHeader.vue'
import FileBrowserView from '../components/browser/FileBrowserView.vue'
import {Tabs, Tab, TabPanels, TabPanel} from 'vue3-tabs'
import { property } from '../helpers/vue-extensions'

const ConsoleView = defineAsyncComponent(() => import('../components/console/ConsoleView.vue'))
const DashboardView = defineAsyncComponent(() => import('../components/dashboard/DashboardView.vue'))
const TokensView = defineAsyncComponent(() => import('../components/tokens/TokensView.vue'))
const SettingsView = defineAsyncComponent(() => import('../components/settings/SettingsView.vue'))

defineProps({
  qualifier: property(Object, true)
})

const listOfTabs = [
  { name: 'Overview', manager: false },
  { name: 'Dashboard', manager: true },
  { name: 'Tokens', manager: true },
  { name: 'Console', manager: true },
  { name: 'Settings', manager: true },
]

const { isManager } = useSession()
const { redirectTo } = useQualifier()

const menuTabs = computed(() => {
  return listOfTabs
    .filter(entry => !entry?.manager || isManager.value)
    .map(entry => entry.name)
})

const selectedTab = ref(localStorage.getItem('selectedTab') || 'Overview')

watchEffect(() => {
  localStorage.setItem('selectedTab', selectedTab.value)
})

const createTabClick = (newTab) => {
  if (newTab == 'Overview') {
    redirectTo('/')
  }
}

const selectHomepage = () => 
  selectedTab.value = 'Overview'
</script>

<template>
  <div>
    <DefaultHeader :logoClickCallback="selectHomepage" />
    <div class="bg-gray-100 dark:bg-black overflow-y-visible">
      <!--
        The bar scrolls sideways when it runs out of room. Every tab used to be pinned to a
        quarter of the width on small screens, which five tabs cannot satisfy: the row wrapped
        onto a second line and the last label was cut off at the edge.
      -->
      <div class="tab-bar container mx-auto overflow-x-auto max-sm:px-0">
        <Tabs
          v-model="selectedTab"
          @update:modelValue="createTabClick"
        >
          <Tab
            v-for="(tab, i) in menuTabs"
            :key="`menu${i}`"
            class="item font-normal whitespace-nowrap"
            :val="tab"
            :label="tab"
            :indicator="true"
          />
        </Tabs>
      </div>
      <hr class="dark:border-gray-700">
      <div class="overflow-auto">
        <TabPanels v-model="selectedTab">
          <TabPanel :val="'Overview'">
            <FileBrowserView v-if="selectedTab == 'Overview'" :qualifier="qualifier" ref=""/>
          </TabPanel>
          <TabPanel :val="'Dashboard'" v-show="isManager">
            <DashboardView v-if="selectedTab == 'Dashboard'" />
          </TabPanel>
          <TabPanel :val="'Console'" v-show="isManager">
            <ConsoleView v-if="selectedTab == 'Console'" />
          </TabPanel>
          <TabPanel :val="'Tokens'" v-show="isManager">
            <TokensView v-if="selectedTab == 'Tokens'" :selectedTab="selectedTab" />
          </TabPanel>
           <TabPanel :val="'Settings'" v-show="isManager">
            <SettingsView v-if="selectedTab == 'Settings'" :selectedTab="selectedTab" />
          </TabPanel>
        </TabPanels>
      </div>
    </div>
  </div>
</template>

<style>
@reference "../style.css";
.tabs .tab {
  cursor: pointer;
  text-transform: capitalize;
}
.tabs .item:hover {
  @apply bg-gray-150 dark:bg-gray-900;
  transition: background-color 0.5s;
}
/*
 * vue3-tabs lets its row wrap. On a phone that turns the bar into two ragged lines, so the
 * row is held on one line and the wrapper around it scrolls instead. Two classes deep
 * because the rule it overrides is a scoped one from the library, which carries the weight
 * of a class and an attribute.
 */
.tab-bar > .tabs {
  flex-wrap: nowrap;
}
.tab-bar .item {
  flex-shrink: 0;
}
</style>

<style scoped>
@reference "../style.css";
.item {
  @apply px-1;
  @apply pb-1;
  @apply pt-1.5;
  @apply cursor-pointer;
  @apply text-gray-600 dark:text-gray-300;
  @apply bg-gray-100 dark:bg-black;
}
.selected {
  @apply border-b-2;
  @apply border-black dark:border-white;
  @apply text-black dark:text-white;
}
.tabs .item {
  border-top-left-radius: 10%;
  border-top-right-radius: 10%;
}
</style>
