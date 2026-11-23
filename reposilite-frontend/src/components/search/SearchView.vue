<!--
  - Copyright (c) 2026 OneLiteFeather
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

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { NavigationFailureType, RouterLink, isNavigationFailure, useRoute, useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import { ADMIN_NODES } from '../../store/nodeAccess'
import { errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdSkeleton from '../md/MdSkeleton.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import MdPage from '../md/MdPage.vue'
import MdSelect from '../md/MdSelect.vue'
import SearchBar from './SearchBar.vue'
import SearchFilters from './SearchFilters.vue'
import SearchResultRow from './SearchResultRow.vue'
import {
  activeFilters,
  changeQuery,
  lastPage,
  parseSearchQuery,
  resetFilters,
  toRequestQuery,
  toRouteQuery
} from './searchQuery'
import type { Order, SearchQuery, Sort } from './searchQuery'
import { announcement, indexNotice, normalizeFacetable, normalizeFiles, normalizePage, rowKey } from './searchResult'
import type { FacetableAttribute, Facets, FilesState, SearchItem, SearchPage } from './searchResult'

/*
 * The search page (spec section 8). The whole query lives in the address, so a search can be linked and
 * the back button steps through it: the request is built from the address and not from state of this
 * component, and every change to the query is a navigation. The only state the page keeps is what the
 * address cannot hold: the text being typed before Enter, which rows are open, and the files loaded for them.
 *
 * Answers are matched to the request that asked for them, so a slow answer never replaces a newer one.
 * A 404 means the instance has search turned off (spec section 6.4) and is said so, not shown as a failure.
 */
const route = useRoute()
const router = useRouter()
const { client, sessionReady, details, isLogged, can } = useSession()

const FILES_PAGE_SIZE = 50
const SORT_OPTIONS: { value: Sort; label: string }[] = [
  { value: 'groupId', label: 'Group id' },
  { value: 'artifactId', label: 'Artifact id' },
  { value: 'version', label: 'Version' },
  { value: 'modified', label: 'Last modified' },
  { value: 'size', label: 'Size' }
]
const ORDER_OPTIONS: { value: Order; label: string }[] = [
  { value: 'asc', label: 'Ascending' },
  { value: 'desc', label: 'Descending' }
]
const EMPTY_FACETS: Facets = { repository: [], extension: [], classifier: [], origin: [], snapshot: [] }
const SKELETON_ROWS = 5
// The problem types of an attribute request that the backend cannot answer (search attribute facets, D1 and D6).
const ATTRIBUTE_REFUSALS = ['urn:ingot:problem:unsupported-search-capability', 'urn:ingot:problem:undeclared-attribute']

// The HTTP answer of a failed request, when the error carries one.
interface HttpResponse {
  status?: number
  data?: { message?: string; type?: string }
}
const responseOf = (error: unknown): HttpResponse | undefined =>
  (typeof error === 'object' && error !== null && 'response' in error ? (error as { response?: HttpResponse }).response : undefined)

const query = computed(() => parseSearchQuery(route.query))
// The keys the instance can count, read once before the first search. When they cannot be read, or the backend
// refused attributes, the search is the plain one.
const facetable = ref<FacetableAttribute[]>([])
const facetableKnown = ref(false)
const attributesRefused = ref(false)
const requestQuery = computed(() => toRequestQuery({ ...query.value, attribute: attributesRefused.value ? [] : query.value.attribute }))

const result = ref<SearchPage | null>(null)
const loading = ref(true)
const failure = ref('')
const disabled = ref(false)
const announced = ref('')
const draft = ref(query.value.q)
const sheetOpen = ref(false)
const activeRow = ref(0)
const expanded = ref<Record<string, boolean>>({})
const filesByKey = ref<Record<string, FilesState>>({})
const bar = ref<InstanceType<typeof SearchBar> | null>(null)
const list = ref<InstanceType<typeof MdCard> | null>(null)
const filterButton = ref<InstanceType<typeof MdButton> | null>(null)
const filtersTitleId = useId()
let latestRequest = 0

const items = computed(() => result.value?.items ?? [])
const facets = computed(() => result.value?.facets ?? EMPTY_FACETS)
const attributeFacets = computed(() => (attributesRefused.value ? {} : result.value?.attributes ?? {}))
const notice = computed(() => (result.value ? indexNotice(result.value.indexStatus) : null))
const canOpenMaintenance = computed(() => can(ADMIN_NODES.maintenance))
const filterWords = computed(() => activeFilters(query.value))
const filterCount = computed(() => activeFilters({ ...query.value, q: '' }).length)
const finalPage = computed(() => lastPage(result.value?.total ?? 0, query.value.size))
const countText = computed(() => (result.value ? announcement(result.value) : ''))
const outOfRange = computed(() => result.value !== null && result.value.total > 0 && items.value.length === 0)
const onlyMirrors = computed(() => query.value.origin === 'mirror')
const emptyMessage = computed(() => (query.value.q ? `Nothing matches "${query.value.q}".` : 'Nothing matches.'))

const loadFacetable = async () => {
  try {
    const { data } = await client.value.maven.facetableAttributes()
    facetable.value = normalizeFacetable(data)
  } catch {
    facetable.value = []
  } finally {
    facetableKnown.value = true
  }
}

/*
 * Asks for the page the address names, once the session is known and again when it changes, because a
 * sign in can show more repositories. A response that arrives after a newer request has started is dropped.
 */
const load = async () => {
  if (!sessionReady.value) return
  const current = ++latestRequest
  loading.value = true
  failure.value = ''
  if (!facetableKnown.value) await loadFacetable()
  if (current !== latestRequest) return
  try {
    const facetKeys = attributesRefused.value ? [] : facetable.value.map(entry => entry.key)
    const { data } = await client.value.maven.search(toRequestQuery({ ...query.value, attribute: attributesRefused.value ? [] : query.value.attribute }, facetKeys))
    if (current !== latestRequest) return
    disabled.value = false
    result.value = normalizePage(data)
    announced.value = announcement(result.value)
    expanded.value = {}
    filesByKey.value = {}
    activeRow.value = 0
  } catch (error) {
    if (current !== latestRequest) return
    const response = responseOf(error)
    if (response?.status === 404) {
      disabled.value = true
    } else if (response?.status === 400 && ATTRIBUTE_REFUSALS.includes(response.data?.type ?? '') && !attributesRefused.value) {
      // The plain search: the attributes leave the address, and the request is made again without them.
      attributesRefused.value = true
      await router.replace({ name: route.name, query: toRouteQuery({ ...query.value, attribute: [] }) })
    } else {
      const detail = response?.status === 400 ? response.data?.message : ''
      failure.value = detail || `Cannot search: ${errorMessage(error)}`
    }
  } finally {
    if (current === latestRequest) loading.value = false
  }
}

watch(() => [requestQuery.value, attributesRefused.value, sessionReady.value, details.value], load, { immediate: true })

// Back and forward change the address; the box follows it.
watch(() => query.value.q, (text) => {
  draft.value = text
})

// Every change is a navigation. A change that leaves the address as it was runs the search again.
const go = async (changes: Partial<SearchQuery>) => {
  const next = toRouteQuery(changeQuery(query.value, changes))
  const outcome = await router.push({ name: route.name, query: next })
  if (isNavigationFailure(outcome, NavigationFailureType.duplicated)) await load()
}

const submit = () => go({ q: draft.value.trim() })
const reset = () => go(resetFilters(query.value))
const resetEverything = () => go({ ...resetFilters(query.value), q: '' })
const changeFilter = (changes: Partial<SearchQuery>) => go(changes)

const toggleSheet = async (open: boolean) => {
  sheetOpen.value = open
  if (!open) {
    await nextTick()
    filterButton.value?.$el?.focus?.()
  }
}

// The keys of the search box (spec section 8.2).
const rowToggles = () => [...((list.value?.$el as HTMLElement | undefined)?.querySelectorAll<HTMLElement>('[data-row-toggle]') ?? [])]

const focusRow = async (index: number) => {
  await nextTick()
  rowToggles()[index]?.focus()
}

const onBoxKey = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    draft.value = ''
    bar.value?.blur()
    event.preventDefault()
  } else if (event.key === 'ArrowDown' && items.value.length > 0) {
    event.preventDefault()
    activeRow.value = 0
    focusRow(0)
  } else if (event.key === 'ArrowUp' && items.value.length > 0) {
    event.preventDefault()
    activeRow.value = items.value.length - 1
    focusRow(items.value.length - 1)
  }
}

