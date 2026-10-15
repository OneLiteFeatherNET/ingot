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
 * Material Design 3 outlined select. It is a native <select>, so the browser supplies the
 * keyboard and the mobile picker. The label always floats on the top edge, because a select
 * always shows a value or an empty box. Supporting text sits under the field, and error text
 * replaces it, as in MdTextField.
 *
 * Attributes that are not props go to the <select>. Class and style stay on the wrapper.
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
  /* Each option is { value, label }. The value is what modelValue holds. */
  options: {
    type: Array,
    required: true
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
const inputId = computed(() => props.id || `md-select-${generatedId}`)
const helperId = computed(() => `${inputId.value}-helper`)
const helperText = computed(() => (props.error ? props.errorText : props.supportingText))
const describedBy = computed(() => (helperText.value ? helperId.value : undefined))

// Every class name is spelled out in full so that Tailwind finds it in this file. The outline
// and the 2px focus indicator follow MdTextField: a 1px border plus a 1px inset shadow.
const fieldClass = computed(() => (props.error
  ? 'border-error shadow-[inset_0_0_0_1px_var(--md-sys-color-error)]'
  : 'border-outline focus:border-primary focus:shadow-[inset_0_0_0_1px_var(--md-sys-color-primary)]'))

const labelClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  return props.error ? 'text-error' : 'text-on-surface-variant peer-focus:text-primary'
})

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
      <select
        v-bind="forwardedAttrs()"
        :id="inputId"
        :value="modelValue"
        :required="required"
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        :aria-describedby="describedBy"
        class="peer h-14 w-full appearance-none rounded-corner-extra-small border bg-transparent pl-4 pr-12 md-body-large text-on-surface outline-none disabled:border-on-surface/12 disabled:text-on-surface/38"
        :class="fieldClass"
        @change="emit('update:modelValue', $event.target.value)"
      >
        <option
          v-for="option in options"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </option>
      </select>
      <label
        :for="inputId"
        class="pointer-events-none absolute left-3 top-0 -translate-y-1/2 bg-surface px-1 md-label-medium"
        :class="labelClass"
      >{{ label }}</label>
      <span
        class="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 [&>svg]:size-6"
        :class="iconClass"
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
        >
          <path d="M7 10l5 5 5-5z" />
        </svg>
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
