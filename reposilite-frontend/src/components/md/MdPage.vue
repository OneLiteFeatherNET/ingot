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
 * Material Design 3 page container. The side gutter comes from the shared container utility
 * (16px in a compact window, 24px from 600px), so the page adds no padding of its own.
 * `measure` caps the width at the reading measure, and `centred` centres the page in the window.
 * `spaced` adds the top and bottom spacing of a page (28 and 48 pixels), so every page owns the same spacing here
 * instead of writing it, as a margin or as a padding, on its own root. Headers and sections that only borrow the gutter leave it off.
 * Without `measure` the page takes the full width: the inline max-width lifts the width steps of Tailwind's container
 * (1536 pixels from the 2xl window), and leaves no max-w-* class on the page container.
 */
withDefaults(defineProps<{
  measure?: boolean
  centred?: boolean
  spaced?: boolean
}>(), {
  measure: false,
  centred: false,
  spaced: false
})

defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <div
    :class="['container', measure && 'max-w-md-reading', centred && 'mx-auto', spaced && 'pt-7 pb-12']"
    :style="measure ? undefined : { maxWidth: 'none' }"
    data-ingot-component="md-page"
  >
    <slot />
  </div>
</template>
