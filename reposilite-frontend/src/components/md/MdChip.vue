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
import { computed } from 'vue'

/*
 * Material Design 3 chip.
 *   assist: a single action, such as "Copy coordinates". Renders an anchor when `href` is set.
 *   filter: a toggle that narrows a set. It is a checkbox to assistive technology, so it
 *           reports `selected` through aria-checked, and a selected chip shows a check mark.
 */
const props = defineProps({
  variant: {
    type: String,
    default: 'assist',
    validator: (value) => ['assist', 'filter'].includes(value)
  },
  selected: {
    type: Boolean,
    default: false
  },
  href: {
    type: String,
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  },
  // How a filter chip tells assistive technology its state: as a checkbox (aria-checked), or as a
  // toggle button (aria-pressed), which suits a chip that is one of several choices.
  toggle: {
    type: String,
    default: 'checkbox',
    validator: (value) => ['checkbox', 'button'].includes(value)
  }
})

const isFilter = computed(() => props.variant === 'filter')
const isCheckbox = computed(() => isFilter.value && props.toggle === 'checkbox')
const isPressable = computed(() => isFilter.value && props.toggle === 'button')
const tag = computed(() => (props.href && !isFilter.value ? 'a' : 'button'))

const STATE = {
  assist: 'border border-outline bg-surface-container-low text-on-surface',
  filter: 'border border-outline bg-transparent text-on-surface',
  selected: 'border border-transparent bg-secondary-container text-on-secondary-container',
  disabled: 'border border-on-surface/12 bg-transparent text-on-surface/38'
}

const stateClass = computed(() => {
  if (props.disabled) return STATE.disabled
  if (isFilter.value) return props.selected ? STATE.selected : STATE.filter
  return STATE.assist
})
</script>

<template>
  <component
    :is="tag"
    :href="tag === 'a' && !disabled ? href : undefined"
    :type="tag === 'button' ? 'button' : undefined"
    :disabled="tag === 'button' ? disabled : undefined"
    :aria-disabled="tag === 'a' && disabled ? 'true' : undefined"
    :role="isCheckbox ? 'checkbox' : undefined"
    :aria-checked="isCheckbox ? String(selected) : undefined"
    :aria-pressed="isPressable ? String(selected) : undefined"
    :data-variant="variant"
    :class="[
      'md-state-layer md-focus-ring md-touch-target relative inline-flex h-8 items-center gap-2 rounded-corner-small px-4 md-label-large no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
      stateClass
    ]"
  >
    <svg
      v-if="isFilter && selected"
      class="size-[18px] shrink-0"
      viewBox="0 -960 960 960"
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="currentColor"
        d="M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z"
      />
    </svg>
    <span
      v-if="$slots.icon"
      class="inline-flex shrink-0 [&>svg]:size-[18px]"
      aria-hidden="true"
    >
      <slot name="icon" />
    </span>
    <slot />
  </component>
</template>
