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
import { ref } from 'vue'

/*
 * A block of code of one or more lines, in the monospace role on a surface tint. The lines keep their spacing and are
 * never wrapped, so a long line scrolls sideways inside the block and the page does not scroll.
 *
 * Props:
 *   label  the accessible name of the scroll area, for example "Maven snippet". It is required, because the area is
 *          focusable and a screen reader must be told what it holds.
 * Slots: default (the code text) and actions (buttons in the top right corner). The actions stay outside the scroll
 * area, and the code keeps a right padding while they are shown, so they never cover a line.
 * Exposes codeElement, the code element, so that a caller can select its text.
 */
defineProps<{
  label: string
}>()

defineSlots<{
  default?(): unknown
  actions?(): unknown
}>()

const codeElement = ref<HTMLElement>()

defineExpose({ codeElement })
</script>

<template>
  <div
    class="relative md-code-medium rounded-corner-medium bg-surface-container-highest p-4 text-on-surface"
    data-ingot-component="md-code-block"
  >
    <div
      v-if="$slots.actions"
      class="absolute top-2 right-2 flex gap-1"
    >
      <slot name="actions" />
    </div>
    <div
      role="region"
      tabindex="0"
      :aria-label="label"
      class="md-focus-ring md-scrollbar max-w-full overflow-x-auto whitespace-pre break-normal"
      :class="$slots.actions ? 'pr-12' : ''"
    >
      <pre><code ref="codeElement"><slot /></code></pre>
    </div>
  </div>
</template>
