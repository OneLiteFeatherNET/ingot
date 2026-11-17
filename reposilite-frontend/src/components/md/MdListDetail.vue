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
 * Material Design 3 list-detail: a list beside the detail of the selected item. The two stack in one
 * column below the breakpoint and sit side by side from it, with the list at two fifths of the width.
 * The breakpoint is the window size class where the detail has room: medium (600px) or expanded (840px).
 */
withDefaults(defineProps<{
  breakpoint?: 'medium' | 'expanded'
}>(), {
  breakpoint: 'expanded'
})

defineSlots<{
  list?(): unknown
  detail?(): unknown
}>()

// Each class is written out in full, so the compiler finds it in the source.
const LAYOUT = {
  medium: { root: 'medium:grid-cols-5', list: 'medium:col-span-2', detail: 'medium:col-span-3' },
  expanded: { root: 'expanded:grid-cols-5', list: 'expanded:col-span-2', detail: 'expanded:col-span-3' }
}
</script>

<template>
  <div
    :data-breakpoint="breakpoint"
    :class="['grid grid-cols-1 gap-4', LAYOUT[breakpoint].root]"
    data-ingot-component="md-list-detail"
  >
    <div :class="['min-w-0', LAYOUT[breakpoint].list]">
      <slot name="list" />
    </div>
    <div :class="['min-w-0', LAYOUT[breakpoint].detail]">
      <slot name="detail" />
    </div>
  </div>
</template>
