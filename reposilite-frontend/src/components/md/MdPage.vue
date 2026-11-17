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
 */
withDefaults(defineProps<{
  measure?: boolean
  centred?: boolean
}>(), {
  measure: false,
  centred: false
})

defineSlots<{
  default?(): unknown
}>()
</script>

<template>
  <div
    :class="['container', measure && 'max-w-md-reading', centred && 'mx-auto']"
    data-ingot-component="md-page"
  >
    <slot />
  </div>
</template>
