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
import { computed, onBeforeUnmount, ref } from 'vue'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import RebuildIndexDialog from './RebuildIndexDialog.vue'
import { countLabel, formatDateTime } from './format'
import {
  DEGRADED_ABOVE,
  INDEX_STATE_LABELS,
  isDegraded,
  isRunLive,
  normalizeIndexStatus,
  repositoryProgress,
  runKindLabel,
  runStatusLabel,
  runTriggerLabel
} from './searchIndex'

// While a run is live, the panel asks for the status again this often.
const POLL_INTERVAL_MS = 2000

const PROGRESS_LABELS = {
  done: 'Done',
  current: 'In progress',
  pending: 'Waiting'
}

const STATE_CLASS = {
  READY: 'text-success',
  BUILDING: 'text-warning',
  MISSING: 'text-warning',
  DEGRADED: 'text-warning',
  UNKNOWN: 'text-on-surface-variant'
}

/*
 * The search index of spec section 8.4: its state, the lag and the outbox, the last full rebuild,
 * the run in progress with its repositories, the drift report of the last verify and the run history.
 * Rebuild and verify are started here; the server refuses a second run while one is live (409). The
 * status is asked for again while a run is live, and stops when none is.
 */
const { client } = useSession()

const index = ref(null)
const loaded = ref(false)
const loadError = ref('')
const notAvailable = ref(false)
const notAllowed = ref(false)
const starting = ref(false)
const announcement = ref('')
const rebuildOpen = ref(false)
let pollTimer = null

const live = computed(() => isRunLive(index.value?.activeRun))
const busy = computed(() => live.value || starting.value)
const stateLabel = computed(() => INDEX_STATE_LABELS[index.value?.state] ?? INDEX_STATE_LABELS.UNKNOWN)
const runs = computed(() => [...(index.value?.runs ?? [])].sort((a, b) => b.id - a.id))
const progress = computed(() => {
  const run = index.value?.activeRun
  return run && run.kind === 'REBUILD' ? repositoryProgress(run, index.value.repositories) : []
})

const lagText = (seconds) => (seconds < 60
  ? countLabel(seconds, 'second')
  : countLabel(Math.floor(seconds / 60), 'minute'))

const percent = (fraction) => `${Math.round(fraction * 100)}%`

const countersText = (counters) =>
  `Processed ${counters.processed}, upserted ${counters.upserted}, deleted ${counters.deleted}, errors ${counters.errors}`

const stopPolling = () => {
  clearTimeout(pollTimer)
  pollTimer = null
}

const schedulePoll = () => {
  stopPolling()
  if (live.value) pollTimer = setTimeout(load, POLL_INTERVAL_MS)
}

async function load() {
  try {
    const { data } = await client.value.maven.searchIndex()
    index.value = normalizeIndexStatus(data)
    loadError.value = ''
    notAvailable.value = false
    notAllowed.value = false
  } catch (error) {
    const status = error?.response?.status
    index.value = null
    notAvailable.value = status === 404
    notAllowed.value = status === 403
    loadError.value = status === 404 || status === 403 ? '' : `Could not load the search index: ${errorMessage(error)}`
  } finally {
    loaded.value = true
    schedulePoll()
  }
}

// A refused start says why in words. The server's own message is not shown, as it names internals.
const refusalOf = (error) => {
  const status = error?.response?.status
  if (status === 409) return 'A rebuild or verify run is already running. Wait for it to finish or cancel it.'
  if (status === 403) return 'Only managers can change the search index.'
  if (status === 404) return 'Search is turned off on this instance.'
  return `Could not start the run: ${errorMessage(error)}`
}

async function start(request, label) {
  starting.value = true
  try {
    const { data } = await request()
    announcement.value = `${label} run ${data.runId} started.`
    await load()
  } catch (error) {
    if (error?.response?.status === 404) notAvailable.value = true
    createErrorToast(refusalOf(error))
  } finally {
    starting.value = false
  }
}

const rebuild = async (body) => {
  rebuildOpen.value = false
  await start(() => client.value.maven.startIndexRebuild(body), 'Rebuild')
}

const verify = () => start(() => client.value.maven.startIndexVerify(), 'Verify')

