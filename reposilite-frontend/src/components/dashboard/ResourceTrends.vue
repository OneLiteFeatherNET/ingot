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
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { useSession } from '../../store/session'
import { createErrorToast } from '../../helpers/toast'
import ChartCard from './ChartCard.vue'

const TimeSeriesChart = defineAsyncComponent(() => import('./TimeSeriesChart.vue'))

const POLL_INTERVAL_MS = 30000

const { client } = useSession()
const snapshots = ref(null)
const failed = ref(false)
let timer = null

const requestSnapshots = () =>
  client.value.status.snapshots()
    .then(response => {
      snapshots.value = response.data
      failed.value = false
    })
    .catch(error => {
      console.error(error)
      if (!failed.value) {
        failed.value = true
        createErrorToast('Cannot load status snapshots statistics')
      }
    })

onMounted(() => {
  requestSnapshots()
  timer = setInterval(() => {
    if (!document.hidden) requestSnapshots()
  }, POLL_INTERVAL_MS)
})

onBeforeUnmount(() => clearInterval(timer))

/*
 * Memory and threads used to share one chart and therefore one y-axis, which put megabytes
 * and a thread count on the same scale. Whichever number happened to be larger drew the
 * taller line, and the comparison the reader took from that shape meant nothing. They are
 * two charts now, stacked, sharing a time axis: the trends stay comparable, the magnitudes
 * no longer pretend to be.
 */
const memorySeries = computed(() => [{
  name: 'Used memory',
  data: snapshots.value.map(record => [record.at, record.memory])
}])

const threadsSeries = computed(() => [{
  name: 'Used threads',
  data: snapshots.value.map(record => [record.at, record.threads])
}])

const loading = computed(() => snapshots.value === null)
const empty = computed(() => snapshots.value !== null && snapshots.value.length === 0)
</script>

<template>
  <div class="grid gap-3 lg:grid-cols-2">
    <ChartCard
      title="Memory"
      subtitle="Megabytes in use, recent history"
      :loading="loading"
      :empty-message="empty ? 'No snapshots recorded yet. The first one appears a few minutes after startup.' : ''"
    >
      <TimeSeriesChart :series="memorySeries" unit="MB" />
    </ChartCard>

    <ChartCard
      title="Threads"
      subtitle="Threads in use, recent history"
      :loading="loading"
      :empty-message="empty ? 'No snapshots recorded yet. The first one appears a few minutes after startup.' : ''"
    >
      <TimeSeriesChart :series="threadsSeries" />
    </ChartCard>
  </div>
</template>
