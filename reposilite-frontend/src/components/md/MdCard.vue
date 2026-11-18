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
import { computed } from 'vue'
import type { Component } from 'vue'

/*
 * Material Design 3 card. A plain container: the content and its actions belong to the
 * slot. Pick the variant by how much the card must stand out from the page: elevated for
 * a card that floats, filled for grouped content, low for a flat tinted panel on the page,
 * outlined for a quiet container.
 * `padding` is `sm` (8px), `md` or `default` (16px, the one a card had before the sizes), `lg` (24px), or `none` for a card
 * whose content draws its own inset, such as a table or a code block.
 * `as` draws the card as another element or component, such as a router link, with the same surface.
 * `tone` sets the surface role (low, container, high or highest) in place of the variant's surface. `danger` keeps
 * the surface and draws the border in the error role. `edge` (none, outline or elevation) replaces the variant's edge.
 * The `header` slot holds the title and the supporting text. The `trailing` slot sits at the end of the same row, and the
 * row is drawn only when one of the two slots is given.
 * Without `tone` and `edge` the card renders exactly as the variant says.
 */
const props = withDefaults(defineProps<{
  variant?: 'elevated' | 'filled' | 'low' | 'outlined'
  padding?: 'default' | 'none' | 'sm' | 'md' | 'lg'
  as?: string | Component
  tone?: 'low' | 'container' | 'high' | 'highest' | 'danger'
  edge?: 'none' | 'outline' | 'elevation'
}>(), {
  variant: 'filled',
  padding: 'default',
  as: 'div',
  tone: undefined,
  edge: undefined
})

defineSlots<{
  default?(): unknown
  header?(): unknown
  trailing?(): unknown
}>()

const PADDINGS = {
  default: 'p-4',
  sm: 'p-2',
  md: 'p-4',
  lg: 'p-6',
  none: ''
}

// Each variant names its surface role and its edge. The edge is one of outline, elevation or none.
const VARIANTS = {
  elevated: { surface: 'bg-surface-container-low', edge: 'elevation' },
  filled: { surface: 'bg-surface-container-low', edge: 'outline' },
  low: { surface: 'bg-surface-container-low', edge: 'none' },
  outlined: { surface: 'bg-surface', edge: 'outline' }
} as const

const SURFACES = {
  low: 'bg-surface-container-low',
  container: 'bg-surface-container',
  high: 'bg-surface-container-high',
  highest: 'bg-surface-container-highest'
}

const EDGES = {
  none: [],
  outline: ['border', 'border-outline'],
  elevation: ['shadow-elevation-1']
}

const base = computed(() => VARIANTS[props.variant] ?? VARIANTS.filled)

// A surface tone replaces the variant's surface. The danger tone keeps the variant's surface and draws the error border.
const surfaceClass = computed(() => (props.tone && props.tone !== 'danger' ? SURFACES[props.tone] : base.value.surface))

const edgeClasses = computed(() => {
  const edge = props.edge ?? base.value.edge
  if (props.tone === 'danger') return ['border', 'border-error', ...(edge === 'elevation' ? EDGES.elevation : [])]
  return EDGES[edge]
})
</script>

<template>
  <component
    :is="as"
    :data-variant="variant"
    :class="['rounded-corner-medium text-on-surface', PADDINGS[padding], surfaceClass, edgeClasses]"
    data-ingot-component="md-card"
  >
    <div
      v-if="$slots.header || $slots.trailing"
      data-slot="header"
      class="mb-3 flex items-start gap-3"
    >
      <div class="min-w-0 flex-1">
        <slot name="header" />
      </div>
      <div
        v-if="$slots.trailing"
        data-slot="trailing"
        class="shrink-0"
      >
        <slot name="trailing" />
      </div>
    </div>
    <slot />
  </component>
</template>