async function cancel(runId) {
  try {
    await client.value.maven.cancelIndexRun(runId)
    announcement.value = `Run ${runId} cancelled.`
    await load()
  } catch (error) {
    createErrorToast(`Could not cancel the run: ${errorMessage(error)}`)
  }
}

load()
onBeforeUnmount(stopPolling)
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="search-index-title"
  >
    <MdCard
      variant="filled"
      class="flex flex-col gap-4"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex min-w-0 flex-col gap-1">
          <h3
            id="search-index-title"
            class="md-title-large text-on-surface"
          >
            Search index
          </h3>
          <p class="md-body-medium text-on-surface-variant">
            The index behind artifact search. The outbox keeps it current; a rebuild reads storage again, and a
            verify compares a sample of rows with storage.
          </p>
        </div>
        <div
          v-if="index"
          class="flex flex-wrap gap-2"
        >
          <MdButton
            variant="tonal"
            :disabled="busy"
            @click="rebuildOpen = true"
          >
            Rebuild index
          </MdButton>
          <MdButton
            variant="outlined"
            :disabled="busy"
            @click="verify"
          >
            Verify sample
          </MdButton>
        </div>
      </div>

      <p
        v-if="notAvailable"
        class="md-body-large text-on-surface-variant"
        role="status"
      >
        Search is turned off on this instance. Set INGOT_LOCAL_SEARCHENABLED to true and restart Ingot to turn it on.
      </p>
      <p
        v-else-if="notAllowed"
        class="md-body-medium text-error"
        role="alert"
      >
        Only managers can see the search index.
      </p>
      <p
        v-else-if="loadError"
        class="md-body-medium text-error"
        role="alert"
      >
        {{ loadError }}
      </p>
      <p
        v-else-if="!loaded"
        class="md-body-medium text-on-surface-variant"
      >
        Loading the search index…
      </p>

      <template v-if="index">
        <div class="flex flex-col gap-1">
          <p class="md-title-medium">
            <span
              :class="STATE_CLASS[index.state]"
              data-state
            >{{ stateLabel }}</span>
          </p>
          <dl class="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2 md-body-medium">
            <div class="flex flex-col">
              <dt class="text-on-surface-variant">
                Lag behind storage
              </dt>
              <dd
                class="text-on-surface"
                data-lag
              >
                {{ lagText(index.lagSeconds) }}
              </dd>
            </div>
            <div class="flex flex-col">
              <dt class="text-on-surface-variant">
                Last full rebuild
              </dt>
              <dd
                class="text-on-surface"
                data-last-rebuild
              >
                {{ formatDateTime(index.lastFullRebuild, 'Never') }}
              </dd>
            </div>
            <div class="flex flex-col">
              <dt class="text-on-surface-variant">
                Indexed files
              </dt>
              <dd class="text-on-surface">
                {{ countLabel(index.rows, 'row') }}
              </dd>
            </div>
            <div class="flex flex-col">
              <dt class="text-on-surface-variant">
                Waiting in the outbox
              </dt>
              <dd
                class="text-on-surface"
                data-outbox="pending"
              >
                {{ countLabel(index.outbox.pending, 'entry', 'entries') }}
              </dd>
            </div>
            <div class="flex flex-col">
              <dt class="text-on-surface-variant">
                Dead letters
              </dt>
              <dd
                class="text-on-surface"
                data-outbox="dead-letters"
              >
                {{ countLabel(index.outbox.deadLetters, 'entry', 'entries') }}
              </dd>
              <dd class="md-body-small text-on-surface-variant">
                Entries that failed too often. A rebuild or a verify repairs their rows.
              </dd>
            </div>
            <div
              v-if="index.outbox.droppedEnqueues > 0"
              class="flex flex-col"
            >
              <dt class="text-on-surface-variant">
                Changes not queued
              </dt>
              <dd
                class="text-on-surface"
                data-outbox="dropped"
              >
                {{ index.outbox.droppedEnqueues }} since the last rebuild
              </dd>
            </div>
          </dl>
        </div>

        <div
          v-if="live"
          class="flex flex-col gap-3 rounded-corner-medium bg-surface-container-low p-4"
          data-live-run
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="flex flex-col gap-1">
              <h4 class="md-title-medium text-on-surface">
                {{ runKindLabel(index.activeRun.kind) }} running
              </h4>
              <p class="md-body-small text-on-surface-variant">
                {{ runTriggerLabel(index.activeRun.trigger) }}, started {{ formatDateTime(index.activeRun.startedAt) }}
              </p>
              <p class="md-body-small text-on-surface-variant">
                {{ countersText(index.activeRun.counters) }}
              </p>
            </div>
            <MdButton
              variant="outlined"
              @click="cancel(index.activeRun.id)"
            >
              Cancel run
            </MdButton>
          </div>
          <ul
            v-if="progress.length"
            class="flex flex-col divide-y divide-outline-variant"
            aria-label="Repositories of the run"
          >
            <li
              v-for="entry in progress"
              :key="entry.name"
              :data-repository="entry.name"
              :data-progress="entry.state"
              class="flex flex-wrap items-baseline justify-between gap-2 py-2"
            >
              <span class="font-mono md-body-large text-on-surface">{{ entry.name }}</span>
              <span
                class="md-body-small"
                :class="entry.state === 'current' ? 'text-primary' : 'text-on-surface-variant'"
              >{{ PROGRESS_LABELS[entry.state] }}</span>
            </li>
          </ul>
        </div>

        <div
          v-if="index.lastVerify"
          class="flex flex-col gap-2"
          data-verify
        >
          <h4 class="md-title-medium text-on-surface">
            Last verify
          </h4>
          <p class="md-body-medium text-on-surface">
            Drift {{ percent(index.lastVerify.drift) }} in a sample of {{ countLabel(index.lastVerify.sampled, 'row') }}:
            {{ countLabel(index.lastVerify.missing, 'row') }} missing from storage,
            {{ countLabel(index.lastVerify.changed, 'row') }} changed,
            {{ countLabel(index.lastVerify.reorigined, 'row') }} with a corrected origin.
          </p>
          <p
            v-if="index.lastVerify.removedRepositoryRows > 0"
            class="md-body-small text-on-surface-variant"
          >
            {{ countLabel(index.lastVerify.removedRepositoryRows, 'row') }} of repositories that are no longer configured were removed.
          </p>
          <p
            v-if="isDegraded(index.lastVerify)"
            class="md-body-medium text-warning"
          >
            Drift is above {{ Math.round(DEGRADED_ABOVE * 100) }} percent. Start a rebuild to repair the index.
          </p>
        </div>

        <div class="flex flex-col gap-3">
          <h4 class="md-title-medium text-on-surface">
            Index runs
          </h4>
          <p
            v-if="!runs.length"
            class="md-body-medium text-on-surface-variant"
          >
            No run has been started yet.
          </p>
          <ul
            v-else
            class="flex flex-col divide-y divide-outline-variant rounded-corner-medium bg-surface-container-low"
            aria-label="Index runs, newest first"
          >
            <li
              v-for="run in runs"
              :key="run.id"
              :data-run-id="String(run.id)"
              class="flex flex-col gap-1 px-4 py-3"
            >
              <div class="flex flex-wrap items-baseline justify-between gap-2">
                <span class="md-title-small text-on-surface">
                  {{ runKindLabel(run.kind) }}, {{ runTriggerLabel(run.trigger) }}
                </span>
                <span class="md-label-large text-on-surface-variant">{{ runStatusLabel(run.status) }}</span>
              </div>
              <p class="md-body-small text-on-surface-variant">
                Started {{ formatDateTime(run.startedAt) }}, finished {{ formatDateTime(run.finishedAt, 'not finished') }}
              </p>
              <p class="md-body-small text-on-surface-variant">
                {{ countersText(run.counters) }}
              </p>
              <p
                v-if="run.lastError"
                class="md-body-small text-error"
              >
                {{ run.lastError }}
              </p>
            </li>
          </ul>
        </div>
      </template>

      <p
        aria-live="polite"
        class="md-body-small text-on-surface-variant"
      >
        {{ announcement }}
      </p>
    </MdCard>

    <RebuildIndexDialog
      v-if="index"
      v-model="rebuildOpen"
      :repositories="index.repositories"
      :running="starting"
      @confirm="rebuild"
    />
  </section>
</template>
