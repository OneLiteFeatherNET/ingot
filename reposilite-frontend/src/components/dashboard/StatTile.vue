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
import { computed } from 'vue'
import { STATUS } from '../../store/palette'
import LinkIcon from '../icons/LinkIcon.vue'

const props = defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: String,
    required: true
  },
  /** Small print under the value: a limit, a comparison, whatever gives the number scale. */
  detail: {
    type: String,
    default: ''
  },
  /**
   * Share of a limit, 0 to 1. Present it and the tile grows a meter, which answers "how
   * close to full" at a glance where "151.5 of 512 MB" makes the reader do the division.
   */
  ratio: {
    type: Number,
    default: null
  },
  /** One of the reserved state names. Always shown with `statusLabel`, never colour alone. */
  status: {
    type: String,
    default: null,
    validator: (value) => value === null || value in STATUS
  },
  statusLabel: {
    type: String,
    default: ''
  },
  link: {
    type: String,
    default: ''
  }
})

const meterWidth = computed(() =>
  props.ratio === null ? '0%' : `${Math.min(100, Math.max(0, props.ratio * 100))}%`)

// The meter track and its fill are steps of one hue, so the fill reads as "more of the
// same" rather than as a second category. It only turns to a state colour once the reading
// is one the operator has to act on.
const meterColor = computed(() => {
  if (props.ratio === null) return null
  if (props.ratio >= 0.9) return STATUS.critical
  if (props.ratio >= 0.75) return STATUS.warning
  return '#2a78d6'
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
    <div class="flex items-center gap-1.5">
      <span class="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">{{ label }}</span>
      <a
        v-if="link"
        :href="link"
        target="_blank"
        rel="noopener"
        class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
        :aria-label="`${label}: open in a new tab`"
      >
        <LinkIcon class="w-3" />
      </a>
    </div>

    <!--
      Tabular figures on purpose: several of these values are replaced while the reader is
      looking at them, and proportional digits make the whole tile twitch on every poll.
    -->
    <p class="pt-1 text-2xl font-semibold tabular-nums text-gray-900 dark:text-gray-50">{{ value }}</p>

    <div
      v-if="ratio !== null"
      class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-800"
      role="meter"
      :aria-valuenow="Math.round(ratio * 100)"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="`${label}: ${Math.round(ratio * 100)} percent of the limit`"
    >
      <div class="h-full rounded-full" :style="{ width: meterWidth, backgroundColor: meterColor }" />
    </div>

    <p v-if="detail" class="pt-1.5 text-xs text-gray-500 dark:text-gray-400">{{ detail }}</p>

    <p v-if="status" class="flex items-center gap-1.5 pt-1.5 text-xs text-gray-600 dark:text-gray-300">
      <span class="h-2 w-2 shrink-0 rounded-full" :style="{ backgroundColor: STATUS[status] }" aria-hidden="true" />
      {{ statusLabel }}
    </p>
  </div>
</template>
