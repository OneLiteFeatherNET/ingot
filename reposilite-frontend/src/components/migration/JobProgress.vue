<script setup>
/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// The progress of a job: its state in words, the bar, and the reason of a pause in words. The bar is indeterminate
// while the job reports no repositories, so no percentage is shown for an unknown total.
import { computed } from 'vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import { SETTLED_RESULTS, statusText, pauseText } from './jobText'

const props = defineProps({
  job: {
    type: Object,
    required: true
  },
  label: {
    type: String,
    required: true
  }
})

const total = computed(() => props.job.repositories?.length ?? 0)
const settled = computed(() => (props.job.repositories ?? []).filter(repository => SETTLED_RESULTS.includes(repository.status)).length)
const indeterminate = computed(() => total.value === 0)
const percent = computed(() => (indeterminate.value ? 0 : Math.round((settled.value / total.value) * 100)))
const reason = computed(() => (props.job.status === 'PAUSED' ? pauseText(props.job.pauseReason) : ''))
</script>

<template>
  <div class="flex flex-col gap-2">
    <p class="md-body-large text-on-surface">
      Status: {{ statusText(job.status) }}
      <template v-if="total > 0">
        ({{ settled }} of {{ total }} repositories done)
      </template>
    </p>
    <MdLinearProgress
      :value="percent"
      :indeterminate="indeterminate"
      :label="label"
    />
    <p
      v-if="reason"
      class="md-body-medium text-on-surface-variant"
    >
      {{ reason }}
    </p>
  </div>
</template>
