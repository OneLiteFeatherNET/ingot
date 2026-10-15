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
import { computed } from 'vue'
import MdSelect from '../md/MdSelect.vue'
import { countLabel, formatBytes, formatDateTime } from './format'
import { sortNewestFirst, statusLabel, triggerLabel } from './cleanup'

const props = defineProps({
  /** The runs the server returned, in any order. They are shown newest first. */
  runs: {
    type: Array,
    default: () => []
  },
  /** The policy ids the filter offers. */
  policyIds: {
    type: Array,
    default: () => []
  },
  /** The policy the list is filtered to, or an empty string for every policy. */
  policy: {
    type: String,
    default: ''
  }
})

defineEmits(['update:policy'])

const STATUS_CLASS = {
  SUCCEEDED: 'text-success',
  PARTIAL: 'text-warning',
  FAILED: 'text-error'
}

const filterOptions = computed(() => [
  { value: '', label: 'All policies' },
  ...props.policyIds.map(id => ({ value: id, label: id }))
])

const visibleRuns = computed(() => sortNewestFirst(
  props.policy ? props.runs.filter(run => run.policyId === props.policy) : props.runs
))
</script>

<template>
  <section
    class="flex flex-col gap-4"
    aria-labelledby="run-history-title"
  >
    <div class="flex flex-wrap items-end justify-between gap-3">
      <h3
        id="run-history-title"
        class="md-title-large text-on-surface"
      >
        Run history
      </h3>
      <MdSelect
        id="run-history-policy"
        label="Policy"
        class="w-full sm:w-72"
        :model-value="policy"
        :options="filterOptions"
        @update:model-value="$emit('update:policy', $event)"
      />
    </div>

    <p
      v-if="!visibleRuns.length"
      class="md-body-medium text-on-surface-variant"
    >
      No runs yet.
    </p>

    <ul
      v-else
      class="flex flex-col divide-y divide-outline-variant rounded-corner-medium bg-surface-container-low"
      aria-label="Runs, newest first"
    >
      <li
        v-for="run in visibleRuns"
        :key="run.id"
        :data-run-id="String(run.id)"
        class="flex flex-col gap-2 px-4 py-3"
      >
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <span class="font-mono md-title-small text-on-surface">{{ run.policyId }}</span>
          <span
            class="md-label-large"
            :class="STATUS_CLASS[run.status] ?? 'text-on-surface-variant'"
          >{{ statusLabel(run.status) }}</span>
        </div>
        <p class="md-body-medium text-on-surface-variant">
          {{ triggerLabel(run.trigger) }}, started {{ formatDateTime(run.startedAt) }},
          finished {{ formatDateTime(run.finishedAt, 'not finished') }}
        </p>
        <p class="md-body-medium text-on-surface">
          {{ countLabel(run.deletedCount, 'artifact') }} deleted, {{ formatBytes(run.deletedBytes) }}
        </p>
        <details
          v-if="run.failures.length"
          class="md-body-medium"
        >
          <summary class="cursor-pointer text-error">
            {{ countLabel(run.failures.length, 'failure') }}
          </summary>
          <ul class="mt-2 flex list-disc flex-col gap-1 pl-5 text-on-surface-variant">
            <li
              v-for="(failure, index) in run.failures"
              :key="index"
            >
              {{ failure }}
            </li>
          </ul>
        </details>
      </li>
    </ul>
  </section>
</template>
