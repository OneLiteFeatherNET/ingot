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
import { createErrorToast, errorMessage } from '../../helpers/toast'
import { browseLocation, childPath, parentPathOf } from '../../router/paths'
import { createURL } from '../../store/client'
import { useSession } from '../../store/session'
import DetailedListEntry from './DetailedListEntry.vue'
import DeleteEntryModal from './DeleteEntryModal.vue'
import { useAdjustments } from '../../store/adjustments'
import MdButton from '../md/MdButton.vue'
import BrowserIcon from './BrowserIcon.vue'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { property } from '../../helpers/vue-extensions'

const props = defineProps({
  qualifier: property(Object, true),
  files: property(Object, true),
  compactMode: property(Boolean, true),
  loading: property(Boolean, true),
  hiddenCount: property(Number, false),
  // The repositories a group references, shown on the root of a repository only.
  members: property(Array, false)
})

const router = useRouter()
const references = computed(() => props.members ?? [])
const hasReferences = computed(() => references.value.length > 0)
// The not-found actions go to the directory above the one that is missing, and to the first
// segment of the browsed path, which is the repository itself.
const parentLocation = computed(() => browseLocation(parentPathOf(props.qualifier.path)))
const repositoryLocation = computed(() => browseLocation(props.qualifier.path.split('/')[0]))

const emit = defineEmits(['retry'])

// Judged on the list as it is shown, after hidden files are filtered out.
const isEmpty = computed(() =>
  !props.loading && !props.files.error && (props.files.list?.length ?? 0) === 0
)

const { displayHashFiles } = useAdjustments()
const showUtilityFiles = () => {
  displayHashFiles.value = true
}

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

const { client, token } = useSession()

// Only a signed-in request needs the page to fetch the file; anonymous links go straight to it.
const signedIn = computed(() => Boolean(token.value.name && token.value.secret))

// Names of the files being fetched right now, so their rows can show progress.
const downloading = reactive({})
// A property read, not Object.hasOwn: only reads through the proxy register for re-rendering.
const isDownloading = (file) => downloading[file.name] === true

const downloadHandler = (file) => {
  downloading[file.name] = true
  client.value.maven.download(childPath(props.qualifier.path, file.name))
    .then(response => download(response.data, file.name, response.headers['content-type']))
    .catch(error => createErrorToast(`Cannot download file: ${errorMessage(error)}`))
    .finally(() => {
      delete downloading[file.name]
    })
}

const deleteModalValue = ref()
const openDeleteModal = (entry) => {
  deleteModalValue.value = {
    path: props.qualifier.path,
    file: entry.name,
    directory: entry.type === 'DIRECTORY'
  }
}
const closeDeleteModal = () =>
  (deleteModalValue.value = undefined)

// Directories open in the browser, so their links are routes; files are served by the
// repository itself, so their links are plain URLs.
const directoryLocation = (file) =>
  browseLocation(childPath(props.qualifier.path, file.name))

const entryUrl = (file) =>
  createURL(`/${childPath(props.qualifier.path, file.name)}`)
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
      v-if="loading || (files.list?.length ?? 0) > 0"
      class="rounded-corner-medium bg-surface-container-low"
      :class="compactMode ? 'p-1' : 'p-2 [&>li+li]:after:absolute [&>li+li]:after:inset-x-4 [&>li+li]:after:top-0 [&>li+li]:after:h-px [&>li+li]:after:bg-outline-variant'"
    >
      <template
        v-for="(entry, index) in displayedEntries"
        :key="entry.__skeleton ? `skeleton-${index}` : entry.name"
      >
        <li
          v-if="entry.__skeleton"
          class="relative flex items-center gap-4 rounded-corner-medium px-4"
          :class="compactMode ? 'min-h-14' : 'min-h-18'"
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
          :to="directoryLocation(entry)"
          :url="entryUrl(entry)"
          :open-delete-entry-modal="openDeleteModal"
          :compact-mode="compactMode"
          :signed-in="signedIn"
          :busy="isDownloading(entry)"
          @download="downloadHandler"
        />
      </template>
    </ul>
    <div
      v-if="isEmpty && hiddenCount > 0"
      class="flex flex-col items-start gap-3 px-4 pt-4"
    >
      <p class="md-body-large text-on-surface-variant">
        This directory holds {{ hiddenCount }} utility {{ hiddenCount === 1 ? 'file' : 'files' }}
        (checksums and signatures), which are hidden.
      </p>
      <MdButton
        variant="tonal"
        @click="showUtilityFiles"
      >
        Show utility files
      </MdButton>
    </div>
    <p
      v-else-if="isEmpty && !hasReferences"
      class="px-4 pt-4 md-body-large text-on-surface-variant"
    >
      Directory is empty
    </p>
    <section
      v-if="!loading && !files.error && hasReferences"
      id="browser-includes"
      aria-labelledby="browser-includes-title"
      class="px-4 pt-6"
    >
      <h2
        id="browser-includes-title"
        class="md-title-small text-on-surface-variant"
      >
        Includes
      </h2>
      <ul class="mt-3 rounded-corner-medium bg-surface-container-low p-1">
        <li
          v-for="member in references"
          :key="member"
        >
          <router-link
            :to="browseLocation(member)"
            class="md-state-layer md-focus-ring flex min-h-14 items-center gap-4 rounded-corner-medium px-4 text-on-surface"
          >
            <BrowserIcon
              name="folder"
              class="text-on-surface-variant"
            />
            <span class="md-body-large">{{ member }}</span>
          </router-link>
        </li>
      </ul>
    </section>
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
      <div
        v-if="files.error === 'not-found'"
        class="flex flex-wrap gap-2"
      >
        <MdButton
          variant="tonal"
          @click="router.push(parentLocation)"
        >
          Go to parent
        </MdButton>
        <MdButton
          variant="tonal"
          @click="router.push(repositoryLocation)"
        >
          Go to repository root
        </MdButton>
      </div>
    </section>
  </div>
</template>
