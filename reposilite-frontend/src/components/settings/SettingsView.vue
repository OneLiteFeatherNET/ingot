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
import { useMediaQuery } from '@vueuse/core'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { useSession } from '../../store/session'
import { adminGroups, currentTreeKey, indexRebuildLinkOf, OVERVIEW_TREE_KEY, sectionTabsOf, settingsLabelOf, shortcutsOf, sideNavOf } from '../../navigation/registry'
import AppTabs from '../tabs/AppTabs.vue'
import { ADMIN_NODES } from '../../store/nodeAccess'
import AdministrationFrame from '../layout/AdministrationFrame.vue'
import AccessDomainCard from './AccessDomainCard.vue'
import SettingsAttention from './SettingsAttention.vue'
import SearchRebuildNote from './SearchRebuildNote.vue'
import SettingsHub from './SettingsHub.vue'
import DashboardView from '../dashboard/DashboardView.vue'
import { attentionRows, glanceRows, unsavedTreeKeys } from './settingsHub'
import { isAccessDomain, settingsTabDomains } from './accessDomains'
import { domainFormOf } from './domainForm'
import { activeTabKey, errorCountsOf, firstErrorTab, labelWithErrors, tabsOfLayout, validationErrorsOf } from './sectionTabs'
import CollectionEditor from './CollectionEditor.vue'
import UnsavedChangesBar from './UnsavedChangesBar.vue'
import RepositoriesView from '../repositories/RepositoriesView.vue'
import RepositoryDetail from '../repositories/RepositoryDetail.vue'
import { collectionsOf, entryAddresses, idText, validateNewId } from './collections'
import { barClearance } from './barClearance'
import { settingsByDomain } from './settingsExport'
import SettingsReviewSheet from './SettingsReviewSheet.vue'
import ConflictDialog from './ConflictDialog.vue'
import { diffSettings, revertChange as revertDraftChange } from './settingsDiff'
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
  savedSettings,
  conflict,
  conflictChanges,
  reviewConflict,
  reloadConflict,
  overwriteConflict,
  dismissConflict,
  saveError
} = store

// The domain on screen is the one the store holds. The address sets it, and the tabs set it;
// the watchers below keep the address and the store in step, so there is one place it lives.
// The tabs leave out the domains that are edited under Access. Opened by address, such a domain shows its card.
const tabDomains = computed(() => settingsTabDomains(domains.value))

const activeDomain = computed(() => {
  if (domains.value.includes(store.selectedDomain.value)) return store.selectedDomain.value
  return tabDomains.value[0] ?? domains.value[0]
})

// Below 1240px the side navigation is an index instead of a column beside the domain; see SideNavLayout.
const narrow = useMediaQuery('(max-width: 1239px)')

/*
 * The side navigation is the Administration tree, the same one every Administration page shows. Its settings domains
 * are the tabs of this page, and the repositories page is one of its links.
 */
