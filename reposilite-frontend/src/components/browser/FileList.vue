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
import download from 'downloadjs'
import { useRoute } from 'vue-router'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import { createURL } from '../../store/client'
import { useSession } from '../../store/session'
import DetailedListEntry from './DetailedListEntry.vue'
import DeleteEntryModal from './DeleteEntryModal.vue'
import MdButton from '../md/MdButton.vue'
import BrowserIcon from './BrowserIcon.vue'
import { computed, ref } from 'vue'
import { property } from '../../helpers/vue-extensions'

const props = defineProps({
  qualifier: property(Object, true),
  files: property(Object, true),
  compactMode: property(Boolean, true),
  loading: property(Boolean, true)
})

const emit = defineEmits(['retry'])

// What each kind of failed listing says, and whether asking again can help.
const FAILURES = {
  network: {
    icon: 'error',
    title: 'Cannot reach the repository',
    body: 'Check your connection, then try again.',
    retryable: true
  },
  'not-found': {
    icon: 'search',
    title: 'Directory not found',
    body: 'Nothing is published at this path. It may have moved or been removed.',
    retryable: false
  },
  forbidden: {
    icon: 'lock',
    title: 'You do not have access to this directory',
    body: 'Sign in with a token that can read it, or pick another path.',
    retryable: false
  },
  unknown: {
    icon: 'error',
    title: 'Cannot load this directory',
    body: 'The repository answered with an error. Try again in a moment.',
    retryable: true
  }
}

const failure = computed(() => {
  const base = FAILURES[props.files.error] ?? FAILURES.unknown
  const body = props.files.error === 'unknown' && props.files.message ? props.files.message : base.body
  return { ...base, body }
})

// cycled across skeleton rows so the placeholder name bars vary in width
const skeletonWidths = ['11rem', '7rem', '14rem', '9rem', '12rem', '8rem']
const skeletonCount = computed(() => Math.min(props.files.list?.length || 5, 10))

// one index-keyed loop so rows patch in place (skeleton -> data) instead of remounting
const displayedEntries = computed(() =>
  props.loading
    ? Array.from({ length: skeletonCount.value }, () => ({ __skeleton: true }))
    : (props.files.list ?? [])
)

const route = useRoute()
const { client } = useSession()

const downloadHandler = (file) => {
  client.value.maven.download(route.path.substring(1) + '/' + file.name)
    .then(response => download(response.data, file.name, response.headers['content-type']))
    .catch(error => createErrorToast(`Cannot download file: ${errorMessage(error)}`))
}

const deleteModalValue = ref()
const openDeleteModal = (file) => {
  deleteModalValue.value = {
    path: props.qualifier.path,
    file
  }
}
const closeDeleteModal = () =>
  (deleteModalValue.value = undefined)

const append = (path, pathToAppend) =>
  path + (path.endsWith('/') ? '' : '/') + pathToAppend

const entryUrl = (file) =>
  createURL(`${route.path}/${file.name}`)
</script>

<template>
  <div
    id="browser-list"
    class="pt-3"
  >
    <DeleteEntryModal
      :value="deleteModalValue"
      :close="closeDeleteModal"
    />
    <ul
      class="rounded-corner-extra-large bg-surface-container-low"
      :class="compactMode ? 'p-1' : 'p-2 divide-y divide-outline-variant'"
    >
      <template
        v-for="(entry, index) in displayedEntries"
        :key="entry.__skeleton ? `skeleton-${index}` : entry.name"
      >
        <li
          v-if="entry.__skeleton"
          class="flex items-center gap-4 rounded-corner-medium px-4"
          :class="compactMode ? 'min-h-12' : 'min-h-16'"
        >
          <span class="size-6 shrink-0 rounded-full bg-surface-container-high skeleton-bars" />
          <span
            class="h-3.5 rounded bg-surface-container-high skeleton-bars"
            :style="{ width: skeletonWidths[index % skeletonWidths.length] }"
          />
        </li>
        <DetailedListEntry
          v-else
          :file="entry"
          :qualifier="qualifier"
          :to="append(route.path, entry.name)"
          :url="entryUrl(entry)"
          :open-delete-entry-modal="openDeleteModal"
          :compact-mode="compactMode"
          @download="downloadHandler"
        />
      </template>
    </ul>
    <p
      v-if="!loading && files.isEmpty"
      class="px-4 pt-4 md-body-large text-on-surface-variant"
    >
      Directory is empty
    </p>
    <section
      v-if="!loading && files.error"
      role="alert"
      class="mt-3 flex flex-col items-start gap-3 rounded-corner-extra-large bg-surface-container-low p-6"
    >
      <BrowserIcon
        :name="failure.icon"
        class="text-error"
      />
      <h2 class="md-title-medium text-on-surface">
        {{ failure.title }}
      </h2>
      <p class="md-body-medium text-on-surface-variant">
        {{ failure.body }}
      </p>
      <MdButton
        v-if="failure.retryable"
        variant="tonal"
        @click="emit('retry')"
      >
        <template #icon>
          <BrowserIcon name="refresh" />
        </template>
        Retry
      </MdButton>
    </section>
  </div>
</template>
