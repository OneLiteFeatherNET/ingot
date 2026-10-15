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
import { VueFinalModal } from 'vue-final-modal'

/*
 * The frame of the identity dialogs: a vue-final-modal teleported to the body, a 24px side
 * inset, capped at 560px and never below 280px (DESIGN.md, "Rules for contributors").
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   labelledby  the id of the heading inside the slot, for the dialog's accessible name
 *   dismissable false keeps the dialog open on Escape and on a click outside, so that only
 *               its own buttons close it
 * Emits update:modelValue, opened and closed.
 */
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  labelledby: {
    type: String,
    required: true
  },
  dismissable: {
    type: Boolean,
    default: true
  }
})

defineEmits(['update:modelValue', 'opened', 'closed'])

// A scrim in the role colour. The modal's own stylesheet paints the overlay in a fixed black
// and is not layered, so the colour goes in as an inline style that wins over it.
const overlayStyle = {
  backgroundColor: 'color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)'
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    class="flex items-center justify-center"
    content-class="w-full px-6"
    teleport-to="body"
    :overlay-style="overlayStyle"
    :aria-labelledby="labelledby"
    :click-to-close="dismissable"
    :esc-to-close="dismissable"
    @update:model-value="$emit('update:modelValue', $event)"
    @opened="$emit('opened')"
    @closed="$emit('closed')"
  >
    <div class="mx-auto max-h-[90dvh] w-full min-w-70 max-w-140 overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3">
      <slot />
    </div>
  </VueFinalModal>
</template>
