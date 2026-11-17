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
 * Material Design 3 section: a titled group of content on a page. The title is a heading at level 2
 * for a section of a page, or level 3 for a section inside another one. The actions sit beside the
 * title and the content follows the header.
 */
withDefaults(defineProps<{
  title: string
  description?: string
  level?: 2 | 3
}>(), {
  description: undefined,
  level: 2
})

defineSlots<{
  actions?(): unknown
  default?(): unknown
}>()
</script>

<template>
  <section class="flex flex-col gap-4 text-on-surface" data-ingot-component="md-section">
    <div data-test="section-header" class="flex flex-wrap items-end justify-between gap-4">
      <div class="min-w-0">
        <component
          :is="level === 3 ? 'h3' : 'h2'"
          :class="level === 3 ? 'md-title-medium' : 'md-title-large'"
        >{{ title }}</component>
        <p
          v-if="description"
          data-test="section-description"
          class="md-body-medium text-on-surface-variant"
        >{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </section>
</template>
