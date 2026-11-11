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
import { useMediaQuery } from '@vueuse/core'
import { useSession } from '../store/session'
import { openLogin } from '../store/login'
import { useLoginErrorNotice } from '../components/header/loginErrors'
import DefaultHeader from '../components/header/DefaultHeader.vue'
import RailBrand from '../components/header/RailBrand.vue'
import HeaderHero from '../components/header/HeaderHero.vue'
import FileBrowserView from '../components/browser/FileBrowserView.vue'
import RepositoryOverview from '../components/overview/RepositoryOverview.vue'
import MdButton from '../components/md/MdButton.vue'
import MdNavigationBar from '../components/md/MdNavigationBar.vue'
import MdNavigationRail from '../components/md/MdNavigationRail.vue'
import PageFooter from '../components/footer/PageFooter.vue'
import AdministrationFrame from '../components/layout/AdministrationFrame.vue'
import { useSettingsDomains } from '../components/settings/useSettingsDomains'
import OverviewIcon from '../components/icons/OverviewIcon.vue'
import SearchIcon from '../components/icons/SearchIcon.vue'
import DashboardIcon from '../components/icons/DashboardIcon.vue'
import AnalyticsIcon from '../components/icons/AnalyticsIcon.vue'
import AccessIcon from '../components/icons/AccessIcon.vue'
import ConsoleIcon from '../components/icons/ConsoleIcon.vue'
import CleaningIcon from '../components/icons/CleaningIcon.vue'
import SettingsIcon from '../components/icons/SettingsIcon.vue'
import HistoryIcon from '../components/icons/HistoryIcon.vue'
import MoreIcon from '../components/icons/MoreIcon.vue'
import NotFoundView from '../components/util/NotFoundView.vue'
import { property } from '../helpers/vue-extensions'
import { routeAllowed } from '../store/nodeAccess'
import { BOTTOM_BAR_VARIABLE, bottomBarHeight } from '../helpers/bottom-bar'
import ViewLoading from '../components/util/ViewLoading.vue'
import ViewLoadError from '../components/util/ViewLoadError.vue'
import { supportBundleOffered, MORE_KEY, pageByKey, pageLabel, visiblePages, railGroups, barLayout, accessTabOf, adminGroups, currentTreeKey, administrationLocation, sideNavOf, contributedPageByRoute, contributedPageNode, contributedRailGroups, pluginPageRedirect, pluginEnabled, NEXUS_IMPORT_PLUGIN } from '../navigation/registry'
import { pluginUiReady, loadPluginUi, clearPluginUi } from '../store/pluginUi'
import { supportBundleReady, loadSupportBundle, clearSupportBundle } from '../store/supportBundle'
import PluginPageView from '../components/plugins/PluginPageView.vue'
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
const AnalyticsView = lazyView(() => import('../components/dashboard/AnalyticsView.vue'))
const AccessView = lazyView(() => import('../components/access/AccessView.vue'))
const SettingsView = lazyView(() => import('../components/settings/SettingsView.vue'))
const MigrationView = lazyView(() => import('../components/migration/MigrationView.vue'))
const SystemInfoView = lazyView(() => import('../components/support/SystemInfoView.vue'))
const SupportView = lazyView(() => import('../components/support/SupportView.vue'))
const AuditView = lazyView(() => import('../components/audit/AuditView.vue'))
const AccountView = lazyView(() => import('../components/account/AccountView.vue'))
const SearchView = lazyView(() => import('../components/search/SearchView.vue'))

