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
 * Material Design 3 page header: the page's only h1, an optional description, and three slots.
 * `trail` sits above the title, `actions` on the right, and `back` before the text column. The
 * text column is min-w-0, so a long title wraps inside it and never runs past the content edge.
 */
withDefaults(defineProps<{
  title: string
  description?: string
}>(), {
  description: undefined
})

defineSlots<{
  trail?(): unknown
  actions?(): unknown
  back?(): unknown
}>()
</script>

<template>
  <header
    class="flex items-start gap-4"
    data-ingot-component="md-page-header"
  >
    <div
      v-if="$slots.back"
      class="shrink-0"
    >
      <slot name="back" />
    </div>
    <div
      class="flex min-w-0 flex-1 flex-col gap-1"
      data-test="page-header-title"
    >
      <div
        v-if="$slots.trail"
        class="md-label-large text-on-surface-variant"
      >
        <slot name="trail" />
      </div>
      <h1 class="md-headline-medium break-words text-on-surface">
        {{ title }}
      </h1>
      <p
        v-if="description"
        class="md-body-large text-on-surface-variant"
      >
        {{ description }}
      </p>
    </div>
    <div
      v-if="$slots.actions"
      class="flex shrink-0 flex-wrap items-center gap-2"
    >
      <slot name="actions" />
    </div>
  </header>
</template>
