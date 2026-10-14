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
 * Material Design 3 card. A plain container: the content and its actions belong to the
 * slot. Pick the variant by how much the card must stand out from the page: elevated for
 * a card that floats, filled for grouped content, outlined for a quiet container.
 */
defineProps({
  variant: {
    type: String,
    default: 'filled',
    validator: (value) => ['elevated', 'filled', 'outlined'].includes(value)
  }
})

const VARIANTS = {
  elevated: 'bg-surface-container-low shadow-elevation-1',
  filled: 'bg-surface-container-highest',
  outlined: 'border border-outline-variant bg-surface'
}
</script>

<template>
  <div
    :data-variant="variant"
    :class="['rounded-corner-medium p-4 text-on-surface', VARIANTS[variant] ?? VARIANTS.filled]"
  >
    <slot />
  </div>
</template>
