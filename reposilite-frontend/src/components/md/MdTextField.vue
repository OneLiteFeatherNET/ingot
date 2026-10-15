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
import { computed, useAttrs, useId } from 'vue'

/*
 * Material Design 3 text field, filled or outlined. The label is always a real <label for>
 * tied to the input. It floats above the value, or when the input is focused, through the
 * peer pattern: the input carries a single space as its placeholder, which is what
 * :placeholder-shown tests for, so no real placeholder text is shown.
 *
 * Supporting text and error text share one slot in the layout. When `error` is set the
 * error text replaces the supporting text, and aria-invalid is set. Both are linked to the
 * input through aria-describedby.
 *
 * Attributes that are not props, such as min, max, step, pattern, maxlength or inputmode,
 * go to the input, where the browser enforces them. Class and style stay on the wrapper.
 */
defineOptions({ inheritAttrs: false })

const attrs = useAttrs()
const forwardedAttrs = () =>
  Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style'))

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    required: true
  },
  variant: {
    type: String,
    default: 'filled',
    validator: (value) => ['filled', 'outlined'].includes(value)
  },
  supportingText: {
    type: String,
    default: ''
  },
  error: {
    type: Boolean,
    default: false
  },
  errorText: {
    type: String,
    default: ''
  },
  id: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  name: {
    type: String,
    default: undefined
  },
  autocomplete: {
    type: String,
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  },
  required: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const generatedId = useId()
const inputId = computed(() => props.id || `md-text-field-${generatedId}`)
const helperId = computed(() => `${inputId.value}-helper`)
const helperText = computed(() => (props.error ? props.errorText : props.supportingText))
const describedBy = computed(() => (helperText.value ? helperId.value : undefined))

// Every class name is spelled out in full so that Tailwind finds it in this file.
// The focus indicator is 2px in both variants: the MD3 reference sets it to 2px. Filled is a
// 1px primary border plus a 1px inset shadow. Outlined is a 1px primary border plus a 1px
// inset shadow.
// The disabled rules follow MD3 (`_md-comp-filled-text-field.scss` and
// `_md-comp-outlined-text-field.scss`, v0_192): the filled container is on-surface at 4%,
// the filled active indicator and the outlined outline are on-surface at 38% and 12%, and
// the input text is on-surface at 38%. The label, supporting text and icons are coloured
// from props in the script, because they are not after the input in the DOM.
const FIELD = {
  filled: {
    default: 'h-14 w-full rounded-t-corner-extra-small bg-surface-container-highest pt-4 md-body-large text-on-surface border-b border-on-surface-variant outline-none focus:border-primary focus:shadow-[inset_0_-1px_0_0_var(--md-sys-color-primary)] disabled:bg-on-surface/4 disabled:border-on-surface/38 disabled:text-on-surface/38',
    error: 'h-14 w-full rounded-t-corner-extra-small bg-surface-container-highest pt-4 md-body-large text-on-surface border-b border-error outline-none shadow-[inset_0_-1px_0_0_var(--md-sys-color-error)] disabled:bg-on-surface/4 disabled:border-on-surface/38 disabled:text-on-surface/38 disabled:shadow-none'
  },
  outlined: {
    default: 'h-14 w-full rounded-corner-extra-small border border-outline bg-transparent md-body-large text-on-surface outline-none focus:border-primary focus:shadow-[inset_0_0_0_1px_var(--md-sys-color-primary)] disabled:border-on-surface/12 disabled:text-on-surface/38',
    error: 'h-14 w-full rounded-corner-extra-small border border-error bg-transparent md-body-large text-on-surface outline-none shadow-[inset_0_0_0_1px_var(--md-sys-color-error)] disabled:border-on-surface/12 disabled:text-on-surface/38 disabled:shadow-none'
  }
}

// The resting label sits in the middle of the field. A floated label moves to the top edge.
const FLOAT = {
  filled: 'peer-focus:top-2 peer-focus:translate-y-0 peer-focus:md-label-medium peer-[:not(:placeholder-shown)]:top-2 peer-[:not(:placeholder-shown)]:translate-y-0 peer-[:not(:placeholder-shown)]:md-label-medium',
  outlined: 'peer-focus:top-0 peer-focus:bg-[var(--md-field-container,var(--md-sys-color-surface))] peer-focus:px-1 peer-focus:md-label-medium peer-[:not(:placeholder-shown)]:top-0 peer-[:not(:placeholder-shown)]:bg-[var(--md-field-container,var(--md-sys-color-surface))] peer-[:not(:placeholder-shown)]:px-1 peer-[:not(:placeholder-shown)]:md-label-medium'
}

const LABEL_COLOUR = {
  default: 'text-on-surface-variant peer-focus:text-primary',
  error: 'text-error peer-focus:text-error',
  disabled: 'text-on-surface/38'
}

const variantKey = computed(() => (props.variant === 'outlined' ? 'outlined' : 'filled'))
const stateKey = computed(() => (props.error ? 'error' : 'default'))

const fieldClass = computed(() => FIELD[variantKey.value][stateKey.value])

const labelClass = computed(() => [
  'pointer-events-none absolute top-1/2 -translate-y-1/2 md-body-large transition-all duration-(--md-sys-motion-duration-short4) ease-md-standard',
  FLOAT[variantKey.value],
  props.disabled ? LABEL_COLOUR.disabled : LABEL_COLOUR[stateKey.value]
])

// Icons and supporting text are outside the input's peer scope, so they take their colour
// from the props. A disabled field uses on-surface at 38% for all of them.
const iconClass = computed(() => (props.disabled ? 'text-on-surface/38' : 'text-on-surface-variant'))

const helperClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  return props.error ? 'text-error' : 'text-on-surface-variant'
})
</script>

<template>
  <div
    class="flex w-full flex-col gap-1"
    :class="attrs.class"
    :style="attrs.style"
  >
    <div class="relative">
      <span
        v-if="$slots['leading-icon']"
        class="absolute left-3 top-1/2 -translate-y-1/2 [&>svg]:size-6"
        :class="iconClass"
        aria-hidden="true"
      >
        <slot name="leading-icon" />
      </span>
      <input
        v-bind="forwardedAttrs()"
        :id="inputId"
        :class="[
          'peer',
          fieldClass,
          $slots['leading-icon'] ? 'pl-12' : 'pl-4',
          $slots['trailing-icon'] ? 'pr-12' : 'pr-4'
        ]"
        :value="modelValue"
        :type="type"
        :name="name"
        :autocomplete="autocomplete"
        :disabled="disabled"
        :required="required"
        placeholder=" "
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        @input="emit('update:modelValue', $event.target.value)"
      >
      <label
        :for="inputId"
        :class="[
          ...labelClass,
          $slots['leading-icon'] ? 'left-12' : 'left-4'
        ]"
      >{{ label }}</label>
      <span
        v-if="$slots['trailing-icon']"
        class="absolute right-3 top-1/2 -translate-y-1/2 [&>svg]:size-6"
        :class="iconClass"
        aria-hidden="true"
      >
        <slot name="trailing-icon" />
      </span>
    </div>
    <p
      v-if="helperText"
      :id="helperId"
      class="md-body-small px-4"
      :class="helperClass"
    >
      {{ helperText }}
    </p>
  </div>
</template>