const moveFrom = (index: number, direction: 'next' | 'previous') => {
  if (direction === 'next') {
    const target = Math.min(index + 1, items.value.length - 1)
    activeRow.value = target
    focusRow(target)
  } else if (index === 0) {
    bar.value?.focus()
  } else {
    activeRow.value = index - 1
    focusRow(index - 1)
  }
}

// "/" focuses the search box from anywhere but a field that takes text.
const onDocumentKey = (event: KeyboardEvent) => {
  if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey) return
  const target = event.target
  const typing = target instanceof HTMLElement
    && (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable)
  if (typing) return
  event.preventDefault()
  bar.value?.focus()
}

onMounted(() => document.addEventListener('keydown', onDocumentKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onDocumentKey))

// The files of a row are asked for when it opens, and again for the next page on request.
const loadFiles = async (item: SearchItem, page = 0) => {
  const key = rowKey(item)
  const generation = latestRequest
  const previous = page > 0 ? filesByKey.value[key] : undefined
  const earlier = previous?.items ?? []
  filesByKey.value = { ...filesByKey.value, [key]: { status: 'loading', items: earlier, total: previous?.total ?? 0, page } }
  try {
    const { data } = await client.value.maven.searchFiles({
      groupId: item.groupId,
      artifactId: item.artifactId,
      version: item.version,
      origin: query.value.origin,
      repository: query.value.repository,
      page,
      size: FILES_PAGE_SIZE
    })
    if (generation !== latestRequest) return
    const files = normalizeFiles(data)
    filesByKey.value = { ...filesByKey.value, [key]: { status: 'ready', items: [...earlier, ...files.items], total: files.total, page } }
  } catch (error) {
    if (generation !== latestRequest) return
    filesByKey.value = {
      ...filesByKey.value,
      [key]: { status: 'error', items: earlier, total: previous?.total ?? 0, page, message: `Cannot load the files: ${errorMessage(error)}` }
    }
  }
}

