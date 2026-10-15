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

<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import { property } from '../../helpers/vue-extensions'
import { errorMessage } from '../../helpers/toast'
import { browseLocation } from '../../router/paths'
import { tagsFromParam, tagsParam, textFromParam, textParam } from '../../router/url-state'
import { filterRepositories, tagUsage } from '../../store/maven/repositories'
import Card from '../card/SnippetsCard.vue'
import FileBrowserView from '../browser/FileBrowserView.vue'
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'

/*
 * The root of the file browser as a list of repositories. The list is loaded once from the overview
 * endpoint, and the search and the tag filter are applied to it on the page, so a keystroke never waits
 * for a request. The search text is `q` and the tags are the repeated `tag` parameter of the address,
 * and every change to them replaces the address, so the address always says what is shown.
 *
 * An older backend does not have the endpoint and answers 404; the folder listing is shown in its place.
 * Any other failure is shown with a retry.
 */
defineProps({
  qualifier: property(Object, true)
})

// How many of the most used tags are offered as filter chips. A selected tag is always offered too.
const SHOWN_TAGS = 8

// The visibility labels: an icon and a word, in the variant colour, with no fill. A public repository has none.
const BADGES = {
  PRIVATE: {
    label: 'Private',
    icon: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z'
  },
  HIDDEN: {
    label: 'Hidden',
    icon: 'M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z'
  }
}

const route = useRoute()
const router = useRouter()
const { client, sessionReady, details } = useSession()

const repositories = ref([])
const loading = ref(true)
const failure = ref('')
const unsupported = ref(false)
let latestRequest = 0

const normalise = (repository) => ({
  id: String(repository.id ?? ''),
  visibility: repository.visibility ?? '',
  description: repository.description ?? '',
  tags: Array.isArray(repository.tags) ? repository.tags : []
})

// A repository is one row whatever the answer says: the first entry for an id is kept, so the
// key of each row is unique and the count is the number of repositories.
const uniqueById = (list) => {
  const byId = new Map()
  for (const repository of list.map(normalise)) {
    if (!byId.has(repository.id)) byId.set(repository.id, repository)
  }
  return [...byId.values()]
}

/*
 * Asks for the list once the session is known, and again when it changes, because a sign in can show
 * more repositories. A response that arrives after a newer request has started is dropped.
 */
const load = async () => {
  if (!sessionReady.value) return
  const current = ++latestRequest
  loading.value = true
  failure.value = ''
  try {
    const { data } = await client.value.maven.repositories()
    if (current !== latestRequest) return
    repositories.value = uniqueById(data.repositories ?? [])
    unsupported.value = false
  } catch (error) {
    if (current !== latestRequest) return
    if (error?.response?.status === 404) {
      unsupported.value = true
    } else {
      failure.value = `Cannot load repositories: ${errorMessage(error)}`
    }
  } finally {
    if (current === latestRequest) loading.value = false
  }
}

watch(() => [sessionReady.value, details.value], load, { immediate: true })

const text = computed(() => textFromParam(route.query.q))
const selectedTags = computed(() => tagsFromParam(route.query.tag))
const filtersActive = computed(() => text.value.trim() !== '' || selectedTags.value.length > 0)
const matches = computed(() => filterRepositories(repositories.value, { text: text.value, tags: selectedTags.value }))

const shownTags = computed(() => {
  const used = tagUsage(repositories.value).slice(0, SHOWN_TAGS).map(entry => entry.tag)
  return [...used, ...selectedTags.value.filter(tag => !used.includes(tag))]
})

const countText = computed(() => (filtersActive.value
  ? `${matches.value.length} of ${repositories.value.length} repositories`
  : `${repositories.value.length} repositories`))

const badgeOf = (repository) => BADGES[repository.visibility] ?? null

// Replaces the current entry, so typing does not add a history entry for every letter. A value that
// is undefined leaves its parameter out of the address, which is the default.
const replaceQuery = (changes) => {
  const query = { ...route.query, ...changes }
  for (const key of Object.keys(query)) {
    if (query[key] === undefined) delete query[key]
  }
  return router.replace({ name: route.name, params: route.params, query })
}

const setText = (value) => replaceQuery({ q: textParam(value) })

const toggleTag = (tag) => {
  const tags = selectedTags.value.includes(tag)
    ? selectedTags.value.filter(selected => selected !== tag)
    : [...selectedTags.value, tag]
  return replaceQuery({ tag: tagsParam(tags) })
}

const clearFilters = () => replaceQuery({ q: undefined, tag: undefined })
</script>

