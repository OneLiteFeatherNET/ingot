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
import type { Component } from 'vue'

/*
 * Material Design 3 card. A plain container: the content and its actions belong to the
 * slot. Pick the variant by how much the card must stand out from the page: elevated for
 * a card that floats, filled for grouped content, low for a flat tinted panel on the page,
 * outlined for a quiet container.
 * `padding` is `none` for a card whose content draws its own inset, such as a table or a code block.
 * `as` draws the card as another element or component, such as a router link, with the same surface.
 */
withDefaults(defineProps<{
  variant?: 'elevated' | 'filled' | 'low' | 'outlined'
  padding?: 'default' | 'none'
  as?: string | Component
}>(), {
  variant: 'filled',
  padding: 'default',
  as: 'div'
})

defineSlots<{
  default?(): unknown
}>()

const PADDINGS = {
  default: 'p-4',
  none: ''
}

const VARIANTS = {
  elevated: 'bg-surface-container-low shadow-elevation-1',
  filled: 'bg-surface-container-highest',
  low: 'bg-surface-container-low',
  outlined: 'border border-outline-variant bg-surface'
}
</script>

<template>
  <component
    :is="as"
    :data-variant="variant"
    :class="['rounded-corner-medium text-on-surface', PADDINGS[padding], VARIANTS[variant] ?? VARIANTS.filled]"
    data-ingot-component="md-card"
  >
    <slot />
  </component>
</template>
