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
import { useRoute } from 'vue-router'
import { useSession } from '../store/session'
import DefaultHeader from '../components/header/DefaultHeader.vue'
import HeaderHero from '../components/header/HeaderHero.vue'
import FileBrowserView from '../components/browser/FileBrowserView.vue'
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

defineProps({
  qualifier: property(Object, true)
})

const route = useRoute()
const { isManager } = useSession()

// The section lives in the URL, so the back button and deep links work. A manager tab that
// is named but not visible to this user shows Overview instead. Each tab link keeps the
// repository path already in the URL, which is how the browsed path comes back.
const activeKey = computed(() => resolveTab(route.query.tab, isManager.value))
const activeLabel = computed(() => tabLabel(activeKey.value))
const menuTabs = computed(() => visibleTabs(isManager.value))

const tabTarget = (key) => ({ path: route.path, query: withTab(route.query, key) })
</script>

<template>
  <div class="bg-surface text-on-surface">
    <DefaultHeader />
    <!--
      Primary tabs in the Material Design 3 style. The row scrolls sideways when it runs out
      of room, which five tabs can do on a phone. Each tab is a real link, so it can be
      opened in a new tab and the browser's history works.
    -->
    <nav
      aria-label="Sections"
      class="border-b border-outline-variant"
    >
      <ul class="container mx-auto flex list-none overflow-x-auto">
        <li
          v-for="tab in menuTabs"
          :key="tab.key"
          class="shrink-0"
        >
          <router-link
            v-slot="{ href, navigate }"
            :to="tabTarget(tab.key)"
            custom
          >
            <a
              :href="href"
              :aria-current="tab.key === activeKey ? 'page' : undefined"
              class="md-state-layer md-focus-ring relative flex min-h-12 items-center rounded-corner-small px-4 md-label-large no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard"
              :class="tab.key === activeKey ? 'text-primary' : 'text-on-surface-variant'"
              @click="navigate"
            >
              {{ tab.label }}
              <span
                v-if="tab.key === activeKey"
                class="absolute inset-x-0 bottom-0 h-1 rounded-t-corner-extra-small bg-primary"
                aria-hidden="true"
              />
            </a>
          </router-link>
        </li>
      </ul>
    </nav>
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
  </div>
</template>