const session = useSession()
const can = (node) => (typeof session.can === 'function' ? session.can(node) : true)
const sideNav = sideNavOf('settings')
// The hub is the one page of the section without an entry of the tree: its heading is the section, and this line says what it is.
const HUB_DESCRIPTION = 'Shared configuration for all instances.'
const navGroups = computed(() => adminGroups(can, {
  domains: tabDomains.value,
  titleOf: (domain) => settingsLabelOf(domain) ?? store.titleOf(domain),
  descriptionOf: (domain) => store.schemas.value?.[domain]?.description ?? '',
}))
// The Repositories page and one repository are their own pages, not a domain: they have no domain in the address.
const repositoriesPage = computed(() => route.name === 'settings-repositories')
const repositoryPage = computed(() => route.name === 'settings-repository')
const repositoriesRoute = computed(() => repositoriesPage.value || repositoryPage.value)
// One entry of a collection, at /settings/:domain/:collection/:entry, as the props name it. Its page names the entry in the
// frame heading, as a repository page does, and the list of the collection is its way back.
const entryPage = computed(() => props.collection !== undefined && props.entry !== undefined)
// The name a detail page gives the entry at an address: its id, or "Unnamed" and the noun of its collection while it has
// no usable id. The address itself when no entry has it.
const nameOfEntry = (domain, collectionKey, address) => {
  if (!domain) return address
  const list = collectionsFor(domain).find(collection => collection.key === collectionKey)
  const entries = configurations.value[domain]?.[collectionKey] ?? []
  const entry = entries[entryAddresses(entries, store.entryKeysOf(domain, collectionKey)).indexOf(address)]
  if (!list || !entry) return address
  return idText(entry) || `Unnamed ${list.noun}`
}
// The name of the item on a detail page: the repository or the entry it opens. Empty on every other page.
const detailTitle = computed(() => {
  if (repositoryPage.value) return nameOfEntry('maven', 'repositories', route.params.id)
  return entryPage.value ? nameOfEntry(activeDomain.value, props.collection, props.entry) : ''
})
// The entry of the tree on screen: the domain in the address, or the Repositories page. The hub marks none.
const settingsCurrent = computed(() => currentTreeKey(route.name, props.domain))

// The schema fields of a repository, which decide the columns and the controls the Repositories page shows.
const repositoryFields = computed(() => Object.keys(store.schemas.value.maven?.properties?.repositories?.items?.properties ?? {}))

// With no domain in the address and several to choose from, the page is the hub. With one domain the
// address is replaced with it, as the watcher below reports. The repositories pages have no domain at all.
const hubShown = computed(() => !props.domain && tabDomains.value.length > 1 && !repositoriesRoute.value)

const collectionsFor = (domain) => collectionsOf(store.schemas.value[domain])

const activeCollections = computed(() => (activeDomain.value && !isAccessDomain(activeDomain.value)
  ? collectionsFor(activeDomain.value)
  : []))

const entriesOf = (domain, collection) => configurations.value[domain]?.[collection.key] ?? []

const selectedIdFor = (collection) => (props.collection === collection.key ? (props.entry ?? null) : null)

/*
 * The fields of a domain that are not collections, as one generated form. Most domains have none
 * of these, and then no form is drawn for them.
 */
const domainForm = computed(() => {
  const domain = activeDomain.value
  if (!domain) return null
  return domainFormOf(store.schemas.value[domain], collectionsFor(domain).map(collection => collection.key))
})

/*
 * A domain that opts in (see sectionTabsOf) shows the sections of its form as tabs. Every tab draws the same domain data,
 * so one save and one Discard cover all of them. The errors of every section are counted, including the hidden ones, and
 * shown in the tab label.
 */
const tabbedDomain = computed(() => {
  const domain = activeDomain.value
  return Boolean(domain) && sectionTabsOf(domain) && !isAccessDomain(domain) && domainForm.value !== null
})
const sectionTabs = computed(() => (tabbedDomain.value ? tabsOfLayout(domainForm.value.layout, domainForm.value.schema) : []))
const sectionErrors = computed(() => {
  if (sectionTabs.value.length === 0) return {}
  const domain = activeDomain.value
  return errorCountsOf(sectionTabs.value, validationErrorsOf(store.configurationValidator, store.schemas.value[domain], configurations.value[domain]))
})
const sectionLabels = computed(() => sectionTabs.value.map(tab => ({
  key: tab.key,
  label: labelWithErrors(tab.label, sectionErrors.value[tab.key] ?? 0)
})))
const activeSection = computed(() => activeTabKey(sectionTabs.value, route.query.tab))
// The rebuild of the search index runs on Maintenance: the General section links there, beside what a rebuild does.
const indexRebuild = computed(() => (activeDomain.value === 'search' ? indexRebuildLinkOf(can) : undefined))
const activeSectionLayout = computed(() => sectionTabs.value.find(tab => tab.key === activeSection.value)?.layout)
const firstErrorSection = computed(() => firstErrorTab(sectionTabs.value, sectionErrors.value))

