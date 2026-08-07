<script setup>
import { ref, computed, defineAsyncComponent } from "vue"
import { useSession } from "../../store/session"
import { createErrorToast } from '../../helpers/toast'

const TimeSeriesChart = defineAsyncComponent(() => import('./TimeSeriesChart.vue'))

const props = defineProps({
  selectedTab: {
    type: String,
    required: true
  }
})

const { client } = useSession()
const statusSnapshots = ref()
const statusSnapshotsSeries = computed(() => {
  return [
    {
      name: 'Used memory (MB)',
      data: statusSnapshots.value.map(record => [record.at, record.memory])
    },
    {
      name: 'Used threads',
      data: statusSnapshots.value.map(record => [record.at, record.threads])
    }
  ]
})

function requestStatus() {
  if (props.selectedTab == 'Dashboard') {
    client.value.status.snapshots()
      .then(response => response.data)
      .then(snapshotsData => {
        statusSnapshots.value = snapshotsData
        setTimeout(requestStatus, 1000 * 30)
      })
      .catch(error => {
        console.error(error)
        createErrorToast(`Cannot load status snapshots statistics`)
      })
  }
}
requestStatus()
</script>

<template>
  <div v-if="statusSnapshots">
    <h1 class="font-bold pt-6 text-lg">Resources</h1>
    <TimeSeriesChart class="pt-1" :series="statusSnapshotsSeries" />
  </div>
</template>