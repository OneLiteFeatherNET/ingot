<script setup>
import { ref, defineAsyncComponent } from "vue"
import { createErrorToast } from '../../helpers/toast'
import { useSession } from "../../store/session"

const TimeSeriesChart = defineAsyncComponent(() => import('./TimeSeriesChart.vue'))

const { client } = useSession()
const statisticsEnabled = ref(false)
const resolvedSeries = ref()

client.value.statistics.allResolved()
  .then(response => response.data)
  .then(allResolved => {
    resolvedSeries.value = allResolved.repositories.map(repositoryStatistics => {
      return {
        name: repositoryStatistics.name,
        // [epoch millis, count] is what the time axis expects
        data: repositoryStatistics.data.map(record => [record.date, record.count])
      }
    })
    statisticsEnabled.value = allResolved.statisticsEnabled
  })
  .catch(error => {
    console.error(error)
    createErrorToast(`Cannot load statistics`)
  })
</script>

<template>
  <div v-if="statisticsEnabled && resolvedSeries">
    <h1 class="font-bold text-lg">Resolved requests</h1>
    <TimeSeriesChart class="pt-2" variant="area" :series="resolvedSeries" />
  </div>
</template>