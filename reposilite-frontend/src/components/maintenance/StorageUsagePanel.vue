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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import MdList from '../md/MdList.vue'
import MdListItem from '../md/MdListItem.vue'
import MdSection from '../md/MdSection.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import RefreshIcon from '../icons/RefreshIcon.vue'
import { countLabel, formatBytes, formatDateTime } from './format'
import { hasQuota, normalizeStorage, quotaFraction, quotaNote, usageLine } from './storage'
import type { StorageUsage, StorageUsageWire } from './storage'

// While the server computes the figures, the panel asks again this often.
const POLL_INTERVAL_MS = 2000

/*
 * The storage figures of the maven repositories. The panel loads them when it opens and again
 * whenever `revision` changes, which the parent bumps once a cleanup run has finished. A refresh
 * starts the computation on the server; the panel polls until it reports that it is done.
 */
const props = withDefaults(defineProps<{
  revision?: number
}>(), {
  revision: 0
})

const { client } = useSession()

const storage = ref<StorageUsage | null>(null)
const loadError = ref('')
const requesting = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | undefined

const computing = computed(() => storage.value?.computing === true)
const busy = computed(() => computing.value || requesting.value)
const rows = computed(() => storage.value?.repositories ?? [])

const stopPolling = () => {
  clearTimeout(pollTimer)
  pollTimer = undefined
}

const apply = (data: StorageUsageWire | undefined) => {
  storage.value = normalizeStorage(data)
  loadError.value = ''
  stopPolling()
  if (computing.value) pollTimer = setTimeout(load, POLL_INTERVAL_MS)
}

async function load() {
  try {
    const { data } = await client.value.maven.storage()
    apply(data)
  } catch (error) {
    loadError.value = `Could not load the storage figures: ${errorMessage(error)}`
  }
}

async function refresh() {
  requesting.value = true
  try {
    const { data } = await client.value.maven.refreshStorage()
    apply(data)
  } catch (error) {
    createErrorToast(`Could not refresh the storage figures: ${errorMessage(error)}`)
  } finally {
    requesting.value = false
  }
}

watch(() => props.revision, load)
load()
onBeforeUnmount(stopPolling)
</script>

<template>
  <MdCard
    variant="filled"
    class="flex flex-col gap-4"
  >
    <div data-ingot-component="storage-usage-panel">
      <MdSection
        :level="2"
        title="Storage usage"
        :description="storage && storage.computedAt ? `Computed ${formatDateTime(storage.computedAt)}` : undefined"
        heading-id="storage-usage-title"
      >
        <template #actions>
          <MdButton
            variant="tonal"
            :disabled="busy"
            @click="refresh"
          >
            <template #icon>
              <RefreshIcon />
            </template>
            Refresh
          </MdButton>
        </template>
      </MdSection>
    </div>

    <MdStatePanel
      v-if="loadError"
      state="error"
      :description="loadError"
      @retry="load"
    />

    <div
      v-if="computing"
      class="flex items-center gap-3"
    >
      <MdLinearProgress
        indeterminate
        label="Computing storage figures"
        class="flex-1"
      />
      <span class="md-body-medium text-on-surface-variant">Computing storage figures…</span>
    </div>

    <MdStatePanel
      v-if="storage && !storage.computedAt && !computing"
      data-test="not-computed"
      state="empty"
      description="The figures have not been computed yet. Refresh to compute them."
    />

    <!-- While the server computes, the zero totals of the response are not results: only the progress is shown. -->
    <template v-else-if="storage && !computing">
      <p class="md-body-large text-on-surface">
        Total <span class="md-title-medium">{{ formatBytes(storage.totalBytes) }}</span>
        across {{ countLabel(rows.length, 'repository', 'repositories') }}
      </p>

      <MdStatePanel
        v-if="!rows.length"
        data-test="no-repositories"
        state="empty"
        description="No repository has been measured yet."
      />

      <MdList
        v-else
        divided
        aria-label="Storage per repository"
      >
        <MdListItem
          v-for="row in rows"
          :key="row.id"
        >
          <div class="flex flex-col gap-2">
            <div class="flex flex-wrap items-baseline justify-between gap-2">
              <span class="md-code-medium text-on-surface">{{ row.id }}</span>
              <span class="md-body-small text-on-surface-variant">{{ countLabel(row.fileCount, 'file') }}</span>
            </div>
            <p class="md-body-medium text-on-surface">
              {{ usageLine(row) }}
            </p>
            <MdLinearProgress
              v-if="hasQuota(row) && quotaFraction(row.usedBytes, row.quotaBytes) !== null"
              :value="quotaFraction(row.usedBytes, row.quotaBytes) ?? undefined"
              :label="`Usage of ${row.id}`"
            />
            <div
              v-else-if="hasQuota(row)"
              class="h-1 rounded-corner-full bg-surface-container-highest"
              aria-hidden="true"
            />
            <p
              v-if="quotaNote(row)"
              class="md-body-small text-on-surface-variant"
            >
              {{ quotaNote(row) }}
            </p>
          </div>
        </MdListItem>
      </MdList>
    </template>
  </MdCard>
</template>
