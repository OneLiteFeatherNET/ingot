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
 * The type of a repository in words: the label, with an icon that repeats it for sighted users. The icon is
 * decoration only, so a screen reader reads the label. A repository without a declared type also gets the word
 * "derived", in text, so the difference is not carried by colour alone.
 */
import type { RepositoryRow } from './repositoryRows'
import MdBadge from '../md/MdBadge.vue'

defineProps<{
  // The type as rowsOf builds it: { key, label, derived }.
  type: RepositoryRow['type']
}>()

const TONES: Record<string, 'hosted' | 'proxy' | 'group' | 'mixed'> = {
  HOSTED: 'hosted',
  PROXY: 'proxy',
  GROUP: 'group',
  MIXED: 'mixed'
}

const ICONS: Record<string, string> = {
  HOSTED: 'M4 4h16v4H4zm0 6h16v4H4zm0 6h16v4H4z',
  PROXY: 'M12 4l-8 8h5v8h6v-8h5z',
  GROUP: 'M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z',
  MIXED: 'M12 2L1 21h22z'
}
</script>

<template>
  <span
    class="inline-flex flex-wrap items-center gap-2"
    data-ingot-component="type-badge"
  >
    <MdBadge
      data-test="type-badge"
      :data-type="type.key"
      :tone="TONES[type.key]"
    >
      <template #leading>
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
          class="size-4"
          aria-hidden="true"
        >
          <path :d="ICONS[type.key]" />
        </svg>
      </template>
      {{ type.label }}
    </MdBadge>
    <span
      v-if="type.derived"
      data-test="derived"
      class="md-label-medium text-on-surface-variant"
    >derived</span>
  </span>
</template>