<template>
  <FileBrowserView
    v-if="unsupported"
    :qualifier="qualifier"
  />
  <div
    v-else
    class="container mx-auto mb-12 grid min-h-[320px] grid-cols-[minmax(0,1fr)] gap-6 pt-7 lg:grid-cols-[minmax(0,1fr)_minmax(320px,400px)] lg:items-start"
  >
    <div class="min-w-0 lg:col-start-2 lg:row-start-1">
      <Card :qualifier="qualifier" />
    </div>
    <section
      class="min-w-0"
      aria-labelledby="overview-repositories-title"
    >
      <h2
        id="overview-repositories-title"
        class="md-title-large text-on-surface"
      >
        Repositories
      </h2>

      <form
        role="search"
        class="mt-4 flex flex-col gap-3"
        @submit.prevent
      >
        <!-- The M3 search bar: a 56px pill on the raised surface, without an outline. -->
        <div class="flex h-14 items-center gap-2 rounded-corner-full bg-surface-container-high pl-4 pr-2 text-on-surface has-[input:focus-visible]:outline-3 has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-secondary">
          <svg
            class="size-6 shrink-0 text-on-surface-variant"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1 0 9.5 16a6.5 6.5 0 0 0 4.2-1.6l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z" />
          </svg>
          <input
            type="search"
            class="md-embedded-input min-w-0 flex-1 md-body-large text-on-surface placeholder:text-on-surface-variant [&::-webkit-search-cancel-button]:hidden"
            aria-label="Search by id, description or tag"
            placeholder="Search by id, description or tag"
            autocomplete="off"
            :value="text"
            @input="setText($event.target.value)"
          >
          <button
            v-if="text !== ''"
            type="button"
            class="md-state-layer md-focus-ring md-touch-target relative inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant"
            aria-label="Clear search"
            @click="setText('')"
          >
            <svg
              class="size-6"
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
              focusable="false"
            >
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </button>
        </div>

        <div
          v-if="shownTags.length > 0"
          role="group"
          aria-label="Filter by tag"
          class="-mx-4 -mt-2 flex flex-nowrap gap-2 overflow-x-auto px-4 pt-2 pb-2 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          <MdChip
            v-for="tag in shownTags"
            :key="tag"
            variant="filter"
            data-test="filter-tag"
            :selected="selectedTags.includes(tag)"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </MdChip>
        </div>
      </form>

      <p
        v-if="!loading && !failure"
        role="status"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        {{ countText }}
      </p>

      <div
        v-if="loading"
        class="mt-4 md-body-large text-on-surface-variant"
      >
        Loading repositories
      </div>

      <div
        v-else-if="failure"
        class="mt-4 flex flex-col items-start gap-3"
      >
        <p class="md-body-large text-error">
          {{ failure }}
        </p>
        <MdButton
          variant="tonal"
          type="button"
          @click="load"
        >
          Retry
        </MdButton>
      </div>

      <!--
        The list is one surface-container-low block. Each row is a link over its whole width: a 40px tonal
        avatar, the id and description, the tags as assist chips, and the visibility and a chevron at the end.
        The rows are divided by a line that starts at the text, 72px in, as MD3 insets list dividers.
      -->
      <ul
        v-else-if="matches.length > 0"
        class="mt-2 m-0 list-none overflow-hidden rounded-corner-medium bg-surface-container-low p-0"
      >
        <li
          v-for="repository in matches"
          :key="repository.id"
          class="relative after:absolute after:inset-x-0 after:bottom-0 after:h-px after:bg-outline-variant after:content-[''] after:left-[72px] last:after:hidden"
        >
          <RouterLink
            data-test="repository-row"
            :to="browseLocation(repository.id)"
            :aria-label="`Browse ${repository.id}`"
            class="md-state-layer md-focus-ring-inset relative flex min-w-0 items-center gap-4 px-4 py-3 text-on-surface no-underline"
          >
            <span
              class="flex size-10 shrink-0 items-center justify-center rounded-corner-full bg-secondary-container text-on-secondary-container"
              aria-hidden="true"
            >
              <svg
                class="size-6"
                viewBox="0 0 24 24"
                fill="currentColor"
                focusable="false"
              >
                <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
              </svg>
            </span>
            <span class="flex min-w-0 flex-1 flex-col gap-1">
              <span
                data-test="repository-id"
                class="truncate md-body-large text-on-surface"
              >{{ repository.id }}</span>
              <span
                v-if="repository.description"
                data-test="repository-description"
                class="truncate md-body-medium text-on-surface-variant"
                :title="repository.description"
              >{{ repository.description }}</span>
              <span
                v-if="repository.tags.length > 0"
                class="mt-1 flex flex-wrap gap-2"
              >
                <span
                  v-for="tag in repository.tags"
                  :key="tag"
                  data-test="tag"
                  class="inline-flex h-8 items-center rounded-corner-small border border-outline px-3 md-label-large text-on-surface"
                >{{ tag }}</span>
              </span>
            </span>
            <span
              v-if="badgeOf(repository)"
              data-test="visibility"
              class="inline-flex shrink-0 items-center gap-1 md-label-large text-on-surface-variant"
            >
              <svg
                class="size-[18px]"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden="true"
                focusable="false"
              >
                <path :d="badgeOf(repository).icon" />
              </svg>
              {{ badgeOf(repository).label }}
            </span>
            <svg
              class="size-6 shrink-0 text-on-surface-variant"
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
              focusable="false"
            >
              <path d="M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z" />
            </svg>
          </RouterLink>
        </li>
      </ul>

      <div
        v-else
        class="mt-4 flex flex-col items-start gap-3"
      >
        <p class="md-body-large text-on-surface-variant">
          {{ repositories.length === 0 ? 'No repositories yet.' : 'No repository matches these filters.' }}
        </p>
        <MdButton
          v-if="repositories.length > 0 && filtersActive"
          variant="text"
          type="button"
          @click="clearFilters"
        >
          Clear filters
        </MdButton>
      </div>
    </section>
  </div>
</template>
