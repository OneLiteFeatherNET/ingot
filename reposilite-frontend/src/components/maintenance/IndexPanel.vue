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
import { computed, onBeforeUnmount, ref } from 'vue'
import { useSession } from '../../store/session'
import { stateLabel as jobStateLabel } from '../jobs/jobs'
import type { JobRun } from '../jobs/jobs'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdSection from '../md/MdSection.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
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
  runTriggerLabel
} from './searchIndex'
import type { IndexCounters, IndexStatus } from './searchIndex'

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

const index = ref<IndexStatus | null>(null)
const loaded = ref(false)
const loadError = ref('')
const notAvailable = ref(false)
const notAllowed = ref(false)
const starting = ref(false)
const announcement = ref('')
const rebuildOpen = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | undefined

const live = computed(() => isRunLive(index.value?.activeRun))
const busy = computed(() => live.value || starting.value)
const stateLabel = computed(() => INDEX_STATE_LABELS[index.value?.state ?? 'UNKNOWN'])
// The latest run of each index kind comes from the Jobs endpoint, one run per kind. The full history is on the Jobs page.
const LATEST_KINDS = ['index.rebuild', 'index.verify'] as const
const LATEST_LABELS: Record<(typeof LATEST_KINDS)[number], string> = {
  'index.rebuild': 'Latest rebuild',
  'index.verify': 'Latest verify'
}
const latestRuns = ref<Record<string, JobRun | null>>({})
// One card per kind, in the order of LATEST_KINDS, with the run it shows (null until one has been started).
const latestCards = computed(() => LATEST_KINDS.map(kind => ({
  kind,
  label: LATEST_LABELS[kind],
  run: latestRuns.value[kind] ?? null
})))
const progress = computed(() => {
  const status = index.value
  const run = status?.activeRun
  return status && run && run.kind === 'REBUILD' ? repositoryProgress(run, status.repositories) : []
})

const lagText = (seconds: number) => (seconds < 60
  ? countLabel(seconds, 'second')
  : countLabel(Math.floor(seconds / 60), 'minute'))

const percent = (fraction: number) => `${Math.round(fraction * 100)}%`

const countersText = (counters: IndexCounters) =>
  `Processed ${counters.processed}, upserted ${counters.upserted}, deleted ${counters.deleted}, errors ${counters.errors}`

// The HTTP status of a failed request, when the error carries one.
const statusOf = (error: unknown): number | undefined =>
  (error as { response?: { status?: number } } | null)?.response?.status

const stopPolling = () => {
  clearTimeout(pollTimer)
  pollTimer = undefined
}

const schedulePoll = () => {
  stopPolling()
  if (live.value) pollTimer = setTimeout(load, POLL_INTERVAL_MS)
}

// One request per kind for its latest run. A failed request leaves the cards as they were.
async function loadLatestRuns() {
  try {
    const pages = await Promise.all(LATEST_KINDS.map(kind => client.value.jobs.list({ kind, size: '1' })))
    latestRuns.value = Object.fromEntries(LATEST_KINDS.map((kind, position) => [kind, pages[position].data.items[0] ?? null]))
  } catch {
    // The status above still shows; the cards stay until the next status answer.
  }
}

async function load() {
  try {
    const { data } = await client.value.maven.searchIndex()
    index.value = normalizeIndexStatus(data)
    loadError.value = ''
    notAvailable.value = false
    notAllowed.value = false
    void loadLatestRuns()
  } catch (error) {
    const status = statusOf(error)
    index.value = null
    notAvailable.value = status === 404
    notAllowed.value = status === 403
    loadError.value = status === 404 || status === 403 ? '' : errorMessage(error)
  } finally {
    loaded.value = true
    schedulePoll()
  }
}

// Retry: the loading panel shows again while the status is read once more.
async function reload() {
  loadError.value = ''
  loaded.value = false
  await load()
}

// A refused start says why in words. The server's own message is not shown, as it names internals.
const refusalOf = (error: unknown): string => {
  const status = statusOf(error)
  if (status === 409) return 'A rebuild or verify run is already running. Wait for it to finish or cancel it.'
  if (status === 403) return 'Only managers can change the search index.'
  if (status === 404) return 'Search is turned off on this instance.'
  return `Could not start the run: ${errorMessage(error)}`
}

