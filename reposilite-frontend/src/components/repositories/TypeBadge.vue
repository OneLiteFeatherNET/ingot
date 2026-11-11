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

<script setup>
/*
 * The type of a repository in words: the label, with an icon that repeats it for sighted users. The icon is
 * decoration only, so a screen reader reads the label. A repository without a declared type also gets the word
 * "derived", in text, so the difference is not carried by colour alone.
 */
defineProps({
  // The type as rowsOf builds it: { key, label, derived }.
  type: {
    type: Object,
    required: true
  }
})

const TONES = {
  HOSTED: 'bg-tertiary-container text-on-tertiary-container',
  PROXY: 'bg-secondary-container text-on-secondary-container',
  GROUP: 'bg-primary-container text-on-primary-container',
  MIXED: 'border border-outline text-on-surface-variant'
}

const ICONS = {
  HOSTED: 'M4 4h16v4H4zm0 6h16v4H4zm0 6h16v4H4z',
  PROXY: 'M12 4l-8 8h5v8h6v-8h5z',
  GROUP: 'M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z',
  MIXED: 'M12 2L1 21h22z'
}
</script>

<template>
  <span class="inline-flex flex-wrap items-center gap-2">
    <span
      data-test="type-badge"
      :data-type="type.key"
      class="md-label-medium inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-corner-small px-2"
      :class="TONES[type.key]"
    >
      <svg
        viewBox="0 0 24 24"
        fill="currentColor"
        class="size-4"
        aria-hidden="true"
      >
        <path :d="ICONS[type.key]" />
      </svg>
      {{ type.label }}
    </span>
    <span
      v-if="type.derived"
      data-test="derived"
      class="md-label-medium text-on-surface-variant"
    >derived</span>
  </span>
</template>
