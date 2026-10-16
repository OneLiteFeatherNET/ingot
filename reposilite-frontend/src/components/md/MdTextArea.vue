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
import { computed, nextTick, onMounted, onBeforeUnmount, ref, useId, watch } from 'vue'

/*
 * Multi-line outlined text field, drawn as the MD3 outlined field of MdTextField: the label floats in
 * the notch of the outline, the field takes the full width of its form, and the supporting text and
 * the character counter share one row below it. The label is a real <label for> tied to the textarea,
 * and it floats through the peer pattern, which needs the textarea's placeholder to be a space.
 *
 * The field grows with its text from `rows` lines up to MAX_LINES, then scrolls. It has no resize
 * handle. The counter is shown against `maxLength` and turns to the error colour when the text is over
 * it. The field itself stops at the limit, so the counter only goes over for a value that came from
 * elsewhere. `maxLength` of zero shows no counter and sets no limit.
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
  // The fewest lines the field shows, and the height it starts at.
  rows: {
    type: Number,
    default: 3
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

// The text metrics of the field, from the body-large role (24px lines) and the field's padding (12px top and
// bottom). The longest the field grows to is MAX_LINES; past that it scrolls.
const MAX_LINES = 8
const LINE_PX = 24
const PADDING_PX = 24

const generatedId = useId()
const inputId = computed(() => props.id || `md-text-area-${generatedId}`)
const helperId = computed(() => `${inputId.value}-helper`)

const field = ref(null)
let observer

const length = computed(() => props.modelValue.length)
const overLimit = computed(() => props.maxLength > 0 && length.value > props.maxLength)
const helperText = computed(() => (props.error ? props.errorText : props.supportingText))
const invalid = computed(() => props.error || overLimit.value)

/*
 * Sizes the textarea to its text: at least `rows` lines, at most MAX_LINES, with the borders counted in.
 * It is measured with the height reset, so the text can also shrink the field again.
 */
const grow = () => {
  const element = field.value
  if (!element) return
  element.style.height = 'auto'
  const borders = element.offsetHeight - element.clientHeight
  const floor = props.rows * LINE_PX + PADDING_PX + borders
  const cap = MAX_LINES * LINE_PX + PADDING_PX + borders
  const wanted = element.scrollHeight + borders
  element.style.height = `${Math.min(Math.max(wanted, floor), cap)}px`
  element.style.overflowY = wanted > cap ? 'auto' : 'hidden'
}

const onInput = (event) => {
  emit('update:modelValue', event.target.value)
  grow()
}

watch(() => props.modelValue, () => nextTick(grow))

onMounted(() => {
  grow()
  // The field wraps to another number of lines when its width changes, so it is measured again then.
  if (typeof ResizeObserver !== 'undefined' && field.value) {
    observer = new ResizeObserver(grow)
    observer.observe(field.value)
  }
})

onBeforeUnmount(() => observer?.disconnect())

// The classes are spelled out in full so that Tailwind finds them in this file. The outline, the focus
// indicator and the notch match MdTextField: a 1px outline that becomes 2px primary on focus.
const FIELD = {
  default: 'border-outline focus:border-primary focus:shadow-[inset_0_0_0_1px_var(--md-sys-color-primary)]',
  error: 'border-error shadow-[inset_0_0_0_1px_var(--md-sys-color-error)]',
  disabled: 'border-on-surface/12 text-on-surface/38'
}
const fieldClass = computed(() => {
  if (props.disabled) return FIELD.disabled
  return invalid.value ? FIELD.error : FIELD.default
})

// The label rests on the first line of text, and floats onto the top edge of the outline like the
// label of MdTextField. The notch is filled with the field's container colour.
const LABEL_FLOAT = 'peer-focus:top-0 peer-focus:-translate-y-1/2 peer-focus:bg-[var(--md-field-container,var(--md-sys-color-surface))] peer-focus:px-1 peer-focus:md-label-medium peer-[:not(:placeholder-shown)]:top-0 peer-[:not(:placeholder-shown)]:-translate-y-1/2 peer-[:not(:placeholder-shown)]:bg-[var(--md-field-container,var(--md-sys-color-surface))] peer-[:not(:placeholder-shown)]:px-1 peer-[:not(:placeholder-shown)]:md-label-medium'
const labelClass = computed(() => [
  'pointer-events-none absolute left-4 top-3 translate-y-0 md-body-large transition-all duration-(--md-sys-motion-duration-short4) ease-md-standard',
  LABEL_FLOAT,
  props.disabled ? 'text-on-surface/38' : (invalid.value ? 'text-error peer-focus:text-error' : 'text-on-surface-variant peer-focus:text-primary')
])

const helperClass = computed(() => {
  if (props.disabled) return 'text-on-surface/38'
  return invalid.value ? 'text-error' : 'text-on-surface-variant'
})
</script>

<template>
  <div class="flex min-w-0 w-full flex-col gap-1">
    <div class="relative">
      <textarea
        :id="inputId"
        ref="field"
        :value="modelValue"
        :rows="rows"
        :maxlength="maxLength > 0 ? maxLength : undefined"
        :required="required"
        :disabled="disabled"
        placeholder=" "
        :aria-invalid="invalid ? 'true' : undefined"
        :aria-describedby="helperText ? helperId : undefined"
        class="peer block w-full resize-none rounded-corner-extra-small border bg-transparent px-4 py-3 md-body-large text-on-surface outline-none"
        :class="fieldClass"
        @input="onInput"
      />
      <label
        :for="inputId"
        :class="labelClass"
      >{{ label }}</label>
    </div>
    <div class="flex items-start justify-between gap-2 px-4">
      <p
        :id="helperId"
        class="md-body-small"
        :class="helperClass"
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