async function start(request: () => Promise<{ data: { runId: number } }>, label: string) {
  starting.value = true
  try {
    const { data } = await request()
    announcement.value = `${label} run ${data.runId} started.`
    await load()
  } catch (error) {
    if (statusOf(error) === 404) notAvailable.value = true
    createErrorToast(refusalOf(error))
  } finally {
    starting.value = false
  }
}

const rebuild = async (body: { repositories?: string[] }) => {
  rebuildOpen.value = false
  await start(() => client.value.maven.startIndexRebuild(body), 'Rebuild')
}

const verify = () => start(() => client.value.maven.startIndexVerify(), 'Verify')

async function cancel(runId: number) {
  try {
    // The client types the run id as a string, but the server sends it as a number (see searchIndex.ts).
    await client.value.maven.cancelIndexRun(runId as unknown as string)
    announcement.value = `Run ${runId} cancelled.`
    await load()
  } catch (error) {
    createErrorToast(`Could not cancel the run: ${errorMessage(error)}`)
  }
}

// The Cancel button is shown only while a run is live, so the run is looked up when the button is pressed.
const cancelActive = () => {
  const run = index.value?.activeRun
  if (run) cancel(run.id)
}

load()
onBeforeUnmount(stopPolling)
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="search-index-title"
    data-ingot-component="index-panel"
  >
    <MdCard
      variant="filled"
      class="flex flex-col gap-4"
    >
      <MdSection
        :level="2"
        title="Search index"
        description="The index behind artifact search. The outbox keeps it current; a rebuild reads storage again, and a verify compares a sample of rows with storage."
        heading-id="search-index-title"
      >
        <template
          v-if="index"
          #actions
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
        </template>
      </MdSection>

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
      <MdStatePanel
        v-else-if="loadError"
        state="error"
        data-test="index-error"
        title="Could not load the search index"
        :description="loadError"
        @retry="reload()"
      />
      <MdStatePanel
        v-else-if="!loaded"
        state="loading"
        data-test="index-loading"
      />

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

        <MdCard
          v-if="index.activeRun && live"
          variant="low"
          tone="container"
          class="flex flex-col gap-3"
          data-live-run
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="flex flex-col gap-1">
              <h3 class="md-title-small text-on-surface">
                {{ runKindLabel(index.activeRun.kind) }} running
              </h3>
              <p class="md-body-small text-on-surface-variant">
                {{ runTriggerLabel(index.activeRun.trigger) }}, started {{ formatDateTime(index.activeRun.startedAt) }}
              </p>
              <p class="md-body-small text-on-surface-variant">
                {{ countersText(index.activeRun.counters) }}
              </p>
            </div>
            <MdButton
              variant="outlined"
              @click="cancelActive"
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
        </MdCard>

        <div
          v-if="index.lastVerify"
          class="flex flex-col gap-2"
          data-verify
        >
          <h3 class="md-title-small text-on-surface">
            Last verify
          </h3>
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
          <h3 class="md-title-small text-on-surface">
            Latest runs
          </h3>
          <ul
            class="grid gap-3 sm:grid-cols-2"
            aria-label="Latest index runs"
          >
            <MdCard
              v-for="card in latestCards"
              :key="card.kind"
              as="li"
              variant="low"
              tone="container"
              :data-latest-run="card.kind"
              class="flex flex-col gap-1"
            >
              <span class="md-title-small text-on-surface">{{ card.label }}</span>
              <template v-if="card.run">
                <span class="md-body-small text-on-surface-variant">
                  {{ jobStateLabel(card.run.state) }}, started {{ formatDateTime(card.run.startedAt) }}
                </span>
                <span class="md-body-small text-on-surface-variant">
                  {{ card.run.label }}
                </span>
              </template>
              <span
                v-else
                class="md-body-small text-on-surface-variant"
              >No run has been started yet.</span>
            </MdCard>
          </ul>
          <RouterLink
            :to="{ name: 'jobs', query: { kind: 'index' } }"
            class="md-label-large self-start text-primary"
          >
            All runs
          </RouterLink>
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
