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
import { computed, defineAsyncComponent, ref } from 'vue'
import { createErrorToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import ChartCard from './ChartCard.vue'

const TimeSeriesChart = defineAsyncComponent(() => import('./TimeSeriesChart.vue'))

// Past this many repositories the lines stop being tellable apart, whatever the palette
// does. The remainder is summed into one series rather than given a ninth colour that
// nobody can distinguish from the first eight.
const SERIES_LIMIT = 7

const { client } = useSession()
const statisticsEnabled = ref(null)
const series = ref(null)

const foldTail = (repositories) => {
  if (repositories.length <= SERIES_LIMIT + 1) {
    return repositories
  }

  const ranked = [...repositories].sort((left, right) =>
    right.data.reduce((sum, record) => sum + record.count, 0) -
    left.data.reduce((sum, record) => sum + record.count, 0))

  const head = ranked.slice(0, SERIES_LIMIT)
  const tail = ranked.slice(SERIES_LIMIT)
  const totals = new Map()

  for (const repository of tail) {
    for (const record of repository.data) {
      totals.set(record.date, (totals.get(record.date) ?? 0) + record.count)
    }
  }

  return [
    ...head,
    {
      name: `Other (${tail.length})`,
      data: [...totals.entries()]
        .sort(([left], [right]) => left - right)
        .map(([date, count]) => ({ date, count }))
    }
  ]
}

client.value.statistics.allResolved()
  .then(response => response.data)
  .then(allResolved => {
    statisticsEnabled.value = allResolved.statisticsEnabled
    series.value = foldTail(allResolved.repositories).map(repository => ({
      name: repository.name,
      // [epoch millis, count] is what the time axis expects
      data: repository.data.map(record => [record.date, record.count])
    }))
  })
  .catch(error => {
    console.error(error)
    statisticsEnabled.value = false
    createErrorToast('Cannot load statistics')
  })

const loading = computed(() => statisticsEnabled.value === null)

/*
 * This card used to disappear entirely when statistics were switched off, which left a gap
 * where a reader had every reason to expect a chart and no way to tell whether the feature
 * was off or the request had failed.
 */
const emptyMessage = computed(() => {
  if (loading.value) return ''
  if (!statisticsEnabled.value) {
    return 'Resolved request statistics are disabled for this instance. Enable them under Settings to start recording.'
  }
  return series.value?.length ? '' : 'No resolved requests recorded yet.'
})
</script>

<template>
  <ChartCard
    title="Resolved requests"
    subtitle="Requests served per repository, per day"
    :loading="loading"
    :empty-message="emptyMessage"
  >
    <TimeSeriesChart :series="series" height="260px" />
  </ChartCard>
</template>
