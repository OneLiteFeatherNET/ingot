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
import MdNavigationBar from '../components/md/MdNavigationBar.vue'
import MdNavigationRail from '../components/md/MdNavigationRail.vue'
import OverviewIcon from '../components/icons/OverviewIcon.vue'
import DashboardIcon from '../components/icons/DashboardIcon.vue'
import TokensIcon from '../components/icons/TokensIcon.vue'
import ConsoleIcon from '../components/icons/ConsoleIcon.vue'
import SettingsIcon from '../components/icons/SettingsIcon.vue'
import { property } from '../helpers/vue-extensions'
import ViewLoading from '../components/util/ViewLoading.vue'
import ViewLoadError from '../components/util/ViewLoadError.vue'
import { visibleTabs, resolveTab, withTab, tabLabel } from './index-tabs'

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

// The icon of each section. The active one is drawn filled, the others outlined.
const SECTION_ICONS = {
  overview: OverviewIcon,
  dashboard: DashboardIcon,
  tokens: TokensIcon,
  console: ConsoleIcon,
  settings: SettingsIcon,
}

defineProps({
  qualifier: property(Object, true)
})

const route = useRoute()
const router = useRouter()
const { isManager } = useSession()

// The section lives in the URL, so the back button and deep links work. A manager tab that
// is named but not visible to this user shows Overview instead. Each section link keeps the
// repository path already in the URL, which is how the browsed path comes back.
const activeKey = computed(() => resolveTab(route.query.tab, isManager.value))
const activeLabel = computed(() => tabLabel(activeKey.value))
const menuTabs = computed(() => visibleTabs(isManager.value))

// One section on its own needs no navigation: the bar and the rail are left out, and the
// page keeps its full width.
const hasNavigation = computed(() => menuTabs.value.length > 1)

const tabTarget = (key) => ({ path: route.path, query: withTab(route.query, key) })

// The bar and the rail share these destinations. Each one is a real link, so it can be
// opened in a new tab and the browser's history works.
const destinations = computed(() => menuTabs.value.map(tab => ({
  key: tab.key,
  label: tab.label,
  href: router.resolve(tabTarget(tab.key)).href,
})))

const selectSection = (key) => router.push(tabTarget(key))
</script>

<template>
  <div
    class="bg-surface text-on-surface"
    :class="hasNavigation ? 'max-[600px]:pb-[calc(5rem+env(safe-area-inset-bottom))] min-[600px]:pl-20' : ''"
  >
    <!--
      Below 600px the bar sits at the bottom, so the page reserves its height and the device's
      safe area. From 600px the rail sits at the left and the page moves right by its 80px
      width, the header included.
    -->
    <DefaultHeader />
    <div class="overflow-auto">
      <template v-if="activeKey === 'overview'">
        <HeaderHero />
        <FileBrowserView :qualifier="qualifier" />
      </template>
      <DashboardView v-else-if="activeKey === 'dashboard'" />
      <TokensView
        v-else-if="activeKey === 'tokens'"
        :selected-tab="activeLabel"
      />
      <ConsoleView v-else-if="activeKey === 'console'" />
      <SettingsView
        v-else-if="activeKey === 'settings'"
        :selected-tab="activeLabel"
      />
    </div>
    <template v-if="hasNavigation">
      <div class="fixed inset-x-0 bottom-0 z-20 bg-surface-container pb-[env(safe-area-inset-bottom)] min-[600px]:hidden">
        <MdNavigationBar
          :items="destinations"
          :model-value="activeKey"
          @update:model-value="selectSection"
        >
          <template #icon="{ item, active }">
            <component
              :is="SECTION_ICONS[item.key]"
              :filled="active"
            />
          </template>
        </MdNavigationBar>
      </div>
      <div class="fixed inset-y-0 left-0 z-20 hidden w-20 min-[600px]:block">
        <MdNavigationRail
          class="h-full"
          :items="destinations"
          :model-value="activeKey"
          @update:model-value="selectSection"
        >
          <template #icon="{ item, active }">
            <component
              :is="SECTION_ICONS[item.key]"
              :filled="active"
            />
          </template>
        </MdNavigationRail>
      </div>
    </template>
  </div>
</template>