const toggleRow = (item: SearchItem) => {
  const key = rowKey(item)
  if (expanded.value[key]) {
    const rest = { ...expanded.value }
    delete rest[key]
    expanded.value = rest
    return
  }
  expanded.value = { ...expanded.value, [key]: true }
  if (!filesByKey.value[key]) loadFiles(item)
}
</script>

<template>
  <MdPage spaced>
    <div data-ingot-component="search-view">
      <h1 class="md-headline-small text-on-surface">
        Search
      </h1>

      <p
        v-if="disabled"
        role="status"
        class="mt-4 md-body-large text-on-surface-variant"
        data-test="disabled"
      >
        Search is turned off on this instance.
      </p>

      <template v-else>
        <SearchBar
          ref="bar"
          v-model="draft"
          class="mt-4"
          label="Search by group, artifact or version"
          @submit="submit"
          @keydown="onBoxKey"
        />

        <div
          v-if="notice"
          role="status"
          class="mt-4 rounded-corner-medium px-4 py-3 md-body-medium"
          :class="notice.kind === 'degraded'
            ? 'text-on-surface-variant'
            : 'bg-secondary-container text-on-secondary-container'"
          :data-test="`notice-${notice.kind}`"
        >
          {{ notice.text }}
          <RouterLink
            v-if="notice.kind === 'missing' && canOpenMaintenance"
            :to="{ name: 'maintenance' }"
            class="md-focus-ring rounded-corner-extra-small text-primary underline"
            data-test="maintenance-link"
          >
            Open maintenance
          </RouterLink>
        </div>

        <p
          v-if="!isLogged"
          class="mt-4 md-body-medium text-on-surface-variant"
          data-test="anonymous"
        >
          Some repositories are hidden from visitors who are not signed in. Sign in to see more results.
        </p>

        <div class="mt-6 grid grid-cols-[minmax(0,1fr)] gap-6 expanded:grid-cols-[minmax(240px,300px)_minmax(0,1fr)] expanded:items-start">
          <!-- Below 1024px the filters are a modal sheet; from there on they are a column beside the results. -->
          <div
            :class="sheetOpen ? 'fixed inset-0 z-40 flex flex-col bg-surface expanded:static expanded:z-auto expanded:bg-transparent' : 'hidden expanded:block'"
            :role="sheetOpen ? 'dialog' : undefined"
            :aria-modal="sheetOpen ? 'true' : undefined"
            :aria-labelledby="filtersTitleId"
            data-test="filter-panel"
            @keydown.esc="toggleSheet(false)"
          >
            <div class="flex items-center justify-between px-4 pt-4 expanded:hidden">
              <h2
                :id="filtersTitleId"
                class="md-title-large text-on-surface"
              >
                Filters
              </h2>
              <MdButton
                variant="text"
                data-test="close-filters"
                @click="toggleSheet(false)"
              >
                Close
              </MdButton>
            </div>
            <h2
              v-if="!sheetOpen"
              :id="filtersTitleId"
              class="sr-only"
            >
              Filters
            </h2>
            <div class="flex-1 overflow-y-auto p-4 expanded:overflow-visible expanded:p-0">
              <SearchFilters
                :query="query"
                :facets="facets"
                :attributes="attributeFacets"
                :attribute-labels="facetable"
                @change="changeFilter"
                @reset="reset"
              />
            </div>
            <div class="p-4 expanded:hidden">
              <MdButton
                variant="filled"
                class="w-full"
                @click="toggleSheet(false)"
              >
                Show results
              </MdButton>
            </div>
          </div>

          <section
            aria-labelledby="search-results-title"
            :aria-busy="loading ? 'true' : 'false'"
            class="min-w-0"
          >
            <h2
              id="search-results-title"
              class="sr-only"
            >
              Results
            </h2>
            <!-- Announces the count once an answer is in, and stays quiet while one is on its way. -->
            <p
              class="sr-only"
              aria-live="polite"
              data-test="live-region"
            >
              {{ announced }}
            </p>

            <div class="flex flex-wrap items-end gap-3">
              <MdButton
                ref="filterButton"
                variant="outlined"
                class="expanded:hidden"
                aria-haspopup="dialog"
                :aria-expanded="String(sheetOpen)"
                data-test="open-filters"
                @click="toggleSheet(true)"
              >
                Filters{{ filterCount > 0 ? ` (${filterCount})` : '' }}
              </MdButton>
              <p
                v-if="result && !loading"
                class="m-0 mr-auto md-title-medium text-on-surface"
                data-test="count"
              >
                {{ countText }}
              </p>
              <div class="w-44">
                <MdSelect
                  label="Sort by"
                  :options="SORT_OPTIONS"
                  :model-value="query.sort"
                  data-test="sort"
                  @update:model-value="go({ sort: $event })"
                />
              </div>
              <div class="w-40">
                <MdSelect
                  label="Order"
                  :options="ORDER_OPTIONS"
                  :model-value="query.order"
                  data-test="order"
                  @update:model-value="go({ order: $event })"
                />
              </div>
            </div>

            <MdCard
              v-if="loading"
              as="ul"
              variant="low"
              padding="none"
              data-ingot-state="loading"
              class="mt-4 m-0 list-none overflow-hidden"
              data-test="skeleton"
              aria-hidden="true"
            >
              <li
                v-for="row in SKELETON_ROWS"
                :key="row"
                class="border-b border-outline-variant px-4 py-4 last:border-b-0"
              >
                <div class="flex flex-col gap-3">
                  <MdSkeleton width="md" />
                  <MdSkeleton width="sm" />
                </div>
              </li>
            </MdCard>

            <MdStatePanel
              v-else-if="failure"
              state="error"
              class="mt-4"
              :description="failure"
              data-test="failure"
            >
              <template #actions>
                <MdButton
                  variant="tonal"
                  data-test="retry"
                  @click="load"
                >
                  Retry
                </MdButton>
              </template>
            </MdStatePanel>

            <div
              v-else-if="outOfRange"
              class="mt-4 flex flex-col items-start gap-3"
              data-test="out-of-range"
            >
              <p class="m-0 md-body-large text-on-surface-variant">
                There are no results on this page.
              </p>
              <MdButton
                variant="tonal"
                @click="go({ page: 0 })"
              >
                Back to the first page
              </MdButton>
            </div>

            <div
              v-else-if="result && items.length === 0"
              class="mt-4 flex flex-col items-start gap-3"
              data-test="empty"
            >
              <MdStatePanel
                state="empty"
                class="w-full px-0"
                :description="emptyMessage"
              />
              <p
                v-if="filterWords.length > 0"
                class="m-0 md-body-medium text-on-surface-variant"
                data-test="empty-filters"
              >
                Active filters: {{ filterWords.join(', ') }}.
              </p>
              <p
                v-if="onlyMirrors"
                class="m-0 md-body-medium text-on-surface-variant"
                data-test="empty-mirror"
              >
                Search shows what this instance holds. A proxy repository lists an artifact after it has fetched it once.
              </p>
              <MdButton
                v-if="filterWords.length > 0"
                variant="tonal"
                data-test="reset-all"
                @click="resetEverything"
              >
                Reset filters
              </MdButton>
            </div>

            <template v-else-if="result">
              <MdCard
                ref="list"
                as="ul"
                variant="low"
                padding="none"
                class="mt-4 m-0 list-none overflow-hidden"
                data-test="results"
              >
                <SearchResultRow
                  v-for="(item, index) in items"
                  :key="rowKey(item)"
                  :item="item"
                  :expanded="Boolean(expanded[rowKey(item)])"
                  :tabbable="index === activeRow"
                  :files="filesByKey[rowKey(item)]"
                  @toggle="toggleRow(item)"
                  @focus-row="activeRow = index"
                  @move="moveFrom(index, $event)"
                  @load-files="loadFiles(item, (filesByKey[rowKey(item)]?.page ?? 0) + 1)"
                  @retry-files="loadFiles(item, filesByKey[rowKey(item)]?.page ?? 0)"
                />
              </MdCard>
              <nav
                v-if="finalPage > 0 || query.page > 0"
                aria-label="Pagination"
                class="mt-4 flex items-center justify-between gap-3"
              >
                <MdButton
                  variant="outlined"
                  :disabled="query.page === 0"
                  data-test="previous"
                  @click="go({ page: query.page - 1 })"
                >
                  Previous
                </MdButton>
                <span
                  class="md-body-medium text-on-surface-variant"
                  data-test="page-position"
                >
                  Page {{ query.page + 1 }}{{ result.totalCapped ? '' : ` of ${finalPage + 1}` }}
                </span>
                <MdButton
                  variant="outlined"
                  :disabled="query.page >= finalPage"
                  data-test="next"
                  @click="go({ page: query.page + 1 })"
                >
                  Next
                </MdButton>
              </nav>
            </template>
          </section>
        </div>
      </template>
    </div>
  </MdPage>
</template>
