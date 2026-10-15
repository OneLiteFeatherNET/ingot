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
import { computed, useId } from 'vue'

/*
 * Multi-line outlined text field with a character counter. The label sits above the field, as the
 * chip field does, because a floating label does not fit a field that grows with its text. The
 * counter is shown against `maxLength` and turns to the error colour when the text is over it. The
 * field itself stops at the limit, so the counter only goes over for a value that came from elsewhere.
 * `maxLength` of zero shows no counter and sets no limit.
 */
const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  label: {
    type: String,
    required: true
  },
  id: {
    type: String,
    default: ''
  },
  rows: {
    type: Number,
    default: 4
  },
  maxLength: {
    type: Number,
    default: 0
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
const inputId = computed(() => props.id || `md-text-area-${generatedId}`)
const helperId = computed(() => `${inputId.value}-helper`)

const length = computed(() => props.modelValue.length)
const overLimit = computed(() => props.maxLength > 0 && length.value > props.maxLength)
const helperText = computed(() => (props.error ? props.errorText : props.supportingText))
const invalid = computed(() => props.error || overLimit.value)

// The classes are spelled out in full so that Tailwind finds them in this file.
const FIELD = {
  default: 'border-outline focus:border-primary focus:shadow-[inset_0_0_0_1px_var(--md-sys-color-primary)]',
  error: 'border-error shadow-[inset_0_0_0_1px_var(--md-sys-color-error)]',
  disabled: 'border-on-surface/12 text-on-surface/38'
}
const fieldClass = computed(() => {
  if (props.disabled) return FIELD.disabled
  return invalid.value ? FIELD.error : FIELD.default
})
</script>

<template>
  <div class="flex min-w-0 w-full max-w-xl flex-col gap-1">
    <label
      :for="inputId"
      class="px-1 md-label-large text-on-surface-variant"
      :class="disabled ? 'text-on-surface/38' : ''"
    >
      {{ label }}
    </label>
    <textarea
      :id="inputId"
      :value="modelValue"
      :rows="rows"
      :maxlength="maxLength > 0 ? maxLength : undefined"
      :required="required"
      :disabled="disabled"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-describedby="helperText ? helperId : undefined"
      class="block w-full resize-y rounded-corner-extra-small border bg-transparent px-4 py-3 md-body-large text-on-surface outline-none disabled:resize-none"
      :class="fieldClass"
      @input="emit('update:modelValue', $event.target.value)"
    />
    <div class="flex items-start justify-between gap-2 px-1">
      <p
        :id="helperId"
        class="md-body-small"
        :class="invalid ? 'text-error' : 'text-on-surface-variant'"
      >
        {{ helperText }}
      </p>
      <p
        v-if="maxLength > 0"
        data-test="counter"
        class="shrink-0 md-body-small"
        :class="overLimit ? 'text-error' : 'text-on-surface-variant'"
      >
        {{ length }} / {{ maxLength }}
      </p>
    </div>
  </div>
</template>
