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
/*
 * Material Design 3 key-value list: rows of a label and its value, as the details of a record. The
 * rows come from `items`, and the default slot adds rows of its own after them, as a term (dt) and a
 * description (dd). An item may carry a testId, which becomes the data-test of its description, and mono, which sets its value in the monospace face.
 * Every row sits on the one grid of the list, so the labels line up.
 */
withDefaults(defineProps<{
  items?: { label: string; value: string; testId?: string; mono?: boolean }[]
}>(), {
  items: () => []
})

defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <dl
    class="grid grid-cols-2 gap-4 text-on-surface"
    data-ingot-component="md-key-value-list"
  >
    <div
      v-for="item in items"
      :key="item.label"
      data-test="key-value-row"
      class="contents"
    >
      <dt class="md-label-large text-on-surface-variant">
        {{ item.label }}
      </dt>
      <dd
        class="m-0 min-w-0 wrap-break-word md-body-large"
        :class="{ 'font-mono': item.mono }"
        :data-test="item.testId"
      >
        {{ item.value }}
      </dd>
    </div>
    <slot />
  </dl>
</template>
