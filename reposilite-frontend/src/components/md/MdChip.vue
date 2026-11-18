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

/*
 * Material Design 3 chip.
 *   assist: a single action, such as "Copy coordinates". Renders an anchor when `href` is set.
 *   filter: a toggle that narrows a set. It is a checkbox to assistive technology, so it
 *           reports `selected` through aria-checked, and a selected chip shows a check mark.
 * `toggle` (Boolean) makes a filter chip a toggle button instead: it reports `selected` through aria-pressed.
 *           The string `toggle` of the earlier API (`button` for a toggle button, `checkbox`) still works.
 * `removeLabel` adds a trailing remove button named by the label, which emits `remove` once per click. The chip
 *           is then drawn as a span, so the remove button is not nested in a button.
 */
const props = withDefaults(defineProps<{
  variant?: 'assist' | 'filter'
  selected?: boolean
  href?: string
  disabled?: boolean
  toggle?: boolean | 'checkbox' | 'button'
  removeLabel?: string
}>(), {
  variant: 'assist',
  href: undefined,
  toggle: false,
  removeLabel: undefined
})

const emit = defineEmits<{
  remove: []
}>()

defineSlots<{
  icon?(): unknown
  default?(): unknown
}>()

const isFilter = computed(() => props.variant === 'filter')
const isPressable = computed(() => isFilter.value && (props.toggle === true || props.toggle === 'button'))
const isCheckbox = computed(() => isFilter.value && !isPressable.value)
const removable = computed(() => Boolean(props.removeLabel))
const tag = computed(() => {
  if (removable.value) return 'span'
  return props.href && !isFilter.value ? 'a' : 'button'
})

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
    data-ingot-component="md-chip"
  >
    <svg
      v-if="isFilter && selected"
      class="size-md-icon shrink-0"
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
      class="inline-flex shrink-0 md-icon-svg"
      aria-hidden="true"
    >
      <slot name="icon" />
    </span>
    <slot />
    <button
      v-if="removable"
      type="button"
      data-slot="remove"
      :aria-label="removeLabel"
      :disabled="disabled"
      class="md-state-layer md-focus-ring md-touch-target relative inline-flex size-6 shrink-0 items-center justify-center rounded-corner-full"
      @click="emit('remove')"
    >
      <svg
        class="size-4"
        viewBox="0 -960 960 960"
        aria-hidden="true"
        focusable="false"
      >
        <path
          fill="currentColor"
          d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
        />
      </svg>
    </button>
  </component>
</template>