// The icon of each section. The active one is drawn filled, the others outlined.
const SECTION_ICONS = {
  overview: OverviewIcon,
  search: SearchIcon,
  dashboard: DashboardIcon,
  analytics: AnalyticsIcon,
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

// The pages of the Administration tree that draw the tree as a side navigation. Access draws its own in AccessView.
// Migration is the System page of the tree; it exists only while the nexus-import plugin is enabled (see migrationMissing).
const ADMIN_SIDE_PAGES = ['audit', 'console', 'maintenance', 'analytics', 'migration']
// The support pages are children of Settings: they share its section, and draw the same tree as the ADMIN_SIDE_PAGES.
const SUPPORT_ROUTES = ['system-info', 'support']
// The one-line description each Administration page shows under the frame heading, by route name.
const ADMIN_DESCRIPTIONS = {
  audit: 'Deployments, deletions, settings changes, token changes and failed sign ins on this instance, newest first.',
  console: 'The log lines of this instance, filtered by level and text.',
  maintenance: 'Storage usage, the search index and cleanup for this Ingot instance.',
  analytics: 'Traffic and resource use of this instance over time.',
  'settings-migration': 'Import the repositories of another instance, step by step.',
  'system-info': 'What this instance runs on. Secrets are withheld or masked, and the page only reads.',
  support: 'What a maintainer needs to look at this instance, as one zip.',
}
// The pages that the Administration entry of the rail and the bar stands for.
const ADMINISTRATION_SECTIONS = ['settings', 'access', ...ADMIN_SIDE_PAGES]

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
// The entry of the rail and the bar that is current: every Administration page is under Administration.
const navigationSection = computed(() => (ADMINISTRATION_SECTIONS.includes(section.value) ? 'settings' : section.value))
// The administration pages draw their content in the administration frame, which has its own maximum width.
// The top bar takes the same width, so its title and the frame's content start at one x.
const framedPage = computed(() => ADMINISTRATION_SECTIONS.includes(section.value))
// A contributed page is judged by the area it names, not by the settings meta of its route. A page the manifest
// does not name has no node, so it is not allowed and the page below shows it as not found.
const pluginPageNode = computed(() => (route.name === 'settings-plugin'
  ? contributedPageNode(route.params.plugin, route.params.page)
  : undefined))
const sectionAllowed = computed(() => {
  if (route.name === 'settings-plugin') return pluginPageNode.value !== undefined && can(pluginPageNode.value)
  return routeAllowed(route.meta, can, isManager.value) && (!route.meta.signedIn || isLogged.value)
})
// The migration page exists only while the nexus-import plugin is enabled. Until the manifest is read, the page waits.
const migrationPending = computed(() => route.name === 'settings-migration' && isLogged.value && !pluginUiReady.value)
const migrationMissing = computed(() => route.name === 'settings-migration'
  && isLogged.value
  && pluginUiReady.value
  && !pluginEnabled(NEXUS_IMPORT_PLUGIN))
// The manifest of a plugin page is read once the session is known, so the page waits for it.
const pluginPageMissing = computed(() => route.name === 'settings-plugin'
  && isLogged.value
  && pluginUiReady.value
  && !pluginPageRedirect(route.params.plugin, route.params.page)
  && !contributedPageByRoute(route.params.plugin, route.params.page))
// An address without a page id opens the first page of its plugin, once the manifest says which that is.
watch([pluginUiReady, () => route.fullPath], () => {
  if (route.name !== 'settings-plugin' || !pluginUiReady.value) return
  const target = pluginPageRedirect(route.params.plugin, route.params.page)
  if (target) router.replace(target)
}, { immediate: true })
watch(isLogged, (logged) => (logged ? loadPluginUi() : clearPluginUi()), { immediate: true })
watch(isLogged, (logged) => (logged ? loadSupportBundle() : clearSupportBundle()), { immediate: true })

// The support pages are not found while the instance does not offer them (an opt-out instance), and wait for the answer.
const supportPageOpen = computed(() => SUPPORT_ROUTES.includes(route.name) && isLogged.value && sectionAllowed.value)
const supportPageWaiting = computed(() => supportPageOpen.value && !supportBundleReady.value)
const supportPageAbsent = computed(() => supportPageOpen.value && supportBundleReady.value && !supportBundleOffered.value)

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
// A signed in visitor without the permission stays at the address and sees the no access state.
const askForSignIn = () => {
  if (!sectionAllowed.value && !isLogged.value) openLogin()
}
onMounted(askForSignIn)
watch([sectionAllowed, isLogged], ([allowed, logged]) => {
  if (!allowed && !logged) openLogin()
})
const browsedPath = computed(() => qualifierOf(route.params))

// One section on its own needs no navigation: the bar and the rail are left out, and the
// page keeps its full width.
const menuSections = computed(() => visiblePages(can))
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
// Administration opens the hub, or the one Administration page a visitor may open (see administrationLocation).
const locationOf = (key) => {
  if (key === 'overview') return browseLocation(browsedPath.value)
  if (key === 'settings') return administrationLocation(can)
  return { name: pageByKey(key).route }
}

// The bar and the rail share these destinations. Each one is a real link, so it can be
// opened in a new tab and the browser's history works.
const destinationOf = (entry) => ({
  key: entry.key,
  label: entry.label,
  href: router.resolve(locationOf(entry.key)).href,
})

// The rail draws the destinations in their groups; `group` is the name the rail labels a block with.
// The contributed pages follow the built-in ones, in the group each names. They are never in the bottom bar.
const contributedLocationOf = (page) => ({ name: 'settings-plugin', params: { plugin: page.plugin, page: page.id } })
const railItems = computed(() => [
  ...railGroups(can).flatMap(group =>
    group.pages.map(entry => ({ ...destinationOf(entry), group: group.heading }))),
  ...contributedRailGroups(can).flatMap(group => group.pages.map(page => ({
    key: page.key,
    label: page.title,
    href: router.resolve(contributedLocationOf(page)).href,
    group: group.heading,
  }))),
])

const selectSection = (key) => {
  const page = contributedRailGroups(can).flatMap(group => group.pages).find(entry => entry.key === key)
  router.push(page ? contributedLocationOf(page) : locationOf(key))
}

// The bottom bar holds the first four permitted sections. When some are left over they sit under a More
// item, which opens a small panel above the bar under the group headings, and it stays active while one of
// its sections is on screen.
const layout = computed(() => barLayout(can))
const moreGroups = computed(() => layout.value.more.map(group => ({
  heading: group.heading,
  items: group.pages.map(destinationOf),
})))
const moreKeys = computed(() => moreGroups.value.flatMap(group => group.items.map(item => item.key)))
const barItems = computed(() => [
  ...layout.value.bar.map(destinationOf),
  ...(moreKeys.value.length > 0 ? [{ key: MORE_KEY, label: 'More' }] : []),
])
const barSection = computed(() => (moreKeys.value.includes(navigationSection.value) ? MORE_KEY : navigationSection.value))
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

// The Administration pages other than Access and Settings draw the Administration tree beside the page. Below 1240px
// the tree is an index instead of a column; see SideNavLayout. The settings domains are known only inside Settings,
// so the tree here lists the static pages.
const narrow = useMediaQuery('(max-width: 1239px)')
const adminSideNav = computed(() => {
  if (ADMIN_SIDE_PAGES.includes(section.value)) return { ...sideNavOf(section.value), description: ADMIN_DESCRIPTIONS[route.name] }
  return SUPPORT_ROUTES.includes(route.name) ? { ...sideNavOf('settings'), description: ADMIN_DESCRIPTIONS[route.name] } : null
})
// The tree is the same on every Administration page: the settings domains the visitor may open, requested only here.
const settings = useSettingsDomains(() => adminSideNav.value !== null)
const adminTree = computed(() => adminGroups(can, { domains: settings.domains.value, titleOf: settings.titleOf }))
// The entry of the tree on screen, from the route: the one page of the tree the address names.
const adminCurrent = computed(() => currentTreeKey(route.name))

// Access: the tab on screen (users, machines, teams, sso, ldap, security or check; none at /access, which is the index on a narrow screen) and the name of the principal or team that
// is open. The view moves between them through the router itself. The registry maps each route to its tab.
const accessTab = computed(() => accessTabOf(route.name))
const accessName = computed(() => (section.value === 'access' ? route.params.name : undefined))

// Console: the level switches and the text filter, as the views read and write them.
const consoleLevel = computed(() => optionalParam(route.query.level))
const consoleSearch = computed(() => textFromParam(route.query.q))
const setConsoleLevel = (value) => replaceQuery('level', value)
const setConsoleSearch = (text) => replaceQuery('q', textParam(text))

// Settings: the domain on screen. Changing it drops any entry that was open in the old one.
const setDomain = (domain) =>
  router.replace({ name: 'settings', params: { domain }, query: route.query })

const activeLabel = computed(() => pageLabel(section.value))
</script>

<template>
  <div
    class="bg-surface text-on-surface"
    :class="hasNavigation ? 'min-[600px]:pl-24 min-[600px]:bg-surface-container' : ''"
    data-ingot-component="index-page"
  >
    <!--
      Below 600px the bar sits at the bottom, so the footer block reserves its height and the
      device's safe area above the footer. From 600px the rail sits at the left and the page moves
      right by its 96px width, the header included. From 600px the frame (rail and top bar) is
      surface-container, and the content is a surface sheet with rounded corners where it meets
      them. The sheet is at least as tall as the window below the top bar, so its footer sits at
      the bottom of the window. From 1600px the sheet also rounds its top right corner and keeps
      a 16px gap to the window edge.
    -->
    <DefaultHeader :framed="framedPage" />
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
      <main
        id="main"
        tabindex="-1"
        :class="hasNavigation ? 'min-[600px]:flex min-[600px]:flex-1 min-[600px]:flex-col' : ''"
      >
        <NotFoundView v-if="route.name === 'not-found'" />
        <ViewLoading v-else-if="route.name === 'settings-plugin' && isLogged && !pluginUiReady" />
        <ViewLoading v-else-if="migrationPending" />
        <NotFoundView v-else-if="migrationMissing" />
        <NotFoundView v-else-if="pluginPageMissing" />
        <ViewLoading v-else-if="supportPageWaiting" />
        <NotFoundView v-else-if="supportPageAbsent" />
        <section
          v-else-if="!sectionAllowed && isLogged"
          data-test="no-access"
          class="container mx-auto flex flex-col items-start gap-4 py-12"
          aria-labelledby="no-access-title"
        >
          <h2
            id="no-access-title"
            class="md-headline-small text-on-surface"
          >
            {{ activeLabel }}
          </h2>
          <p class="md-body-large text-on-surface-variant">
            You do not have access to this page.
          </p>
          <RouterLink
            :to="{ name: 'overview' }"
            class="md-focus-ring md-label-large text-primary"
          >
            {{ pageLabel('overview') }}
          </RouterLink>
        </section>
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
        <PluginPageView
          v-else-if="route.name === 'settings-plugin'"
          :plugin="route.params.plugin"
          :page-id="route.params.page"
        />
        <SearchView v-else-if="section === 'search'" />
        <AccountView v-else-if="section === 'account'" />
        <DashboardView v-else-if="section === 'dashboard'" />
        <AccessView
          v-else-if="section === 'access'"
          :tab="accessTab"
          :name="accessName"
        />
        <AdministrationFrame
          v-else-if="adminSideNav"
          :description="adminSideNav.description"
          :label="adminSideNav.label"
          :section-label="adminSideNav.sectionLabel"
          :groups="adminTree"
          :current="adminCurrent"
          :index-to="adminSideNav.indexTo"
          :narrow="narrow"
        >
          <AnalyticsView v-if="section === 'analytics'" />
          <ConsoleView
            v-else-if="section === 'console'"
            :level="consoleLevel"
            :search="consoleSearch"
            @update:level="setConsoleLevel"
            @update:search="setConsoleSearch"
          />
          <MaintenanceView v-else-if="section === 'maintenance'" />
          <MigrationView
            v-else-if="section === 'migration'"
            :step="route.params.step"
          />
          <AuditView
            v-else-if="section === 'audit'"
            :query="route.query"
            @update:query="setAuditQuery"
          />
          <SystemInfoView v-else-if="route.name === 'system-info'" />
          <SupportView v-else-if="route.name === 'support'" />
        </AdministrationFrame>
        <SettingsView
          v-else-if="section === 'settings'"
          :selected-tab="activeLabel"
          :domain="route.params.domain"
          :collection="route.params.collection"
          :entry="route.params.entry"
          @update:domain="setDomain"
        />
      </main>
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
          <div
            v-for="group in moreGroups"
            :key="group.heading"
            role="group"
            :aria-label="group.heading"
          >
            <p
              class="md-label-medium px-4 py-2 text-on-surface-variant"
              aria-hidden="true"
            >
              {{ group.heading }}
            </p>
            <ul
              role="list"
              class="flex list-none flex-col gap-1"
            >
              <li
                v-for="item in group.items"
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
          </div>
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
      <div class="fixed inset-y-0 left-0 z-20 hidden w-24 min-[600px]:block">
        <MdNavigationRail
          class="h-full"
          :items="railItems"
          :model-value="navigationSection"
          @update:model-value="selectSection"
        >
          <template #top>
            <RailBrand />
          </template>
          <template #icon="{ item, active }">
            <component
              :is="SECTION_ICONS[item.key] ?? SECTION_ICONS.settings"
              :filled="active"
            />
          </template>
        </MdNavigationRail>
      </div>
    </template>
  </div>
</template>
