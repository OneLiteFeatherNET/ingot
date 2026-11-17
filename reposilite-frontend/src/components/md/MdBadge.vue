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
 * A static label for a kind or a state, such as a repository type or a token scope. It is not an action, so it
 * is not a chip. The label is the text in the default slot: the tone only tints it, so the meaning is always in
 * the words, never in the colour alone.
 *
 * Props:
 *   tone  neutral (the default), accent, success, error, hosted, proxy, group or mixed. Each tone draws one MD3
 *         role, and the outlined tones draw a border as well.
 * Slots: leading (a decorative icon before the label) and default (the label).
 */
withDefaults(defineProps<{
  tone?: 'neutral' | 'accent' | 'success' | 'error' | 'hosted' | 'proxy' | 'group' | 'mixed'
}>(), {
  tone: 'neutral'
})

defineSlots<{
  leading?(): unknown
  default?(): unknown
}>()

// One height for every tone (h-6, the height of the search pill), and one type role.
const TONES = Object.freeze({
  neutral: 'border border-outline text-on-surface-variant',
  accent: 'bg-secondary-container text-on-secondary-container',
  success: 'border border-outline text-success',
  error: 'bg-error-container text-on-error-container',
  hosted: 'bg-tertiary-container text-on-tertiary-container',
  proxy: 'bg-secondary-container text-on-secondary-container',
  group: 'bg-primary-container text-on-primary-container',
  mixed: 'border border-outline text-on-surface-variant'
})
</script>

<template>
  <span
    :data-tone="tone"
    class="md-label-medium inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-corner-small px-2"
    :class="TONES[tone]"
    data-ingot-component="md-badge"
  >
    <slot name="leading" />
    <slot />
  </span>
</template>
