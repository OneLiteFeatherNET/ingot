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
import StatTile from './StatTile.vue'

// Five seconds, where this used to ask once a second. Memory, threads and uptime do not
// carry a second's worth of news, and the dashboard is open on an operator's screen for
// hours at a time; a fifth of the requests is a fifth of the load for the same reading.
const POLL_INTERVAL_MS = 5000

const { client } = useSession()
const instanceStatus = ref(null)
const failed = ref(false)
let timer = null

const requestStatus = () =>
  client.value.status.instance()
    .then(response => {
      instanceStatus.value = response.data
      failed.value = false
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

const failures = computed(() => {
  const count = instanceStatus.value.failuresCount
  return count === 0
    ? { status: 'good', label: 'None recorded' }
    : { status: 'critical', label: count === 1 ? '1 failure recorded' : `${count} failures recorded` }
})
</script>

<template>
  <!--
    Loading placeholder with the same shape as the result, so the row does not jump into
    place once the first response lands.
  -->
  <div v-if="!instanceStatus" class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
    <div
      v-for="slot in 5"
      :key="`status-placeholder-${slot}`"
      class="h-28 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900"
    >
      <div class="skeleton-bars">
        <div class="h-3 w-20 rounded bg-gray-200 dark:bg-gray-800" />
        <div class="mt-3 h-6 w-24 rounded bg-gray-200 dark:bg-gray-800" />
      </div>
    </div>
  </div>

  <div v-else class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
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
</template>
