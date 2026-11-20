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
 * A summary and the body under it, on the native details element, so the browser keeps the open state, the keyboard
 * and the disclosure role. The chevron turns with the open state, on the motion token.
 *
 * Props:
 *   open  whether the body is shown (v-model:open). It starts closed.
 * Emits update:open when the browser opens or closes the body.
 * Slots: summary (the label of the disclosure) and default (the body).
 */
import { ref, watch } from 'vue'
import MdIcon from './MdIcon.vue'

const props = withDefaults(defineProps<{ open?: boolean }>(), { open: false })
const emit = defineEmits<{ 'update:open': [open: boolean] }>()

// The native state drives the chevron, so it also turns when the browser opens the body without a v-model.
const details = ref<HTMLDetailsElement | null>(null)
const isOpen = ref(props.open)
watch(
  () => props.open,
  (open) => {
    isOpen.value = open
    if (details.value && details.value.open !== open) details.value.open = open
  },
)

const onToggle = (event: Event) => {
  const open = (event.target as HTMLDetailsElement).open
  isOpen.value = open
  if (open !== props.open) emit('update:open', open)
}

const CHEVRON = 'M6 9l6 6 6-6'
const chevronClass = () => (isOpen.value ? 'rotate-180' : '')
</script>

<template>
  <details
    ref="details"
    :open="open"
    data-ingot-component="md-disclosure"
    @toggle="onToggle"
  >
    <summary class="md-state-layer md-title-small flex cursor-pointer items-center justify-between gap-2 rounded-corner-small py-3 text-on-surface focus-visible:outline-2 focus-visible:outline-primary">
      <span><slot name="summary" /></span>
      <MdIcon
        :path="CHEVRON"
        size="md"
        class="transition-transform duration-md-short3"
        :class="chevronClass()"
      />
    </summary>
    <div class="pb-4 text-on-surface-variant">
      <slot />
    </div>
  </details>
</template>
