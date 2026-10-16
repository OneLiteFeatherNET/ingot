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
import { computed, ref, watch } from 'vue'
import { useAdjustments } from '../../store/adjustments'
import { useSession } from '../../store/session'
import AdjustmentsModal from './AdjustmentsModal.vue'
import Card from '../card/SnippetsCard.vue'
import { readDetailsCollapsed } from '../card/detailsCollapsed'
import Breadcrumb from './BreadcrumbNavigation.vue'
import FileList from './FileList.vue'
import BrowserUpload from './FileUpload.vue'
import BrowserIcon from './BrowserIcon.vue'
import MdIconButton from '../md/MdIconButton.vue'
import { property } from '../../helpers/vue-extensions'
import { errorMessage } from '../../helpers/toast'
import { classifyListingError } from './listingError'

const props = defineProps({
  qualifier: property(Object, true)
})

const files = ref({})
const loading = ref(true)
const { details, client, hasPermissionTo, sessionReady } = useSession()
const { applyAdjustments, countHiddenFiles } = useAdjustments()

const processedFiles = computed(() => ({
  ...files.value,
  list: applyAdjustments([...files.value.list ?? []])
}))

// Hidden utility files are counted here, on the listing as the server sent it.
const hiddenCount = computed(() => countHiddenFiles(files.value.list ?? []))

// A directory that does not exist cannot receive an upload, so the upload area is left out.
const canUpload = computed(() => {
  return props.qualifier.path.length > 1
    && files.value.error !== 'not-found'
    && hasPermissionTo(`/${props.qualifier.path}`, 'route:write')
})

// The root of a repository is the only place a group lists the repositories it references.
const isRepositoryRoot = (path) => path.length > 0 && !path.includes('/')

const members = ref([])

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
    new Promise(resolve => resolve(client.value.maven.members(path)))
      .then(response => {
        if (!isStale()) members.value = response.data.members ?? []
      })
      .catch(() => {
        if (!isStale()) members.value = []
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
      .then(response => {
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
            list: uniqueIntermediateDirectories.map(directory => ({
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

// The details card reports its fold state, so the listing can take the free width while it is folded.
const detailsCollapsed = ref(readDetailsCollapsed())

const fileBrowserCompactViewKey = 'file-browser-compact-view'
const fileBrowserCompactMode = ref(localStorage.getItem(fileBrowserCompactViewKey) === "true")
const toggleCompactMode = () => {
  fileBrowserCompactMode.value = !fileBrowserCompactMode.value
  localStorage.setItem(fileBrowserCompactViewKey, fileBrowserCompactMode.value.toString())
}
</script>

<template>
  <!--
    The same container as the header, so both share one content width. Below lg the details
    card stacks above the listing, as before. From lg the listing takes the free column and the
    details card sits in a fixed-width column beside it, with the same 24px gap in both axes.
  -->
  <div
    class="container mx-auto mb-12 grid min-h-[320px] gap-6 pt-7 lg:items-start"
    :class="detailsCollapsed ? 'grid-cols-[minmax(0,1fr)]' : 'grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,1fr)_minmax(320px,400px)]'"
  >
    <div
      class="min-w-0"
      :class="detailsCollapsed ? '' : 'lg:col-start-2 lg:row-start-1'"
    >
      <Card
        :qualifier="qualifier"
        @update:collapsed="value => detailsCollapsed = value"
      />
    </div>
    <div class="min-w-0">
      <div class="flex items-center justify-between gap-2">
        <Breadcrumb :path="qualifier.path" />
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
        @retry="retry"
      />
      <BrowserUpload
        v-if="canUpload"
        :qualifier="qualifier"
      />
    </div>
  </div>
</template>
