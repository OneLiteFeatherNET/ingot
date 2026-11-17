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
import MdIcon from './MdIcon.vue'

/*
 * Inline notice inside a view: a title, a body and an optional action. An error is announced at
 * once (role alert); every other tone is a polite status update, the same split as MdSnackbar.
 * The container takes a role background and the icon a text colour, because warning and success
 * colours exist as text colours only (DESIGN.md).
 */
const props = withDefaults(defineProps<{
  tone?: 'error' | 'warning' | 'tertiary' | 'info'
  title?: string
}>(), {
  tone: 'info',
  title: undefined
})

defineSlots<{
  default?(): unknown
  action?(): unknown
}>()

// Two glyphs: a triangle for the tones that warn, a circle for the tones that inform.
const WARNING_GLYPH = 'M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z'
const INFO_GLYPH = 'M12 8h.01M11 12h1v4h1m-1-9a9 9 0 1 0 0 18 9 9 0 0 0 0-18z'

const TONES = Object.freeze({
  error: { container: 'bg-error-container text-on-error-container', icon: 'text-error', path: WARNING_GLYPH },
  warning: { container: 'bg-surface-container-high text-on-surface', icon: 'text-warning', path: WARNING_GLYPH },
  tertiary: { container: 'bg-tertiary-container text-on-tertiary-container', icon: '', path: INFO_GLYPH },
  info: { container: 'bg-secondary-container text-on-secondary-container', icon: 'text-primary', path: INFO_GLYPH }
})

const style = computed(() => TONES[props.tone])
</script>

<template>
  <div
    :role="tone === 'error' ? 'alert' : 'status'"
    :data-tone="tone"
    :class="['flex items-start gap-3 rounded-corner-medium p-4', style.container]"
    data-ingot-component="md-alert"
  >
    <MdIcon
      :path="style.path"
      size="md"
      :class="['mt-0.5 shrink-0', style.icon]"
    />
    <div class="flex min-w-0 flex-1 flex-col gap-2">
      <p
        v-if="title"
        class="md-title-small m-0"
      >
        {{ title }}
      </p>
      <div
        v-if="$slots.default"
        class="md-body-medium"
      >
        <slot />
      </div>
      <div
        v-if="$slots.action"
        class="flex flex-wrap gap-2"
      >
        <slot name="action" />
      </div>
    </div>
  </div>
</template>
