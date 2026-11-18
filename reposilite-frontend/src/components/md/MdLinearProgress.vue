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
import { computed } from 'vue'

/*
 * Material Design 3 linear progress indicator. `value` is a fraction from 0 to 1 and is
 * clamped into that range. With `indeterminate` the bar slides and aria-valuenow is left
 * out, because the duration is unknown. The motion is pure CSS, so it needs no timer.
 */
const props = withDefaults(defineProps<{
  value?: number
  indeterminate?: boolean
  label?: string
}>(), {
  value: 0,
  label: 'Progress'
})

const percent = computed(() => {
  const fraction = Number.isFinite(props.value) ? props.value : 0
  return Math.round(Math.min(1, Math.max(0, fraction)) * 100)
})
</script>

<template>
  <div
    role="progressbar"
    :aria-label="label"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-valuenow="indeterminate ? undefined : percent"
    class="relative h-1 w-full overflow-hidden rounded-corner-full bg-surface-container-highest"
    data-ingot-component="md-linear-progress"
  >
    <div
      v-if="indeterminate"
      class="absolute inset-y-0 left-0 w-2/5 rounded-corner-full bg-primary animate-md-linear-slide motion-reduce:animate-md-linear-slide-reduced"
    />
    <div
      v-else
      class="absolute inset-y-0 left-0 rounded-corner-full bg-primary transition-[width] duration-(--md-sys-motion-duration-medium2) ease-md-standard"
      :style="{ width: `${percent}%` }"
    />
  </div>
</template>
