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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdSelect from '../md/MdSelect.vue'
import { formatDateTime } from '../maintenance/format'
import {
  JOB_STATES,
  JOB_TRIGGERS,
  addressQueryOf,
  filtersFromQuery,
  listParams,
  stateLabel,
  triggerLabel
} from './jobs'
import type { JobFilters, JobPage, JobRun } from './jobs'

/*
 * The background runs of every source, newest first, in pages of 50. The filters are the address, so a filtered list
 * can be linked. The page loads on open and on Refresh; "Load more" appends the next page from the server's cursor.
 */
const { client } = useSession()
const route = useRoute()
const router = useRouter()

const filters = computed((): JobFilters => filtersFromQuery(route.query))
const filtersKey = computed(() => JSON.stringify(filters.value))

const runs = ref<JobRun[]>([])
const cursor = ref<string | null>(null)
const kinds = ref<string[]>([])
const loaded = ref(false)
const loading = ref(false)
const loadError = ref('')
const sourceNotice = ref('')
const announcement = ref('')
// Each request takes a number; a response of an older request, or of older filters, is dropped.
let generation = 0

const kindOptions = computed(() => {
  const ids = [...new Set([...kinds.value, filters.value.kind].filter(id => id !== ''))]
  return [{ value: '', label: 'All kinds' }, ...ids.map(id => ({ value: id, label: id }))]
})
const stateOptions = computed(() => [
  { value: '', label: 'All states' },
  ...JOB_STATES.map(state => ({ value: state, label: stateLabel(state) }))
])
const triggerOptions = computed(() => [
  { value: '', label: 'All triggers' },
  ...JOB_TRIGGERS.map(trigger => ({ value: trigger, label: triggerLabel(trigger) }))
])

async function request(after: string | null) {
  const current = ++generation
  loading.value = true
  try {
    const { data }: { data: JobPage } = await client.value.jobs.list(listParams(filters.value, after))
    if (current !== generation) return
    runs.value = after ? [...runs.value, ...data.items] : data.items
    cursor.value = data.nextCursor
    kinds.value = data.kinds
    loadError.value = ''
    sourceNotice.value = data.sourceErrors.length ? 'Some runs could not be read. The list shows the others.' : ''
    announcement.value = `${runs.value.length} runs listed.`
  } catch {
    if (current !== generation) return
    loadError.value = 'Could not load the runs. Try again.'
  } finally {
    if (current === generation) {
      loading.value = false
      loaded.value = true
    }
  }
}

const reload = () => request(null)
const loadMore = () => {
  if (cursor.value) request(cursor.value)
}

// A filter change is written to the address; the list reloads from the first page when the filters change.
const setFilter = (name: keyof JobFilters, value: string) => {
  router.replace({ query: addressQueryOf({ ...filters.value, [name]: value }) })
}

onMounted(reload)
watch(filtersKey, reload)
</script>

<template>
  <section
    class="flex flex-col gap-4"
    aria-labelledby="jobs-title"
    data-ingot-component="jobs-view"
  >
    <div class="flex flex-wrap items-end justify-between gap-3">
      <h2
        id="jobs-title"
        class="md-title-large text-on-surface"
      >
        Jobs
      </h2>
      <MdButton
        variant="outlined"
        :disabled="loading"
        @click="reload"
      >
        Refresh
      </MdButton>
    </div>

    <div class="grid gap-3 sm:grid-cols-3">
      <MdSelect
        id="jobs-kind"
        label="Kind"
        :model-value="filters.kind"
        :options="kindOptions"
        @update:model-value="setFilter('kind', $event)"
      />
      <MdSelect
        id="jobs-state"
        label="State"
        :model-value="filters.state"
        :options="stateOptions"
        @update:model-value="setFilter('state', $event)"
      />
      <MdSelect
        id="jobs-trigger"
        label="Trigger"
        :model-value="filters.trigger"
        :options="triggerOptions"
        @update:model-value="setFilter('trigger', $event)"
      />
    </div>

    <p
      v-if="loadError"
      class="md-body-medium text-error"
      role="alert"
    >
      {{ loadError }}
    </p>
    <p
      v-if="sourceNotice"
      class="md-body-medium text-warning"
    >
      {{ sourceNotice }}
    </p>
    <p
      v-if="!loaded"
      class="md-body-medium text-on-surface-variant"
    >
      Loading the runs.
    </p>
    <p
      v-else-if="!runs.length && !loadError"
      class="md-body-medium text-on-surface-variant"
    >
      No runs match these filters.
    </p>

    <ul
      v-if="runs.length"
      class="flex flex-col divide-y divide-outline-variant rounded-corner-medium bg-surface-container-low"
      aria-label="Background runs, newest first"
    >
      <li
        v-for="run in runs"
        :key="run.id"
        :data-job-id="run.id"
        class="flex flex-col gap-1 px-4 py-3"
      >
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <RouterLink
            :to="{ name: 'job', params: { id: run.id } }"
            class="md-title-small text-primary"
          >
            {{ run.label }}
          </RouterLink>
          <span class="md-label-large text-on-surface-variant">{{ stateLabel(run.state) }}</span>
        </div>
        <p class="md-body-small text-on-surface-variant">
          {{ run.kind }}, {{ triggerLabel(run.trigger) }}, started {{ formatDateTime(run.startedAt) }}
        </p>
      </li>
    </ul>

    <MdButton
      v-if="cursor"
      variant="outlined"
      :disabled="loading"
      class="self-start"
      @click="loadMore"
    >
      Load more
    </MdButton>

    <p
      aria-live="polite"
      class="md-body-small text-on-surface-variant"
    >
      {{ announcement }}
    </p>
  </section>
</template>
