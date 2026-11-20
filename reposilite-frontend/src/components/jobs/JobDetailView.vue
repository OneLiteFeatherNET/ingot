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
import { onMounted, ref, watch } from 'vue'
import { useSession } from '../../store/session'
import { formatDateTime } from '../maintenance/format'
import { durationText, stateLabel, triggerLabel } from './jobs'
import type { JobRun } from './jobs'

// The server lists at most this many failures in a detail (add-jobs-page, D3); `failuresTruncated` says when more exist.
const FAILURE_LIMIT = 50

const props = defineProps<{
  /** The run id, `<source>:<local id>`, as the address carries it. */
  id: string
}>()

const { client } = useSession()
const run = ref<JobRun | null>(null)
const loaded = ref(false)
const notFound = ref(false)
const loadError = ref('')

async function load() {
  try {
    const { data }: { data: JobRun } = await client.value.jobs.detail(props.id)
    run.value = data
    notFound.value = false
    loadError.value = ''
  } catch (error) {
    run.value = null
    const status = (error as { response?: { status?: number } } | null)?.response?.status
    notFound.value = status === 404
    loadError.value = status === 404 ? '' : 'Could not load this run. Try again.'
  } finally {
    loaded.value = true
  }
}

onMounted(load)
watch(() => props.id, load)
</script>

<template>
  <section
    class="flex flex-col gap-4"
    aria-labelledby="job-detail-title"
    data-ingot-component="job-detail"
  >
    <RouterLink
      :to="{ name: 'jobs' }"
      class="md-label-large text-primary"
    >
      Back to the runs
    </RouterLink>

    <p
      v-if="!loaded"
      class="md-body-medium text-on-surface-variant"
    >
      Loading the run.
    </p>
    <p
      v-else-if="notFound"
      class="md-body-medium text-on-surface-variant"
    >
      This run does not exist, or its source is not open to you.
    </p>
    <p
      v-else-if="loadError"
      class="md-body-medium text-error"
      role="alert"
    >
      {{ loadError }}
    </p>

    <template v-else-if="run">
      <h2
        id="job-detail-title"
        class="md-title-large text-on-surface"
      >
        {{ run.label }}
      </h2>
      <dl class="grid gap-3 sm:grid-cols-2">
        <div class="flex flex-col">
          <dt class="md-label-large text-on-surface-variant">
            State
          </dt>
          <dd class="md-body-medium text-on-surface">
            {{ stateLabel(run.state) }}
          </dd>
        </div>
        <div class="flex flex-col">
          <dt class="md-label-large text-on-surface-variant">
            Kind
          </dt>
          <dd class="md-body-medium text-on-surface">
            {{ run.kind }}
          </dd>
        </div>
        <div class="flex flex-col">
          <dt class="md-label-large text-on-surface-variant">
            Trigger
          </dt>
          <dd class="md-body-medium text-on-surface">
            {{ triggerLabel(run.trigger) }}
          </dd>
        </div>
        <div class="flex flex-col">
          <dt class="md-label-large text-on-surface-variant">
            Started
          </dt>
          <dd class="md-body-medium text-on-surface">
            {{ formatDateTime(run.startedAt) }}
          </dd>
        </div>
        <div class="flex flex-col">
          <dt class="md-label-large text-on-surface-variant">
            Finished
          </dt>
          <dd class="md-body-medium text-on-surface">
            {{ formatDateTime(run.finishedAt, 'not finished') }}
            <span
              v-if="run.durationMs !== null"
              class="text-on-surface-variant"
            >, took {{ durationText(run.durationMs) }}</span>
          </dd>
        </div>
      </dl>

      <ul
        v-if="run.counts.length"
        class="flex flex-col gap-1 md-body-medium text-on-surface"
        aria-label="Counts of the run"
      >
        <li
          v-for="count in run.counts"
          :key="count.key"
        >
          {{ count.label }}: {{ count.value }}
        </li>
      </ul>

      <div
        v-if="run.failures.length"
        class="flex flex-col gap-2"
      >
        <h3 class="md-title-medium text-on-surface">
          Failures
        </h3>
        <ul class="flex list-disc flex-col gap-1 pl-5 md-body-medium text-error">
          <li
            v-for="(failure, index) in run.failures"
            :key="index"
          >
            {{ failure.message }}
          </li>
        </ul>
        <p
          v-if="run.failuresTruncated"
          class="md-body-small text-on-surface-variant"
        >
          Only the first {{ FAILURE_LIMIT }} failures are listed.
        </p>
      </div>
    </template>
  </section>
</template>
