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
import { computed, ref, watch } from 'vue'
import type { AxiosResponse } from 'axios'
import type { components } from '../../types/api'
import { useAdjustments } from '../../store/adjustments'
import { useSession } from '../../store/session'
import AdjustmentsModal from './AdjustmentsModal.vue'
import Card from '../card/SnippetsCard.vue'
import Breadcrumb from './BreadcrumbNavigation.vue'
import FileList from './FileList.vue'
import BrowserUpload from './FileUpload.vue'
import BrowserIcon from './BrowserIcon.vue'
import RepositoryTypeBadge from '../tokens/RepositoryTypeBadge.vue'
import { typeBadgeOf } from '../../store/maven/repositoryTypes'
import MdCard from '../md/MdCard.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdPage from '../md/MdPage.vue'
import { errorMessage } from '../../helpers/toast'
import { classifyListingError } from './listingError'
import type { BrowserFile, BrowserQualifier, Listing } from './listing'

const props = defineProps<{
  qualifier: BrowserQualifier
}>()

const files = ref<Partial<Listing>>({})
const loading = ref(true)
const { details, client, hasPermissionTo, sessionReady, isManager } = useSession()
const { applyAdjustments, countHiddenFiles } = useAdjustments()

const processedFiles = computed(() => ({
  ...files.value,
  list: applyAdjustments([...files.value.list ?? []]) as BrowserFile[]
}))

// Hidden utility files are counted here, on the listing as the server sent it.
const hiddenCount = computed(() => countHiddenFiles(files.value.list ?? []))

// The names the server lists here, for the details card. Null until this listing is in, so the card waits for it.
const listedNames = computed(() => loading.value ? null : (files.value.list ?? []).map(file => file.name))

// The type of the repository this browser is in, when it is known and was set: 'proxy', 'group', or ''.
const readOnlyType = computed(() => {
  const badge = repositoryEntry.value ? typeBadgeOf(repositoryEntry.value) : null
  return badge && !badge.legacy && (badge.key === 'proxy' || badge.key === 'group') ? badge.key : ''
})

const READ_ONLY_TEXT: Record<string, string> = {
  proxy: 'This is a proxy repository. Its files are fetched from the remote repositories it is set up with. Upload to a hosted repository instead.',
  group: 'This is a group repository. It lists the repositories above and has no files of its own. Upload to one of the repositories it lists instead.'
}

// A directory that does not exist cannot receive an upload, and a proxy or a group takes none, so the upload area is left out.
const canUpload = computed(() => {
  return props.qualifier.path.length > 1
    && files.value.error !== 'not-found'
    && readOnlyType.value === ''
    && hasPermissionTo(`/${props.qualifier.path}`, 'route:write')
})

// The root of a repository is the only place a group lists the repositories it references.
const isRepositoryRoot = (path: string) => path.length > 0 && !path.includes('/')

const members = ref<string[]>([])

// Asked again when the session changes, since what a caller may see of the references changes with it.
// A failed request leaves the listing as it was, with no references and no error of its own.
watch(
  () => [props.qualifier.path, details.value, sessionReady.value],
  () => {
    const path = props.qualifier.path
    if (!sessionReady.value || !isRepositoryRoot(path)) {
      members.value = []
      return
    }

    const isStale = () => path !== props.qualifier.path
    new Promise<AxiosResponse<components['schemas']['RepositoryMembersResponse']>>(resolve => resolve(client.value.maven.members(path)))
      .then(response => {
        if (!isStale()) members.value = response.data.members ?? []
      })
      .catch(() => {
        if (!isStale()) members.value = []
      })
  },
  { immediate: true }
)

// The repository this browser is in, from the repository list, for the badge of its type. Asked once per
// repository, so browsing down its directories does not ask again. A failed request shows no badge.
const repositoryEntry = ref<components['schemas']['RepositoryListEntry'] | null>(null)
const repositoryId = computed(() => props.qualifier.path.split('/')[0])

watch(
  () => [repositoryId.value, details.value, sessionReady.value],
  () => {
    const id = repositoryId.value
    if (!sessionReady.value || id === '') {
      repositoryEntry.value = null
      return
    }

    const isStale = () => id !== repositoryId.value
    new Promise<AxiosResponse<components['schemas']['RepositoryListEntriesResponse']>>(resolve => resolve(client.value.maven.repositories()))
      .then(response => {
        if (!isStale()) repositoryEntry.value = (response.data.repositories ?? []).find(entry => String(entry.id) === id) ?? null
      })
      .catch(() => {
        if (!isStale()) repositoryEntry.value = null
      })
  },
  { immediate: true }
)

// bumped by the retry action, so a failed listing can be requested again
const attempt = ref(0)
const retry = () => attempt.value++

