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
import { computed, defineAsyncComponent, ref, watch, watchEffect, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../store/session'
import { openLogin } from '../store/login'
import { useLoginErrorNotice } from '../components/header/loginErrors'
import DefaultHeader from '../components/header/DefaultHeader.vue'
import HeaderHero from '../components/header/HeaderHero.vue'
import FileBrowserView from '../components/browser/FileBrowserView.vue'
import RepositoryOverview from '../components/overview/RepositoryOverview.vue'
import MdButton from '../components/md/MdButton.vue'
import MdNavigationBar from '../components/md/MdNavigationBar.vue'
import MdNavigationRail from '../components/md/MdNavigationRail.vue'
import PageFooter from '../components/footer/PageFooter.vue'
import OverviewIcon from '../components/icons/OverviewIcon.vue'
import DashboardIcon from '../components/icons/DashboardIcon.vue'
import AccessIcon from '../components/icons/AccessIcon.vue'
import ConsoleIcon from '../components/icons/ConsoleIcon.vue'
import CleaningIcon from '../components/icons/CleaningIcon.vue'
import SettingsIcon from '../components/icons/SettingsIcon.vue'
import HistoryIcon from '../components/icons/HistoryIcon.vue'
import MoreIcon from '../components/icons/MoreIcon.vue'
import NotFoundView from '../components/util/NotFoundView.vue'
import { property } from '../helpers/vue-extensions'
import { createInfoToastOnce } from '../helpers/toast'
import { routeAllowed } from '../store/nodeAccess'
import { NO_ACCESS_MESSAGE } from '../router/guard'
import { BOTTOM_BAR_VARIABLE, bottomBarHeight } from '../helpers/bottom-bar'
import ViewLoading from '../components/util/ViewLoading.vue'
import ViewLoadError from '../components/util/ViewLoadError.vue'
import { SECTIONS, MORE_KEY, visibleSections, sectionLabel, isOverflow } from './index-tabs'
import { qualifierOf, browseLocation } from '../router/paths'
import { textFromParam, textParam, optionalParam } from '../router/url-state'

// Each section is its own bundle. While it downloads the page shows a progress bar, and if
// the download fails it says so and offers a reload instead of an empty panel.
const lazyView = (loader) => defineAsyncComponent({
  loader,
  loadingComponent: ViewLoading,
  errorComponent: ViewLoadError,
})

const ConsoleView = lazyView(() => import('../components/console/ConsoleView.vue'))
const MaintenanceView = lazyView(() => import('../components/maintenance/MaintenanceView.vue'))
const DashboardView = lazyView(() => import('../components/dashboard/DashboardView.vue'))
const AccessView = lazyView(() => import('../components/access/AccessView.vue'))
const SettingsView = lazyView(() => import('../components/settings/SettingsView.vue'))
const AuditView = lazyView(() => import('../components/audit/AuditView.vue'))
const AccountView = lazyView(() => import('../components/account/AccountView.vue'))

// The icon of each section. The active one is drawn filled, the others outlined.
const SECTION_ICONS = {
  overview: OverviewIcon,
  dashboard: DashboardIcon,
  access: AccessIcon,
  console: ConsoleIcon,
  maintenance: CleaningIcon,
  audit: HistoryIcon,
  settings: SettingsIcon,
  [MORE_KEY]: MoreIcon,
}

defineProps({
  qualifier: property(Object, true)
})

const route = useRoute()
const router = useRouter()
const { isManager, can, isLogged, mustChangePassword } = useSession()
// A single sign on provider that failed sends the visitor back here with the reason in the address.
useLoginErrorNotice()

/*
 * Everything this page shows follows from the route. The section comes from the route's meta,
 * the repository path from its params, and the state of each view from its query or params.
 * The views report changes back through the router, so the address always says what is shown.
 */
const section = computed(() => route.meta.section ?? null)
const sectionAllowed = computed(() =>
  routeAllowed(route.meta, can, isManager.value) && (!route.meta.signedIn || isLogged.value))

// The server asks for a new password: the router closes every other page at navigation time, and
// this follows when the flag arrives later, with the session that has just been read or started.
const sendToAccount = () => {
  if (mustChangePassword?.value && route.name !== 'account') {
    router.replace({ name: 'account' })
  }
}
onMounted(sendToAccount)
watch(() => mustChangePassword?.value, sendToAccount)

// The sign in dialog lives in the header of this page, so it is asked for once the page is on
// screen. A request made earlier, while the router was still deciding, would reach nobody.
// A sign in that turns out not to grant the permission sends the visitor where the router
// would have: to overview, with the same notice.
const askForSignIn = () => {
  if (!sectionAllowed.value && !isLogged.value) openLogin()
}
onMounted(askForSignIn)
watch([sectionAllowed, isLogged], ([allowed, logged]) => {
  if (allowed) return
  if (logged) {
    createInfoToastOnce(NO_ACCESS_MESSAGE)
    router.replace({ name: 'overview' })
  } else {
    openLogin()
  }
})
const browsedPath = computed(() => qualifierOf(route.params))

// One section on its own needs no navigation: the bar and the rail are left out, and the
// page keeps its full width.
const menuSections = computed(() => visibleSections(can))
const hasNavigation = computed(() => menuSections.value.length > 1)

// The snackbar lives outside this page, so it learns the bar height from the document root.
// Below 600px it adds the value to its margin; from 600px it ignores it, as the rail is at the
// side. The variable goes away with the page.
watchEffect(() => {
  document.documentElement.style.setProperty(BOTTOM_BAR_VARIABLE, bottomBarHeight(hasNavigation.value))
})
onBeforeUnmount(() => {
  document.documentElement.style.removeProperty(BOTTOM_BAR_VARIABLE)
})

// Overview keeps the repository path being browsed, so moving between sections and back does
// not lose the place in the browser.
const locationOf = (key) =>
  key === 'overview'
    ? browseLocation(browsedPath.value)
    : { name: SECTIONS.find(entry => entry.key === key).route }

// The bar and the rail share these destinations. Each one is a real link, so it can be
// opened in a new tab and the browser's history works.
const destinations = computed(() => menuSections.value.map(entry => ({
  key: entry.key,
  label: entry.label,
  href: router.resolve(locationOf(entry.key)).href,
})))

const selectSection = (key) => router.push(locationOf(key))

// The bottom bar cannot hold every section, so the rarer ones sit under a More item. That item
// opens a small panel above the bar, and it stays active while one of its sections is on screen.
const overflowDestinations = computed(() => destinations.value.filter(item => isOverflow(item.key)))
const barItems = computed(() =>
  overflowDestinations.value.length === 0
    ? destinations.value
    : [
      ...destinations.value.filter(item => !isOverflow(item.key)),
      { key: MORE_KEY, label: 'More' },
    ]
)
const barSection = computed(() => (isOverflow(section.value) ? MORE_KEY : section.value))
const moreOpen = ref(false)

const selectBarItem = (key) => {
  if (key === MORE_KEY) {
    moreOpen.value = !moreOpen.value
    return
  }
  moreOpen.value = false
  selectSection(key)
}

// A plain left click goes through the router and closes the panel. A modified click is left to the
// browser, so the section can still open in a new tab.
const openOverflowItem = (event, key) => {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  selectBarItem(key)
}

watch(section, () => {
  moreOpen.value = false
})

// Audit: the filters live in the address, so the view reports every change through the router and
// the page keeps no copy of them.
const setAuditQuery = (query) =>
  router.replace({ name: route.name, params: route.params, query })

// Replaces the current entry with the same route and one query value, so a search or a
// switch does not add a history entry for every keystroke. An undefined value removes the
// parameter, which is how the views say that they are at their default.
const replaceQuery = (key, value) => {
  const next = { ...route.query }
  if (value === undefined) {
    delete next[key]
  } else {
    next[key] = value
  }
  return router.replace({ name: route.name, params: route.params, query: next })
}

// Access: the tab on screen (users, machines, teams, sso, ldap or check) and the name of the principal or team that
// is open. The view moves between them through the router itself.
const ACCESS_TABS = {
  'access-users': 'users',
  'access-user': 'users',
  'access-machines': 'machines',
  'access-machine': 'machines',
  'access-check': 'check',
  'access-teams': 'teams',
  'access-team': 'teams',
  'access-sso': 'sso',
  'access-ldap': 'ldap'
}
const accessTab = computed(() => ACCESS_TABS[route.name] ?? 'users')
const accessName = computed(() => (section.value === 'access' ? route.params.name : undefined))

// Console: the level switches and the text filter, as the views read and write them.
const consoleLevel = computed(() => optionalParam(route.query.level))
const consoleSearch = computed(() => textFromParam(route.query.q))
const setConsoleLevel = (value) => replaceQuery('level', value)
const setConsoleSearch = (text) => replaceQuery('q', textParam(text))

// Settings: the domain on screen. Changing it drops any entry that was open in the old one.
const setDomain = (domain) =>
  router.replace({ name: 'settings', params: { domain }, query: route.query })

const activeLabel = computed(() => sectionLabel(section.value))
</script>

<template>
  <div
    class="bg-surface text-on-surface"
    :class="hasNavigation ? 'min-[600px]:pl-20 min-[600px]:bg-surface-container' : ''"
  >
    <!--
      Below 600px the bar sits at the bottom, so the footer block reserves its height and the
      device's safe area above the footer. From 600px the rail sits at the left and the page moves
      right by its 80px width, the header included. From 600px the frame (rail and top bar) is
      surface-container, and the content is a surface sheet with rounded corners where it meets
      them. The sheet is at least as tall as the window below the top bar, so its footer sits at
      the bottom of the window. From 1600px the sheet also rounds its top right corner and keeps
      a 16px gap to the window edge.
    -->
    <DefaultHeader />
    <!--
      The corners stay put while the page scrolls: the sheet's own corner scrolls under the top
      bar with the rest of the page, so these two sticky pieces paint the same corners again at the
      bar's bottom edge. They are only in the frame's colour outside the arc, so they are invisible
      where the sheet is flat.
    -->
    <div
      v-if="hasNavigation"
      class="pointer-events-none sticky top-16 z-20 hidden h-0 min-[600px]:block"
      aria-hidden="true"
    >
      <span class="pane-corner pane-corner-start" />
      <span class="pane-corner pane-corner-end min-[1600px]:right-4" />
    </div>
    <!--
      Clips wide content at the sides without making this box a scroll container. overflow-auto
      would make it one, and then a sticky element inside it (the settings action bar) is pinned to
      a box that never scrolls, so it leaves the window. The rounded corners clip content too.
    -->
    <div
      class="overflow-x-clip bg-surface"
      :class="hasNavigation ? 'pane-sheet min-[600px]:flex min-[600px]:min-h-[calc(100dvh_-_4rem)] min-[600px]:flex-col min-[600px]:rounded-tl-corner-extra-large min-[1600px]:mr-4 min-[1600px]:rounded-tr-corner-extra-large' : ''"
    >
      <NotFoundView v-if="route.name === 'not-found'" />
      <section
        v-else-if="!sectionAllowed"
        class="container mx-auto flex flex-col items-start gap-4 py-12"
        aria-labelledby="sign-in-title"
      >
        <h2
          id="sign-in-title"
          class="md-headline-small text-on-surface"
        >
          Sign in to open {{ activeLabel }}
        </h2>
        <p
          v-if="route.meta.manager"
          class="md-body-large text-on-surface-variant"
        >
          This section needs a sign in with the right permission nodes. Sign in with an account
          or token that holds them, and you stay on this page.
        </p>
        <p
          v-else
          class="md-body-large text-on-surface-variant"
        >
          Sign in to see your profile, change your password and manage your tokens.
        </p>
        <MdButton
          variant="filled"
          @click="openLogin"
        >
          Sign in
        </MdButton>
      </section>
      <template v-else-if="section === 'overview'">
        <HeaderHero />
        <!-- The root lists the repositories; a browsed path keeps the folder listing. -->
        <RepositoryOverview
          v-if="browsedPath === ''"
          :qualifier="qualifier"
        />
        <FileBrowserView
          v-else
          :qualifier="qualifier"
        />
      </template>
      <AccountView v-else-if="section === 'account'" />
      <DashboardView v-else-if="section === 'dashboard'" />
      <AccessView
        v-else-if="section === 'access'"
        :tab="accessTab"
        :name="accessName"
      />
      <ConsoleView
        v-else-if="section === 'console'"
        :level="consoleLevel"
        :search="consoleSearch"
        @update:level="setConsoleLevel"
        @update:search="setConsoleSearch"
      />
      <MaintenanceView v-else-if="section === 'maintenance'" />
      <AuditView
        v-else-if="section === 'audit'"
        :query="route.query"
        @update:query="setAuditQuery"
      />
      <SettingsView
        v-else-if="section === 'settings'"
        :selected-tab="activeLabel"
        :domain="route.params.domain"
        :collection="route.params.collection"
        :entry="route.params.entry"
        @update:domain="setDomain"
      />
      <!-- From 600px the footer closes the sheet; below it, the footer below the page takes over. -->
      <PageFooter
        v-if="hasNavigation"
        class="mt-auto hidden min-[600px]:block"
      />
    </div>
    <PageFooter
      :class="hasNavigation ? 'max-[600px]:pt-[calc(5rem+env(safe-area-inset-bottom))] min-[600px]:hidden' : ''"
    />
    <template v-if="hasNavigation">
      <div
        v-if="moreOpen"
        class="fixed inset-x-3 bottom-[calc(5rem+env(safe-area-inset-bottom)+0.5rem)] z-30 rounded-corner-large bg-surface-container-high p-2 min-[600px]:hidden"
        @keydown.esc="moreOpen = false"
      >
        <nav aria-label="More sections">
          <ul
            role="list"
            class="flex list-none flex-col gap-1"
          >
            <li
              v-for="item in overflowDestinations"
              :key="item.key"
            >
              <a
                :href="item.href"
                :aria-current="section === item.key ? 'page' : undefined"
                class="md-state-layer md-focus-ring flex items-center gap-3 rounded-corner-medium px-4 py-3 no-underline"
                :class="section === item.key ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
                @click="openOverflowItem($event, item.key)"
              >
                <component
                  :is="SECTION_ICONS[item.key]"
                  :filled="section === item.key"
                />
                <span class="md-label-large">{{ item.label }}</span>
              </a>
            </li>
          </ul>
        </nav>
      </div>
      <div class="fixed inset-x-0 bottom-0 z-20 bg-surface-container pb-[env(safe-area-inset-bottom)] min-[600px]:hidden">
        <MdNavigationBar
          :items="barItems"
          :model-value="barSection"
          @update:model-value="selectBarItem"
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
          :model-value="section"
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
