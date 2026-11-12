<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { createErrorToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import { healthLabel, summariseCluster, uptimeOf } from './clusterStatus'
import FailuresSheet from './FailuresSheet.vue'
import InstanceHealthRow from './InstanceHealthRow.vue'
import ReadOnlyToggle from './ReadOnlyToggle.vue'
import StatTile from './StatTile.vue'

// Five seconds, where this used to ask once a second. Memory, threads and uptime do not
// carry a second's worth of news, and the dashboard is open on an operator's screen for
// hours at a time; a fifth of the requests is a fifth of the load for the same reading.
const POLL_INTERVAL_MS = 5000

const emit = defineEmits(['cluster'])
const { client } = useSession()
const instanceStatus = ref(null)
const failed = ref(false)
let timer = null

const requestStatus = () =>
  client.value.status.instance()
    .then(response => {
      instanceStatus.value = response.data
      failed.value = false
      // The trends below draw one series per instance from the same answer, so they get the
      // cluster from here. Without a bus it is null and they keep the local history.
      emit('cluster', response.data.cluster ?? null)
    })
    .catch(error => {
      console.error(error)
      // One toast, not one per poll: a server that is down stays down, and a stack of
      // identical toasts buries everything else on screen.
      if (!failed.value) {
        failed.value = true
        createErrorToast('Cannot load instance status')
      }
    })

// A hidden tab is nobody looking at the numbers. Polling through it costs the server the
// same as polling a visible one.
const onVisibilityChange = () => {
  if (!document.hidden) requestStatus()
}

onMounted(() => {
  requestStatus()
  timer = setInterval(() => {
    if (!document.hidden) requestStatus()
  }, POLL_INTERVAL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

const prettyUptime = (millis) => {
  const seconds = Math.floor(millis / 1000)
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)

  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m ${seconds % 60}s`
  return `${seconds}s`
}

const ratio = (used, max) => {
  const limit = Number(max)
  return Number.isFinite(limit) && limit > 0 ? Number(used) / limit : null
}

const memory = computed(() => {
  const status = instanceStatus.value
  return {
    value: `${Number(status.usedMemory).toFixed(1)} MB`,
    detail: `of ${Number(status.maxMemory)} MB available`,
    ratio: ratio(status.usedMemory, status.maxMemory)
  }
})

const threads = computed(() => {
  const status = instanceStatus.value
  return {
    value: `${status.usedThreads}`,
    detail: `of ${status.maxThreads} in the pool`,
    ratio: ratio(status.usedThreads, status.maxThreads)
  }
})

// The backend reports the newest published release next to the running one, and until now
// nothing on this page did anything with it. `<unknown>` is what it sends when the check
// could not run, which is not the same as being up to date.
const versionState = computed(() => {
  const { version, latestVersion } = instanceStatus.value

  if (!latestVersion || latestVersion === '<unknown>') {
    return { status: null, label: '' }
  }
  return latestVersion === version
    ? { status: 'good', label: 'Up to date' }
    : { status: 'warning', label: `${latestVersion} available` }
})

const failureState = (count) => count === 0
  ? { status: 'good', label: 'None recorded' }
  : { status: 'critical', label: count === 1 ? '1 failure recorded' : `${count} failures recorded` }

const failures = computed(() => failureState(instanceStatus.value.failuresCount))

// The cluster view exists only when the status carries instances. An empty list has nothing to
// summarise, so it shows nothing, and the tiles above still describe this instance.
const clusterView = computed(() => {
  const instances = instanceStatus.value?.cluster?.instances
  if (!instances?.length) return null

  const now = Date.now()
  const summary = summariseCluster(instances, now)
  const failuresState = failureState(summary.failuresCount)

  return {
    memory: {
      value: `${summary.usedMemory.toFixed(1)} MB`,
      detail: `of ${summary.maxMemory} MB available`,
      ratio: ratio(summary.usedMemory, summary.maxMemory)
    },
    threads: {
      value: `${summary.usedThreads}`,
      detail: `of ${summary.maxThreads} in the pool`,
      ratio: ratio(summary.usedThreads, summary.maxThreads)
    },
    failures: { value: `${summary.failuresCount}`, ...failuresState },
    uptime: prettyUptime(summary.uptime),
    version: summary.version.mixed
      ? { value: 'mixed', detail: `Versions: ${summary.version.versions.join(', ')}` }
      : { value: summary.version.label, detail: 'Same on every instance' },
    health: { value: healthLabel(summary.health), detail: 'Worst instance' },
    rows: instances.map(instance => ({
      id: instance.id,
      version: instance.version,
      uptime: prettyUptime(uptimeOf(instance, now)),
      memory: `${Number(instance.usedMemory).toFixed(1)} of ${instance.maxMemory} MB`,
      threads: `${instance.usedThreads} of ${instance.maxThreads} threads`,
      failures: failureState(instance.failuresCount).label,
      health: healthLabel(instance.health),
      stale: instance.stale
    }))
  }
})

// The tile opens the list only when there is something in it. An empty tile has no action, so it
// is not announced as a button either.
const failuresOpen = ref(false)
const hasFailures = computed(() => instanceStatus.value?.failuresCount > 0)
const openFailures = () => {
  if (hasFailures.value) failuresOpen.value = true
}

// Read-only mode comes with the status. The source says whether the environment forces it, which
// decides whether turning it off from the dashboard can work.
const readOnlySource = computed(() => instanceStatus.value?.readOnlySource === 'environment'
  ? 'Set by INGOT_READ_ONLY on this instance.'
  : 'Turned on from this dashboard or the API.')
</script>

<template>
  <!--
    The first request failed, so there is nothing to show and nothing coming. Placeholders
    here would promise a result that is not on its way; the card says so and offers a retry.
  -->
  <MdCard
    v-if="!instanceStatus && failed"
    variant="outlined"
    role="alert"
    class="flex flex-wrap items-center justify-between gap-3"
  >
    <p class="md-body-large text-on-surface">
      Cannot load instance status. The server may be down or unreachable.
    </p>
    <MdButton
      variant="tonal"
      @click="requestStatus()"
    >
      Retry
    </MdButton>
  </MdCard>

  <!--
    Loading placeholder with the same shape as the result, so the row does not jump into
    place once the first response lands.
  -->
  <div
    v-else-if="!instanceStatus"
    class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5"
    data-ingot-component="instance-status-panel"
  >
    <MdCard
      v-for="slot in 5"
      :key="`status-placeholder-${slot}`"
      variant="outlined"
      class="h-28"
    >
      <div class="skeleton-bars">
        <div class="h-3 w-20 rounded-corner-extra-small bg-surface-container-high" />
        <div class="mt-3 h-6 w-24 rounded-corner-extra-small bg-surface-container-high" />
      </div>
    </MdCard>
  </div>

  <div
    v-else
    class="flex flex-col gap-3"
    data-ingot-component="instance-status-panel"
  >
    <div
      v-if="instanceStatus.readOnly"
      data-testid="read-only-banner"
      data-ingot-part="read-only-banner"
      role="status"
      class="flex flex-col gap-1 rounded-corner-medium bg-tertiary-container p-4 text-on-tertiary-container"
    >
      <p class="md-title-small">
        Read-only mode is on
      </p>
      <p class="md-body-medium">
        This instance is read-only. Writes are refused until read-only mode is turned off.
      </p>
      <p class="md-body-small">
        {{ readOnlySource }}
      </p>
    </div>

    <ReadOnlyToggle @changed="requestStatus()" />

    <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
      <StatTile
        label="Used memory"
        :value="memory.value"
        :detail="memory.detail"
        :ratio="memory.ratio"
      />
      <StatTile
        label="Used threads"
        :value="threads.value"
        :detail="threads.detail"
        :ratio="threads.ratio"
      />
      <StatTile
        label="Failures"
        :value="`${instanceStatus.failuresCount}`"
        :status="failures.status"
        :status-label="failures.label"
        :role="hasFailures ? 'button' : undefined"
        :tabindex="hasFailures ? 0 : undefined"
        :aria-haspopup="hasFailures ? 'dialog' : undefined"
        :aria-label="hasFailures ? `Failures: ${instanceStatus.failuresCount} recorded. Show details` : undefined"
        :class="hasFailures ? 'cursor-pointer hover:border-outline md-focus-ring' : undefined"
        @click="openFailures"
        @keydown.enter.prevent="openFailures"
        @keydown.space.prevent="openFailures"
      />
      <StatTile
        label="Uptime"
        :value="prettyUptime(instanceStatus.uptime)"
        detail="Since the last restart"
      />
      <StatTile
        label="Version"
        :value="instanceStatus.version"
        :status="versionState.status"
        :status-label="versionState.label"
        link="https://github.com/OneLiteFeatherNET/ingot/releases"
      />
    </div>

    <section
      v-if="clusterView"
      class="flex flex-col gap-3"
      aria-labelledby="cluster-heading"
    >
      <h3
        id="cluster-heading"
        class="md-title-medium text-on-surface"
      >
        Cluster
      </h3>
      <div
        data-testid="cluster-summary"
        data-ingot-part="cluster-summary"
        class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6"
      >
        <StatTile
          label="Cluster memory"
          :value="clusterView.memory.value"
          :detail="clusterView.memory.detail"
          :ratio="clusterView.memory.ratio"
        />
        <StatTile
          label="Cluster threads"
          :value="clusterView.threads.value"
          :detail="clusterView.threads.detail"
          :ratio="clusterView.threads.ratio"
        />
        <StatTile
          label="Cluster failures"
          :value="clusterView.failures.value"
          :status="clusterView.failures.status"
          :status-label="clusterView.failures.label"
        />
        <StatTile
          label="Shortest uptime"
          :value="clusterView.uptime"
          detail="Since the most recent restart"
        />
        <StatTile
          label="Cluster version"
          :value="clusterView.version.value"
          :detail="clusterView.version.detail"
        />
        <StatTile
          label="Cluster health"
          :value="clusterView.health.value"
          :detail="clusterView.health.detail"
        />
      </div>
      <ul
        data-testid="cluster-instances"
        data-ingot-part="cluster-instances"
        aria-label="Instances in the cluster"
        class="flex flex-col gap-2"
      >
        <li
          v-for="row in clusterView.rows"
          :key="row.id"
          data-testid="cluster-instance"
          data-ingot-part="cluster-instance"
          class="flex flex-col gap-1 rounded-corner-medium border border-outline-variant p-3"
        >
          <div class="flex flex-wrap items-center gap-2">
            <span class="md-title-small text-on-surface">{{ row.id }}</span>
            <span
              v-if="row.stale"
              class="rounded-corner-full bg-surface-container-high px-2 py-0.5 md-label-small text-on-surface"
            >
              Stale
            </span>
          </div>
          <p class="md-body-small text-on-surface-variant">
            Version {{ row.version }}. Up {{ row.uptime }}. {{ row.memory }}, {{ row.threads }}. {{ row.failures }}. {{ row.health }}.
          </p>
        </li>
      </ul>
    </section>

    <InstanceHealthRow />
    <FailuresSheet
      v-model="failuresOpen"
      @cleared="requestStatus()"
    />
  </div>
</template>
