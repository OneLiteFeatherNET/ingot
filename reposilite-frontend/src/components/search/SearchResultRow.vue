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
import { computed, useId } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import { formatDay, formatSize, repositoryLabel } from './searchResult'

/*
 * One result of the search: a version (groupId, artifactId, version) with the repositories that hold
 * it. The title is a button that expands the row into its files, so the row is one tab stop whose
 * state is `aria-expanded`; the page moves between the rows with the arrow keys and expands or folds
 * a row with the right and left arrows (spec section 8.2). The link to the browser is reachable
 * from the row that has the focus, and the files in an open row are ordinary links.
 *
 * The row loads nothing itself. The page hands it the state of the files, and it reports what the
 * visitor asks for.
 */
const props = defineProps({
  item: { type: Object, required: true },
  expanded: { type: Boolean, default: false },
  // The one row of the list that is a tab stop (roving tabindex).
  tabbable: { type: Boolean, default: false },
  // { status: 'loading' | 'error' | 'ready', items, total, message } or undefined before the first request.
  files: { type: Object, default: undefined }
})

const emit = defineEmits(['toggle', 'focus-row', 'move', 'load-files', 'retry-files'])

const titleId = useId()
const regionId = useId()

const coordinates = computed(() => `${props.item.groupId}:${props.item.artifactId}:${props.item.version}`)
const hasNewer = computed(() => props.item.latestVersion !== null && props.item.latestVersion !== props.item.version)
const fileCount = computed(() => `${props.item.fileCount} ${props.item.fileCount === 1 ? 'file' : 'files'}`)

const onKeydown = (event) => {
  const { key } = event
  if (key === 'ArrowDown') emit('move', 'next')
  else if (key === 'ArrowUp') emit('move', 'previous')
  else if (key === 'ArrowRight' && !props.expanded) emit('toggle')
  else if (key === 'ArrowLeft' && props.expanded) emit('toggle')
  else return
  event.preventDefault()
}

const sourceOf = (file) => (file.origin === 'MIRROR'
  ? `cached from ${file.repository}${file.sourceHost ? ` (${file.sourceHost})` : ''}`
  : file.repository)
</script>

<template>
  <li
    class="relative border-b border-outline-variant last:border-b-0"
    data-test="result"
  >
    <div class="flex items-start gap-2 px-2 py-3 sm:px-4">
      <div class="flex min-w-0 flex-1 flex-col gap-2">
        <button
          :id="titleId"
          type="button"
          data-row-toggle
          :tabindex="tabbable ? 0 : -1"
          :aria-expanded="String(expanded)"
          :aria-controls="expanded ? regionId : undefined"
          class="md-state-layer md-focus-ring relative flex min-h-10 min-w-0 items-center gap-2 rounded-corner-small px-2 text-left text-on-surface"
          @click="emit('toggle')"
          @focus="emit('focus-row')"
          @keydown="onKeydown"
        >
          <svg
            class="size-6 shrink-0 text-on-surface-variant transition-transform"
            :class="expanded ? 'rotate-90' : ''"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
            focusable="false"
          >
            <path d="M8.59 16.59 13.17 12 8.59 7.41 10 6l6 6-6 6z" />
          </svg>
          <span
            class="min-w-0 break-words md-title-medium"
            data-test="coordinates"
          >
            <span class="text-on-surface-variant">{{ item.groupId }}:</span>{{ item.artifactId }}<span class="text-on-surface-variant">:</span>{{ item.version }}
          </span>
        </button>

        <div class="flex flex-wrap items-center gap-2 pl-10 md-body-medium text-on-surface-variant">
          <span
            v-if="hasNewer"
            data-test="newer"
            class="inline-flex h-6 items-center rounded-corner-small bg-tertiary-container px-2 md-label-medium text-on-tertiary-container"
          >Newer: {{ item.latestVersion }}</span>
          <span
            v-if="item.snapshot"
            data-test="snapshot"
            class="inline-flex h-6 items-center rounded-corner-small border border-outline px-2 md-label-medium text-on-surface"
          >Snapshot</span>
          <span
            v-for="repository in item.repositories"
            :key="`${repository.name}:${repository.origin}`"
            data-test="repository"
            :data-origin="repository.origin"
            class="inline-flex h-6 items-center rounded-corner-small px-2 md-label-medium"
            :class="repository.origin === 'MIRROR'
              ? 'bg-secondary-container text-on-secondary-container'
              : 'border border-outline text-on-surface'"
          >{{ repositoryLabel(repository) }}</span>
          <span data-test="file-count">{{ fileCount }}</span>
          <span
            v-if="item.lastModified"
            data-test="modified"
          >Modified {{ formatDay(item.lastModified) }}</span>
        </div>
      </div>

      <RouterLink
        v-if="item.browseLink"
        data-test="browse"
        :to="item.browseLink"
        :tabindex="tabbable ? undefined : -1"
        :aria-label="`Browse ${coordinates}`"
        class="md-state-layer md-focus-ring md-touch-target relative inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant"
      >
        <svg
          class="size-6"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
          focusable="false"
        >
          <path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" />
        </svg>
      </RouterLink>
    </div>

    <div
      v-if="expanded"
      :id="regionId"
      role="region"
      :aria-labelledby="titleId"
      class="bg-surface-container-low px-4 py-2 sm:pl-14"
      data-test="files"
    >
      <p
        v-if="!files || (files.status === 'loading' && files.items.length === 0)"
        class="m-0 py-2 md-body-medium text-on-surface-variant"
        data-test="files-loading"
      >
        Loading files
      </p>
      <div
        v-else-if="files.status === 'error'"
        class="flex flex-col items-start gap-2 py-2"
      >
        <p
          class="m-0 md-body-medium text-error"
          data-test="files-error"
        >
          {{ files.message }}
        </p>
        <MdButton
          variant="tonal"
          @click="emit('retry-files')"
        >
          Retry
        </MdButton>
      </div>
      <template v-else>
        <p
          v-if="files.items.length === 0"
          class="m-0 py-2 md-body-medium text-on-surface-variant"
        >
          No files to show for this version.
        </p>
        <ul
          v-else
          class="m-0 list-none p-0"
        >
          <li
            v-for="file in files.items"
            :key="`${file.repository}:${file.path}`"
            data-test="file"
            class="flex flex-wrap items-center gap-x-4 gap-y-1 py-2"
          >
            <RouterLink
              v-if="file.browseLink"
              :to="file.browseLink"
              class="md-focus-ring min-w-0 break-all rounded-corner-extra-small md-body-large text-primary underline"
              data-test="file-link"
            >
              {{ file.name }}
            </RouterLink>
            <span
              v-else
              class="min-w-0 break-all md-body-large text-on-surface"
            >{{ file.name }}</span>
            <span
              class="inline-flex h-6 items-center rounded-corner-small px-2 md-label-medium"
              :class="file.origin === 'MIRROR'
                ? 'bg-secondary-container text-on-secondary-container'
                : 'border border-outline text-on-surface'"
              data-test="file-source"
            >{{ sourceOf(file) }}</span>
            <span class="md-body-medium text-on-surface-variant">{{ formatSize(file.size) }}</span>
            <span
              v-if="file.lastModified"
              class="md-body-medium text-on-surface-variant"
            >{{ formatDay(file.lastModified) }}</span>
          </li>
        </ul>
        <MdButton
          v-if="files.items.length < files.total"
          variant="text"
          data-test="more-files"
          @click="emit('load-files')"
        >
          Show more files ({{ files.total - files.items.length }} left)
        </MdButton>
      </template>
    </div>
  </li>
</template>
