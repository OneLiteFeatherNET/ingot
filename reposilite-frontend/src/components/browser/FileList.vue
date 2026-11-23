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

<script setup lang="ts">
import download from 'downloadjs'
import type { AxiosResponse } from 'axios'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import { browseLocation, childPath, parentPathOf } from '../../router/paths'
import { createURL } from '../../store/client'
import { useSession } from '../../store/session'
import DetailedListEntry from './DetailedListEntry.vue'
import DeleteEntryModal from './DeleteEntryModal.vue'
import { canDeleteAt, widestActionCount } from './rowActions'
import usePlaceholders from '../../store/placeholders'
import { useAdjustments } from '../../store/adjustments'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdList from '../md/MdList.vue'
import MdListItem from '../md/MdListItem.vue'
import BrowserIcon from './BrowserIcon.vue'
import { computed, reactive, ref } from 'vue'
import { useRowFade } from '../../composables/useRowFade'
import { useRouter } from 'vue-router'
import type { BrowserFile, BrowserPath, DeleteTarget, Listing, ListingFailure } from './listing'
import type { GlyphName } from './glyphs'

const props = defineProps<{
  qualifier: BrowserPath
  files: Partial<Listing>
  compactMode: boolean
  loading: boolean
  hiddenCount?: number
  // The repositories a group references, shown on the root of a repository only.
  members?: string[]
  // A manager may edit the members of a group, in the settings of the repository.
  canEditMembers?: boolean
}>()

const router = useRouter()
const references = computed(() => props.members ?? [])
const hasReferences = computed(() => references.value.length > 0)
const repositoryId = computed(() => props.qualifier.path.split('/')[0])
const editMembers = () => router.push({
  name: 'settings-repository',
  params: { id: repositoryId.value }
})
// The not-found actions go to the directory above the one that is missing, and to the first
// segment of the browsed path, which is the repository itself.
const parentLocation = computed(() => browseLocation(parentPathOf(props.qualifier.path)))
const repositoryLocation = computed(() => browseLocation(props.qualifier.path.split('/')[0]))

const emit = defineEmits<{ retry: [] }>()

// Judged on the list as it is shown, after hidden files are filtered out.
const isEmpty = computed(() =>
  !props.loading && !props.files.error && (props.files.list?.length ?? 0) === 0
)

const { displayHashFiles } = useAdjustments()
const showUtilityFiles = () => {
  displayHashFiles.value = true
}

// What each kind of failed listing says, and whether asking again can help.
const FAILURES: Record<ListingFailure, { icon: GlyphName; title: string; body: string; retryable: boolean }> = {
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
  const error = props.files.error
  const base = (error ? FAILURES[error] : undefined) ?? FAILURES.unknown
  const body = error === 'unknown' && props.files.message ? props.files.message : base.body
  return { ...base, body }
})

// cycled across skeleton rows so the placeholder name bars vary in width
const skeletonWidths = ['11rem', '7rem', '14rem', '9rem', '12rem', '8rem']
const skeletonCount = computed(() => Math.min(props.files.list?.length || 5, 10))

// A placeholder row while the listing loads. It has no file behind it.
type SkeletonRow = { __skeleton: true }
const isSkeleton = (entry: BrowserFile | SkeletonRow): entry is SkeletonRow => '__skeleton' in entry

// one index-keyed loop so rows patch in place (skeleton -> data) instead of remounting
const displayedEntries = computed<Array<BrowserFile | SkeletonRow>>(() =>
  props.loading
    ? Array.from({ length: skeletonCount.value }, () => ({ __skeleton: true as const }))
    : (props.files.list ?? [])
)

// Only a file that is added after the listing has loaded fades in; a new listing, a removal or a sort does not.
const { isAdded } = useRowFade(
  () => (props.files.list ?? []).map(file => file.name),
  () => !props.loading
)

const { client, token, hasPermissionTo } = useSession()
const { javadocSuffixes } = usePlaceholders()