// The bar's button moves to the tab that holds the first error, where the error is fixed. Save stays off while the domain is invalid.
const showFirstError = () => {
  const key = firstErrorSection.value
  if (key === undefined) return
  router.replace({ path: route.path, query: { ...route.query, tab: key }, hash: route.hash })
}

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
  if (domain && domain !== requested && !hubShown.value && !repositoriesRoute.value) emit('update:domain', domain)
}, { immediate: true })

const selectEntry = (collection, id) => {
  router.push({ name: 'settings-entry', params: { domain: activeDomain.value, collection: collection.key, entry: id } })
}

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

const onDomainChange = (domain, event) => {
  store.setDomainData(domain, event.data)
  store.validateDomain(domain)
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

/* The page reads these two variables in its stylesheet: the space for the bar, and on phones the bottom navigation under it. */
const pageClearance = computed(() => ({
  '--settings-clearance': `${barClearance(barHeight.value, hasUnsavedChanges.value)}px`,
  '--settings-nav': hasUnsavedChanges.value ? 'var(--app-bottom-bar-height, 0px)' : '0px'
}))

const save = () => store.saveConfiguration()
const discard = () => store.discardChanges()

/*
 * The review lists the same changes the bar counts: the saved state against the draft, per domain.
 * Revert puts one change back to its saved value in the draft, and nothing reaches the server until Save.
 */
const reviewOpen = ref(false)
const changes = computed(() => diffSettings(
  settingsByDomain(domains.value, savedSettings.value),
  settingsByDomain(domains.value, configurations.value),
  store.schemas.value
))
const countOfChanges = computed(() => (changes.value.length > 0 ? changes.value.length : changeCount.value))

/*
 * A save refused with 412 opens the conflict dialog. Its Review opens the same sheet, but over the stored
 * settings: the sheet then lists the stored value against the draft, and nothing is saved from it.
 */
const conflictReviewOpen = ref(false)
const reviewingConflict = computed(() => conflictReviewOpen.value && conflict.value?.kind === 'settings')
const sheetOpen = computed(() => reviewOpen.value || reviewingConflict.value)
const sheetChanges = computed(() => (reviewingConflict.value ? conflictChanges.value : changes.value))

const openConflictReview = async () => {
  if (await reviewConflict()) conflictReviewOpen.value = true
}

/*
 * Overview lists what needs attention (the drafts, and a conflict) and the counts of the saved settings. Both read the
 * saved state or the draft's changes, and nothing is fetched for them. Editing is gated by one node, so a visitor either
 * may edit every domain or none. A session without a node check, as the tests provide, is taken to edit.
 */
const unsavedDomains = computed(() => [...new Set(changes.value.map(change => change.domain))])
// The tree marks the entries of the domains with a draft, on every Administration page of the settings section.
const unsavedKeys = computed(() => unsavedTreeKeys(unsavedDomains.value))
const treeLinks = computed(() => navGroups.value.flatMap(group => group.links))
const attentionList = computed(() => attentionRows(treeLinks.value, unsavedKeys.value))
const glanceList = computed(() => glanceRows({ links: treeLinks.value, schemas: store.schemas.value, saved: savedSettings.value }))
const canEdit = computed(() => can(ADMIN_NODES.settings))
const canDashboard = computed(() => can(ADMIN_NODES.dashboard))
// Overview is the page of /settings without a domain. Its content is for a visitor who may edit.
const overviewPage = computed(() => canEdit.value && settingsCurrent.value === OVERVIEW_TREE_KEY)
// The instance status is a section of Overview for the dashboard node. A visitor without the settings node sees only that.
const statusShown = computed(() => canDashboard.value && settingsCurrent.value === OVERVIEW_TREE_KEY)
// The description of Overview names the halves the visitor sees; the tree description covers the settings half alone.
const overviewDescription = computed(() => {
  if (canEdit.value && canDashboard.value) return 'Status of this instance and its shared configuration.'
  if (canDashboard.value) return 'Status of this instance.'
  return undefined
})
// Needs attention is drawn above the index below 1240 pixels only when it has something to say; wider, it always reads.
const attentionShown = computed(() => overviewPage.value && (!narrow.value || attentionList.value.length > 0 || Boolean(conflict.value)))

// The sheet is modal and covers the page, so it closes once there is nothing left in it to review.
watch(() => changes.value.length, (count) => {
  if (count === 0) reviewOpen.value = false
})

// Once the conflict is resolved or dismissed, its review has nothing left to show.
watch(conflict, (current) => {
  if (!current) conflictReviewOpen.value = false
})

const closeReview = () => {
  reviewOpen.value = false
  conflictReviewOpen.value = false
}

const revertChange = (change) => {
  const domain = change.domain
  store.setDomainData(domain, revertDraftChange(configurations.value[domain], savedSettings.value[domain], change))
  store.validateDomain(domain)
}

/*
 * Reverting in the conflict review takes the stored value of one field into the draft. The review then lists
 * what is left, so the user sees the draft come closer to the stored state.
 */
const useStoredChange = (change) => {
  const domain = change.domain
  store.setDomainData(domain, revertDraftChange(configurations.value[domain], conflict.value.server, change))
  store.validateDomain(domain)
}

const discardAll = () => {
  discard()
  closeReview()
}

// The sheet closes when the save went through, or when it was refused as a conflict: the dialog takes over then.
// Any other refusal keeps the changes on screen with the error.
const saveFromReview = async () => {
  const saved = await store.saveConfiguration()
  if (saved || conflict.value) reviewOpen.value = false
}

// Taking the stored settings drops the draft, as the dialog's reload does, so the review has nothing to compare.
const takeStoredSettings = async () => {
  if (await reloadConflict()) closeReview()
}

const onSheetRevert = (change) => (reviewingConflict.value ? useStoredChange(change) : revertChange(change))
const onSheetDiscard = () => (reviewingConflict.value ? takeStoredSettings() : discardAll())

/* JsonForms configuration */
const formsConfiguration = {
  showUnfocusedDescription: true
}

watch(
  () => props.selectedTab,
  (selectedTab, prev) => {
    /* Fetch configuration only when the user opens an Administration page that reads it (any page of the settings section) */
    // A visitor without the settings node makes no settings request: Overview shows only the status to that visitor.
    if (selectedTab && prev == undefined && domains.value.length == 0 && canEdit.value)
      store.fetchConfiguration()
  },
  { immediate: true }
)
</script>

<template>
  <AdministrationFrame
    class="settings-view"
    :style="pageClearance"
    :description="settingsCurrent === undefined ? HUB_DESCRIPTION : (settingsCurrent === OVERVIEW_TREE_KEY ? overviewDescription : undefined)"
    :title="detailTitle"
    :detail="repositoryPage || entryPage"
    :label="sideNav.label"
    :section-label="sideNav.sectionLabel"
    :groups="navGroups"
    :current="settingsCurrent"
    :index-to="sideNav.indexTo"
    :shortcuts="shortcutsOf(settingsCurrent, can)"
    :narrow="narrow"
    :unsaved="unsavedKeys"
    :overview="settingsCurrent === OVERVIEW_TREE_KEY"
    replace
  >
    <template
      v-if="attentionShown"
      #lead
    >
      <SettingsAttention
        :rows="attentionList"
        :conflict="conflict"
        @review="reviewOpen = true"
        @review-conflict="openConflictReview"
      />
    </template>

    <p
      v-if="loadError"
      role="alert"
      class="md-body-medium text-error pb-4"
    >
      {{ loadError }}
    </p>

    <DashboardView v-if="statusShown" />

    <template v-if="hubShown">
      <SettingsHub
        v-if="canEdit"
        :glance="glanceList"
      />
    </template>
    <template v-else-if="activeDomain && canEdit">
      <div>
        <h2
          v-if="tabDomains.length <= 1 && !isAccessDomain(activeDomain) && !repositoriesRoute"
          class="md-title-large text-on-surface"
        >
          {{ store.titleOf(activeDomain) }}
        </h2>

        <div
          :role="repositoryPage ? null : 'tabpanel'"
          class="mt-6 flex flex-col gap-8"
        >
          <RepositoriesView
            v-if="repositoriesPage"
            :schema-fields="repositoryFields"
          />
          <RepositoryDetail
            v-else-if="repositoryPage"
            :id="route.params.id"
          />
          <template v-else>
            <AccessDomainCard
              v-if="isAccessDomain(activeDomain)"
              :domain="activeDomain"
              :title="store.titleOf(activeDomain)"
            />
            <AppTabs
              v-if="tabbedDomain && configurations[activeDomain]"
              :key="`section-tabs:${activeDomain}`"
              :tabs="sectionLabels"
              :label="`${store.titleOf(activeDomain)} sections`"
            >
              <template #default="{ tab }">
                <SearchRebuildNote
                  v-if="tab === 'general' && indexRebuild"
                  :to="indexRebuild"
                />
                <JsonForms
                  v-if="tab === activeSection"
                  :key="`domain-fields:${activeDomain}:${tab}`"
                  :config="formsConfiguration"
                  :data="configurations[activeDomain]"
                  :schema="domainForm.schema"
                  :uischema="activeSectionLayout"
                  :renderers="renderers"
                  :ajv="store.configurationValidator"
                  @change="onDomainChange(activeDomain, $event)"
                />
              </template>
            </AppTabs>
            <JsonForms
              v-else-if="!isAccessDomain(activeDomain) && domainForm && configurations[activeDomain]"
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
              :renderers="renderers"
              :validator="store.configurationValidator"
              :server-error="saveError ?? ''"
              @select="selectEntry(list, $event)"
              @create="createEntry(list, $event)"
              @change="updateEntry(list, $event)"
              @delete="deleteEntry(list, $event)"
            />
          </template>
        </div>
      </div>
    </template>
    <p
      v-else-if="!loadError && canEdit"
      class="mt-6 min-h-12 md-body-large text-on-surface-variant"
    >
      Loading configuration
    </p>

    <div
      v-if="hasUnsavedChanges"
      ref="barElement"
      class="settings-save-bar sticky bottom-0 z-10 mt-6 max-medium:bottom-[calc(var(--app-bottom-bar-height,0px)+env(safe-area-inset-bottom,0px))]"
    >
      <UnsavedChangesBar
        :count="countOfChanges"
        :invalid="invalidDomains.length > 0"
        :saving="saving"
        :first-error-label="firstErrorSection === undefined ? '' : 'Show the first error'"
        @save="save"
        @discard="discard"
        @review="reviewOpen = true"
        @show-error="showFirstError"
      />
    </div>

    <SettingsReviewSheet
      :open="sheetOpen"
      :changes="sheetChanges"
      :invalid="invalidDomains.length > 0"
      :saving="saving"
      :conflict="reviewingConflict"
      @update:open="closeReview"
      @revert="onSheetRevert"
      @discard="onSheetDiscard"
      @save="saveFromReview"
    />

    <ConflictDialog
      v-if="conflict && !reviewingConflict"
      :conflict="conflict"
      :difference-count="conflict.reviewed ? conflictChanges.length : null"
      @review="openConflictReview"
      @reload="reloadConflict"
      @overwrite="overwriteConflict"
      @dismiss="dismissConflict"
    />
  </AdministrationFrame>
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

/*
 * The bottom padding keeps the last card clear of the unsaved-changes bar. On phones the bar sits above
 * the bottom navigation, so that height is added too; the variables are set only while the bar is shown.
 */
.settings-view {
  padding-bottom: calc(1rem + var(--settings-clearance, 0px));
}
@media (max-width: 599px) {
  .settings-view {
    padding-bottom: calc(1rem + var(--settings-clearance, 0px) + var(--settings-nav, 0px) + env(safe-area-inset-bottom, 0px));
  }
}
</style>
