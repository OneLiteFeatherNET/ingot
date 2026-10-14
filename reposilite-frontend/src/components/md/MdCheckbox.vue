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
 * Material Design 3 checkbox. The control is a real <input type="checkbox">, so keyboard,
 * form and assistive technology semantics come from the browser. The input is transparent
 * and covers a 48px area centred on the 40px state layer, so the whole area takes a click.
 * The visible box is an 18px square with a 2px outline, drawn over it and ignored by the
 * pointer.
 *
 * `indeterminate` shows a dash in place of the check mark. The browser sets the state on the
 * input, so it is not an attribute. `error` draws the box in the error role.
 *
 * Attributes that are not props (aria-describedby, name and so on) go to the input. class and
 * style stay on the root.
 */
defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  indeterminate: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: Boolean,
    default: false
  },
  id: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const attrs = useAttrs()
const generatedId = useId()
const inputId = computed(() => props.id || `md-checkbox-${generatedId}`)

// Every listener and attribute except class and style goes to the input.
const forwardedAttrs = () =>
  Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style'))

const onChange = (event) => emit('update:modelValue', event.target.checked)

const state = computed(() => {
  if (props.indeterminate) return 'indeterminate'
  return props.modelValue ? 'checked' : 'unchecked'
})
const selected = computed(() => state.value !== 'unchecked')

// Every class name is spelled out in full so that Tailwind finds it in this file.
const boxClass = computed(() => {
  const base = 'relative flex size-[18px] items-center justify-center rounded-[2px] border-2 pointer-events-none transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard'
  if (props.disabled) {
    return [base, selected.value ? 'border-transparent bg-on-surface/38' : 'border-on-surface/38 bg-transparent']
  }
  if (props.error) {
    return [base, selected.value ? 'border-error bg-error' : 'border-error bg-transparent']
  }
  return [base, selected.value ? 'border-primary bg-primary' : 'border-outline bg-transparent']
})

// The mark takes the on-colour of the box: on-primary, on-error, or surface when disabled.
const markClass = computed(() => {
  if (props.disabled) return 'text-surface'
  return props.error ? 'text-on-error' : 'text-on-primary'
})

// The state layer is 40px around the box. It is primary when checked, error in the error
// state, and on-surface otherwise. Hover is 8%, focus and pressed are 12%, and a disabled
// checkbox has none.
const stateLayerClass = computed(() => {
  const base = 'pointer-events-none absolute inset-0 rounded-corner-full opacity-0 transition-opacity duration-(--md-sys-motion-duration-short3) ease-md-standard'
  if (props.disabled) return base
  const colour = props.error ? 'bg-error' : selected.value ? 'bg-primary' : 'bg-on-surface'
  return [base, colour, 'peer-hover:opacity-[0.08] peer-focus-visible:opacity-[0.12] peer-active:opacity-[0.12]']
})

const labelClass = computed(() =>
  props.disabled ? 'cursor-not-allowed text-on-surface/38' : 'cursor-pointer text-on-surface'
)
</script>

<template>
  <div
    class="flex items-center gap-2"
    :class="attrs.class"
    :style="attrs.style"
  >
    <span class="relative inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full">
      <input
        v-bind="forwardedAttrs()"
        :id="inputId"
        type="checkbox"
        class="peer absolute left-1/2 top-1/2 m-0 size-12 -translate-x-1/2 -translate-y-1/2 cursor-pointer opacity-0 disabled:cursor-not-allowed"
        :checked="modelValue"
        :indeterminate.prop="indeterminate"
        :disabled="disabled"
        :aria-invalid="error ? 'true' : undefined"
        @change="onChange"
      >
      <span
        data-part="state-layer"
        aria-hidden="true"
        :class="stateLayerClass"
      />
      <span
        data-part="box"
        aria-hidden="true"
        :data-state="state"
        :class="boxClass"
      >
        <svg
          v-if="state === 'checked'"
          viewBox="0 -960 960 960"
          focusable="false"
          aria-hidden="true"
          class="size-[18px]"
          :class="markClass"
        >
          <path
            fill="currentColor"
            d="M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z"
          />
        </svg>
        <span
          v-else-if="state === 'indeterminate'"
          class="block h-[2px] w-2.5 rounded-full bg-current"
          :class="markClass"
        />
      </span>
    </span>
    <label
      :for="inputId"
      class="md-body-large select-none"
      :class="labelClass"
    >
      <slot />
    </label>
  </div>
</template>