watch(
  () => [props.qualifier.watchable, details.value, attempt.value, sessionReady.value],
  async () => {
    // Until the stored session has been checked, the listing would be requested once without
    // the session and again with it, so it waits for the answer.
    if (!sessionReady.value) {
      return
    }

    const qualifier = props.qualifier.path
    const isStale = () => qualifier !== props.qualifier.path
    loading.value = true

    client.value.maven.details(qualifier)
      .then((response: AxiosResponse<{ files: BrowserFile[] }>) => {
        if (isStale()) return
        files.value = {
          list: response.data.files,
          error: false
        }
        loading.value = false
      })
      .catch(error => {
        if (isStale()) return
        // simulate intermediate directory if 403 & user has access to only one directory
        // Anonymous browsing has no session details; the throw here used to leave `loading` set.
        const currentRoutes = details.value?.routes
            ?.filter(route => route.path.startsWith(`/${qualifier}`))
            ?? []

        if (error?.response?.status === 403 && currentRoutes.length > 0) {
          const intermediateDirectories = currentRoutes.map(currentRoute => {
            let currentSegment = currentRoute.path.substring(`/${qualifier}/`.replaceAll('//', '/').length)
            return currentSegment.includes('/') ? currentSegment.substring(0, currentSegment.indexOf('/')) : currentSegment
          })

          const uniqueIntermediateDirectories = [...new Set(intermediateDirectories)]

          files.value = {
            list: uniqueIntermediateDirectories.map((directory): BrowserFile => ({
              name: directory,
              type: 'DIRECTORY',
              list: []
            })),
            error: false
          }
        } else {
          // Shown in place of the listing, with a retry, so there is no toast to dismiss.
          files.value = {
            list: [],
            error: classifyListingError(error),
            message: errorMessage(error)
          }
        }

        loading.value = false
      })
  },
  { immediate: true }
)

const fileBrowserCompactViewKey = 'file-browser-compact-view'
const fileBrowserCompactMode = ref(localStorage.getItem(fileBrowserCompactViewKey) === "true")
const toggleCompactMode = () => {
  fileBrowserCompactMode.value = !fileBrowserCompactMode.value
  localStorage.setItem(fileBrowserCompactViewKey, fileBrowserCompactMode.value.toString())
}
</script>

<template>
  <!--
    The page container is MdPage, as for the header and the start page, so all share one content width. Below lg the details
    card stacks above the listing, folded or open alike. From lg the card is always the sidebar on the right, in the
    same column whether it is folded or not, so the listing keeps its width and the toggle keeps its place.
  -->
  <MdPage spaced>
    <div
      class="grid min-h-[320px] grid-cols-[minmax(0,1fr)] gap-6 expanded:grid-cols-[minmax(0,1fr)_minmax(320px,400px)] expanded:items-start"
      data-ingot-component="file-browser-view"
    >
      <div class="min-w-0 expanded:col-start-2 expanded:row-start-1">
        <Card
          :qualifier="qualifier"
          :listing="listedNames"
        />
      </div>
      <div class="min-w-0">
        <div class="flex items-center justify-between gap-2">
          <div class="flex min-w-0 flex-wrap items-center gap-2">
            <Breadcrumb :path="qualifier.path" />
            <RepositoryTypeBadge
              v-if="repositoryEntry"
              data-test="browse-type"
              :repository="repositoryEntry"
            />
          </div>
          <div class="flex shrink-0 items-center gap-1">
            <MdIconButton
              toggle
              aria-label="Compact list"
              title="Compact list"
              :selected="fileBrowserCompactMode"
              @click="toggleCompactMode()"
            >
              <BrowserIcon name="list" />
            </MdIconButton>
            <AdjustmentsModal />
          </div>
        </div>
        <FileList
          :qualifier="qualifier"
          :files="processedFiles"
          :compact-mode="fileBrowserCompactMode"
          :loading="loading"
          :hidden-count="hiddenCount"
          :members="members"
          :can-edit-members="isManager === true"
          @retry="retry"
        />
        <MdCard
          v-if="readOnlyType !== ''"
          as="section"
          variant="low"
          data-test="read-only-explanation"
          aria-labelledby="browser-read-only-title"
          class="mt-8 flex items-start gap-3"
        >
          <BrowserIcon
            name="lock"
            class="mt-0.5 shrink-0 text-on-surface-variant"
          />
          <div class="flex min-w-0 flex-col gap-1">
            <h2
              id="browser-read-only-title"
              class="md-title-small text-on-surface"
            >
              Nothing can be uploaded here
            </h2>
            <p class="md-body-medium text-on-surface-variant">
              {{ READ_ONLY_TEXT[readOnlyType] }}
            </p>
          </div>
        </MdCard>
        <BrowserUpload
          v-else-if="canUpload"
          :qualifier="qualifier"
        />
      </div>
    </div>
  </MdPage>
</template>
