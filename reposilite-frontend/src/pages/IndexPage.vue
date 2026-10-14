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
import { computed, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../store/session'
import DefaultHeader from '../components/header/DefaultHeader.vue'
import HeaderHero from '../components/header/HeaderHero.vue'
import FileBrowserView from '../components/browser/FileBrowserView.vue'
import {Tabs, Tab, TabPanels, TabPanel} from 'vue3-tabs'
import { property } from '../helpers/vue-extensions'
import ViewLoading from '../components/util/ViewLoading.vue'
import ViewLoadError from '../components/util/ViewLoadError.vue'
import { TABS, visibleTabs, resolveTab, withTab, tabLabel } from './index-tabs'

// Each section is its own bundle. While it downloads the page shows a progress bar, and if
// the download fails it says so and offers a reload instead of an empty panel.
const lazyView = (loader) => defineAsyncComponent({
  loader,
  loadingComponent: ViewLoading,
  errorComponent: ViewLoadError,
})

const ConsoleView = lazyView(() => import('../components/console/ConsoleView.vue'))
const DashboardView = lazyView(() => import('../components/dashboard/DashboardView.vue'))
const TokensView = lazyView(() => import('../components/tokens/TokensView.vue'))
const SettingsView = lazyView(() => import('../components/settings/SettingsView.vue'))

defineProps({
  qualifier: property(Object, true)
})

const route = useRoute()
const router = useRouter()
const { isManager } = useSession()

// The section lives in the URL, so the back button and deep links work. A manager tab that
// is named but not visible to this user shows Overview instead. Switching to Overview keeps
// the repository path already in the URL, which is how the browsed path comes back.
const activeKey = computed(() => resolveTab(route.query.tab, isManager.value))

const selectTab = (label) => {
  const key = TABS.find(tab => tab.label === label)?.key
  if (key) {
    router.push({ path: route.path, query: withTab(route.query, key) })
  }
}

const menuTabs = computed(() =>
  visibleTabs(isManager.value).map(tab => tab.label)
)

const selectedTab = computed({
  get: () => tabLabel(activeKey.value),
  set: selectTab
})
</script>

<template>
  <div>
    <DefaultHeader />
    <div class="bg-gray-100 dark:bg-black overflow-y-visible">
      <!--
        The bar scrolls sideways when it runs out of room. Every tab used to be pinned to a
        quarter of the width on small screens, which five tabs cannot satisfy: the row wrapped
        onto a second line and the last label was cut off at the edge.
      -->
      <div class="tab-bar container mx-auto overflow-x-auto max-sm:px-0">
        <Tabs
          v-model="selectedTab"
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
            <template v-if="selectedTab == 'Overview'">
              <!-- The file browser below brings its own top padding, so this only needs its own. -->
              <HeaderHero class="pt-8" />
              <FileBrowserView :qualifier="qualifier" />
            </template>
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
