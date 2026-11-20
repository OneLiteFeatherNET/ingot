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
import { computed } from 'vue'
import { countLabel, formatBytes, formatDateTime } from './format'
import { lastRunOf, statusLabel, triggerLabel } from './cleanup'
import type { CleanupRun } from './cleanup'

/*
 * The latest run of each policy. The full history is on the Jobs page, which the "All runs" link opens
 * filtered to the cleanup source (add-jobs-page, D7).
 */
const props = withDefaults(defineProps<{
  /** The runs the server returned, in any order. */
  runs?: CleanupRun[]
  /** The policy ids, in the order the tab lists them. A policy with no run is left out. */
  policyIds?: string[]
}>(), {
  runs: () => [],
  policyIds: () => []
})

const STATUS_CLASS: Partial<Record<CleanupRun['status'], string>> = {
  SUCCEEDED: 'text-success',
  PARTIAL: 'text-warning',
  FAILED: 'text-error'
}

const latestRuns = computed((): CleanupRun[] => {
  const ids = [...new Set([...props.policyIds, ...props.runs.map(run => run.policyId)])]
  return ids
    .map(id => lastRunOf(props.runs, id))
    .filter((run): run is CleanupRun => run !== null)
})
</script>

<template>
  <section
    class="flex flex-col gap-4"
    aria-labelledby="run-history-title"
    data-ingot-component="run-history"
  >
    <div class="flex flex-wrap items-end justify-between gap-3">
      <h3
        id="run-history-title"
        class="md-title-large text-on-surface"
      >
        Latest runs
      </h3>
      <RouterLink
        :to="{ name: 'jobs', query: { kind: 'cleanup' } }"
        class="md-label-large text-primary"
      >
        All runs
      </RouterLink>
    </div>

    <p
      v-if="!latestRuns.length"
      class="md-body-medium text-on-surface-variant"
    >
      No runs yet.
    </p>

    <ul
      v-else
      class="flex flex-col divide-y divide-outline-variant rounded-corner-medium bg-surface-container-low"
      aria-label="Latest run of each policy"
    >
      <li
        v-for="run in latestRuns"
        :key="run.policyId"
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