// Every row reserves the action slot of the widest row, so the sizes line up. A listing without any
// action, such as a signed-out view of jars, reserves nothing and keeps the full width for names.
const widestActions = computed(() => widestActionCount(props.files.list ?? [], {
  qualifier: props.qualifier,
  canDelete: canDeleteAt(props.qualifier.path, hasPermissionTo),
  javadocSuffixes
}))

// Only a signed-in request needs the page to fetch the file; anonymous links go straight to it.
const signedIn = computed(() => Boolean(token.value.name && token.value.secret))

// Names of the files being fetched right now, so their rows can show progress.
const downloading = reactive<Record<string, boolean>>({})
// A property read, not Object.hasOwn: only reads through the proxy register for re-rendering.
const isDownloading = (file: BrowserFile) => downloading[file.name] === true

const downloadHandler = (file: BrowserFile) => {
  downloading[file.name] = true
  client.value.maven.download(childPath(props.qualifier.path, file.name))
    .then((response: AxiosResponse<Blob>) => download(response.data, file.name, response.headers['content-type'] as string | undefined))
    .catch(error => createErrorToast(`Cannot download file: ${errorMessage(error)}`))
    .finally(() => {
      delete downloading[file.name]
    })
}

const deleteModalValue = ref<DeleteTarget>()
const openDeleteModal = (entry: BrowserFile) => {
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
const directoryLocation = (file: BrowserFile) =>
  browseLocation(childPath(props.qualifier.path, file.name))

const entryUrl = (file: BrowserFile) =>
  createURL(`/${childPath(props.qualifier.path, file.name)}`)
</script>

<template>
  <div
    id="browser-list"
    class="pt-3"
    data-ingot-component="file-list"
  >
    <DeleteEntryModal
      :value="deleteModalValue"
      :close="closeDeleteModal"
    />
    <MdList
      v-if="loading || (files.list?.length ?? 0) > 0"
      divided
      :inset="16"
      class="rounded-corner-medium bg-surface-container-low"
      :class="compactMode ? 'p-1' : 'p-2'"
    >
      <template
        v-for="(entry, index) in displayedEntries"
        :key="isSkeleton(entry) ? `skeleton-${index}` : entry.name"
      >
        <MdListItem v-if="isSkeleton(entry)">
          <template #leading>
            <span class="block size-6 rounded-corner-full bg-surface-container-high skeleton-bars" />
          </template>
          <span
            class="block h-3.5 rounded-corner-extra-small bg-surface-container-high skeleton-bars"
            :style="{ width: skeletonWidths[index % skeletonWidths.length] }"
          />
        </MdListItem>
        <DetailedListEntry
          v-else
          :file="entry"
          :qualifier="qualifier"
          :to="directoryLocation(entry)"
          :url="entryUrl(entry)"
          :open-delete-entry-modal="openDeleteModal"
          :compact-mode="compactMode"
          :action-slots="widestActions"
          :signed-in="signedIn"
          :busy="isDownloading(entry)"
          :class="{ 'motion-row-enter': isAdded(entry.name) }"
          @download="downloadHandler"
        />
      </template>
    </MdList>
    <div
      v-if="isEmpty && (hiddenCount ?? 0) > 0"
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
      class="pt-6"
    >
      <h2
        id="browser-includes-title"
        class="md-title-small text-on-surface-variant"
      >
        Includes
      </h2>
      <MdList class="mt-3 rounded-corner-medium bg-surface-container-low p-1">
        <MdListItem
          v-for="member in references"
          :key="member"
          :to="browseLocation(member)"
        >
          <template #leading>
            <BrowserIcon
              name="folder"
              class="text-on-surface-variant"
            />
          </template>
          {{ member }}
        </MdListItem>
      </MdList>
      <div
        v-if="canEditMembers"
        class="mt-3"
      >
        <MdButton
          variant="tonal"
          type="button"
          @click="editMembers"
        >
          Edit members
        </MdButton>
      </div>
    </section>
    <MdCard
      v-if="!loading && files.error"
      as="section"
      variant="low"
      data-ingot-state="error"
      data-test="list-failure"
      role="alert"
      class="mt-3 flex flex-col items-start gap-3"
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
    </MdCard>
  </div>
</template>
