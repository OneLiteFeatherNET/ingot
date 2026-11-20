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
 * The chrome that the field primitives share. It holds the leading and trailing slots at one inset of 12px,
 * the default slot (the input and its label), and one helper row under the field: the helper line, with the
 * id `<input-id>-helper`, and the counter slot beside it.
 *
 * The default slot receives `describedBy`, the helper id when there is helper text and undefined otherwise,
 * and `invalid`, so the input binds aria-describedby and aria-invalid itself. The root is `display: contents`:
 * it takes no box, so the primitive that wraps it keeps its own layout.
 *
 * A trailing slot is interactive by default, as a visibility toggle is. `decorativeTrailing` marks it as an icon
 * that only shows the field's behaviour, such as a dropdown chevron: it is hidden from assistive technology, it
 * takes no clicks so the field under it still opens, and it follows the focus role of the field.
 */
const props = withDefaults(defineProps<{
  inputId: string
  helperText?: string
  invalid?: boolean
  disabled?: boolean
  decorativeTrailing?: boolean
}>(), {
  helperText: '',
  invalid: false,
  disabled: false,
  decorativeTrailing: false
})

const helperId = computed(() => `${props.inputId}-helper`)
const describedBy = computed(() => (props.helperText ? helperId.value : undefined))
const iconClass = computed(() => (props.disabled ? 'text-on-surface/38' : 'text-on-surface-variant'))

// A decorative trailing icon follows the focus role of the field, as MD3 does, unless the field is invalid.
const trailingClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  if (!props.decorativeTrailing) return 'text-on-surface-variant'
  return props.invalid ? 'text-on-surface-variant' : 'text-on-surface-variant peer-focus:text-primary'
})
const trailingMode = computed(() => (props.decorativeTrailing ? 'pointer-events-none' : ''))

const helperClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  return props.invalid ? 'text-error' : 'text-on-surface-variant'
})
</script>

<template>
  <div
    class="contents"
    data-ingot-component="md-field-shell"
  >
    <div class="relative">
      <span
        v-if="$slots.leading"
        class="absolute left-3 top-1/2 -translate-y-1/2 md-field-icon"
        :class="iconClass"
        aria-hidden="true"
      >
        <slot name="leading" />
      </span>
      <slot
        :described-by="describedBy"
        :invalid="invalid"
      />
      <span
        v-if="$slots.trailing"
        class="absolute right-3 top-1/2 -translate-y-1/2 md-field-icon"
        :class="[trailingClass, trailingMode]"
        :aria-hidden="decorativeTrailing ? 'true' : undefined"
      >
        <!-- Not aria-hidden unless decorative: the trailing slot may hold an interactive button, such as a visibility toggle. -->
        <slot name="trailing" />
      </span>
    </div>
    <slot name="below" />
    <div
      v-if="helperText || $slots.counter"
      class="flex items-start justify-between gap-2 px-4"
    >
      <p
        :id="helperId"
        class="md-body-small whitespace-pre-line"
        :class="helperClass"
      >
        {{ helperText }}
      </p>
      <slot name="counter" />
    </div>
  </div>
</template>
