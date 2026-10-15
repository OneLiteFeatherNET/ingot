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
import MdTextField from '../md/MdTextField.vue'

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

// The visibility badges. A public repository has none.
const BADGES = {
  PRIVATE: { label: 'Private', className: 'bg-secondary-container text-on-secondary-container' },
  HIDDEN: { label: 'Hidden', className: 'bg-tertiary-container text-on-tertiary-container' }
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
    repositories.value = (data.repositories ?? []).map(normalise)
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
        <MdTextField
          :model-value="text"
          label="Search by id, description or tag"
          variant="outlined"
          autocomplete="off"
          @update:model-value="setText"
        >
          <template #leading-icon>
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1 0 9.5 16a6.5 6.5 0 0 0 4.2-1.6l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z" />
            </svg>
          </template>
        </MdTextField>

        <div
          v-if="shownTags.length > 0"
          role="group"
          aria-label="Filter by tag"
          class="flex flex-wrap gap-2"
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

      <ul
        v-else-if="matches.length > 0"
        class="mt-2 m-0 flex list-none flex-col gap-1 p-0"
      >
        <li
          v-for="repository in matches"
          :key="repository.id"
        >
          <RouterLink
            data-test="repository-row"
            :to="browseLocation(repository.id)"
            class="md-state-layer md-focus-ring relative flex min-w-0 flex-col gap-1 rounded-corner-large px-4 py-3 text-on-surface no-underline"
          >
            <span class="flex min-w-0 flex-wrap items-center gap-2">
              <span
                data-test="repository-id"
                class="min-w-0 [overflow-wrap:anywhere] md-title-medium"
              >{{ repository.id }}</span>
              <span
                v-if="badgeOf(repository)"
                data-test="visibility"
                class="rounded-corner-small px-2 py-0.5 md-label-medium"
                :class="badgeOf(repository).className"
              >{{ badgeOf(repository).label }}</span>
            </span>
            <span
              v-if="repository.description"
              data-test="repository-description"
              class="truncate md-body-medium text-on-surface-variant"
            >{{ repository.description }}</span>
            <span
              v-if="repository.tags.length > 0"
              class="flex flex-wrap gap-1"
            >
              <span
                v-for="tag in repository.tags"
                :key="tag"
                data-test="tag"
                class="inline-flex h-6 items-center rounded-corner-small border border-outline px-2 md-label-medium text-on-surface-variant"
              >{{ tag }}</span>
            </span>
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
