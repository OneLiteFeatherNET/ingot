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

<script lang="ts">
// A status dot in front of its label. Every status on the dashboard uses this one pair, so the
// dot sits on the same centre line everywhere. The caller names a status of a closed set, and the
// label picks its colour token, so no caller passes a colour of its own. Declared in module scope,
// because the prop validator below reads it.
type Status = 'good' | 'warning' | 'critical' | 'neutral'

const DOT_CLASSES: Record<Status, string> = {
  good: 'bg-success',
  warning: 'bg-warning',
  critical: 'bg-error',
  neutral: 'bg-outline'
}
</script>

<script setup lang="ts">
import type { PropType } from 'vue'

defineProps({
  status: {
    type: String as PropType<Status>,
    required: true,
    validator: (value: string) => value in DOT_CLASSES
  }
})
</script>

<template>
  <span
    class="inline-flex items-center gap-1.5"
    data-ingot-component="status-label"
  >
    <!--
      items-center centres the dot on the line box, and the x-height of the text sits about half a
      pixel lower than that middle. The nudge moves the dot onto the x-height centre.
    -->
    <span
      class="relative top-[0.5px] size-2 shrink-0 rounded-corner-full"
      :class="DOT_CLASSES[status]"
      aria-hidden="true"
    />
    <span><slot /></span>
  </span>
</template>
