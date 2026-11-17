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
 * Loading placeholder for text lines (`bar`) and avatars (`circle`). It carries no text, so it
 * is hidden from assistive technology; the loading state of the page is announced by the
 * element that wraps it (aria-busy). The fade runs only when the user allows motion.
 */
const props = withDefaults(defineProps<{
  shape?: 'bar' | 'circle'
  width?: 'sm' | 'md' | 'full'
}>(), {
  shape: 'bar',
  width: 'full'
})

const WIDTHS = Object.freeze({ sm: 'w-1/4', md: 'w-1/2', full: 'w-full' })
</script>

<template>
  <div
    v-if="shape === 'circle'"
    aria-hidden="true"
    data-shape="circle"
    class="size-10 shrink-0 rounded-corner-full bg-surface-container-highest motion-safe:animate-skeleton-fade-in"
    data-ingot-component="md-skeleton"
  />
  <div
    v-else
    aria-hidden="true"
    data-shape="bar"
    :class="['h-4 rounded-corner-small bg-surface-container-highest motion-safe:animate-skeleton-fade-in', WIDTHS[width]]"
    data-ingot-component="md-skeleton"
  />
</template>
