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
 * Material Design 3 radio button. The control is a real <input type="radio">, so the group
 * semantics and the form value come from the browser. Radios that share a `name` form one
 * group and the group gets one tab stop.
 *
 * Arrow keys move the selection through the group: Down and Right go forward, Up and Left go
 * back. Both wrap, and disabled radios are skipped. The component does this itself, so the
 * behaviour is the same in every browser and can be tested without one. The default is
 * prevented, so the browser does not move the selection a second time.
 *
 * The input is transparent and covers a 48px area centred on the 40px state layer, so the
 * whole area takes a click. The visible circle is 20px with a 2px outline, and the selected
 * one has a 10px dot in the primary role. The label is a real <label for>, so a click on the
 * text selects the radio too.
 *
 * `card` makes the whole radio one clickable card. The root becomes a <label> around the
 * input, so a click anywhere on it selects the radio. The default slot then sits beside the
 * circle and keeps the colours the card gives it through its own classes. The focus ring is
 * drawn on the card only for keyboard focus (`has-focus-visible`), so a mouse click leaves no
 * ring behind.
 *
 * `modelValue` is the value of the group and `value` is the value of this radio, as with a
 * native radio. Emits update:modelValue with `value` when this radio is chosen.
 *
 * A pointer click leaves the radio without focus, so the focus ring does not stay behind after
 * a mouse click. A keyboard activation has detail 0 and keeps the focus where the keyboard put it.
 *
 * Attributes that are not props (aria-describedby and so on) go to the input. class and style
 * stay on the root.
 */
defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: {
    type: [String, Number, Boolean, Object],
    default: undefined
  },
  value: {
    type: [String, Number, Boolean, Object],
    required: true
  },
  // Required, because the radios of one choice must share it to form a group.
  name: {
    type: String,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  // The whole area is the clickable target and the slot is the card content.
  card: {
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
const inputId = computed(() => props.id || `md-radio-${generatedId}`)

// Every listener and attribute except class and style goes to the input.
const forwardedAttrs = () =>
  Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style'))

const checked = computed(() => props.modelValue === props.value)

const onChange = () => emit('update:modelValue', props.value)

// detail is above 0 for a pointer click. Only then does the focus leave the radio, so the ring
// is not left behind; a keyboard activation keeps it.
const onClick = (event) => {
  if (event.detail > 0) {
    event.currentTarget.blur()
  }
}

const ARROW_STEP = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 }

// Moves the selection to the next enabled radio of the same group, in the scope of its fieldset
// or form. The next radio is focused and clicked, so it is chosen and emits like a pointer click.
const onKeydown = (event) => {
  const step = ARROW_STEP[event.key]
  if (step === undefined || props.disabled) return
  const input = event.currentTarget
  const scope = input.closest('fieldset') ?? input.form ?? document
  const group = Array.from(scope.querySelectorAll('input[type="radio"]'))
    .filter(radio => radio.name === props.name && !radio.disabled)
  const at = group.indexOf(input)
  if (at < 0 || group.length < 2) return
  event.preventDefault()
  const next = group[(at + step + group.length) % group.length]
  next.focus()
  next.click()
}

// Every class name is spelled out in full so that Tailwind finds it in this file.
const rootClass = computed(() => {
  if (!props.card) return 'flex items-center gap-2'
  const base = 'relative flex items-start gap-3 has-focus-visible:outline-3 has-focus-visible:outline-solid has-focus-visible:outline-offset-2 has-focus-visible:outline-secondary'
  return props.disabled ? [base, 'cursor-not-allowed'] : [base, 'cursor-pointer']
})

const circleClass = computed(() => {
  const base = 'relative flex size-5 items-center justify-center rounded-corner-full border-2 pointer-events-none transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard'
  if (props.disabled) {
    return [base, 'border-on-surface/38']
  }
  return [base, checked.value ? 'border-primary' : 'border-on-surface-variant']
})

const dotClass = computed(() =>
  props.disabled ? 'bg-on-surface/38' : 'bg-primary'
)

// The state layer is 40px around the circle, on-surface at rest and primary when selected. Hover
// is 8%, focus and pressed are 12%. A disabled radio has none.
const stateLayerClass = computed(() => {
  const base = 'pointer-events-none absolute inset-0 rounded-corner-full opacity-0 transition-opacity duration-(--md-sys-motion-duration-short3) ease-md-standard'
  if (props.disabled) return base
  const colour = checked.value ? 'bg-primary' : 'bg-on-surface'
  return [base, colour, 'peer-hover:opacity-[0.08] peer-focus-visible:opacity-[0.12] peer-active:opacity-[0.12]']
})

const labelClass = computed(() =>
  props.disabled ? 'cursor-not-allowed text-on-surface/38' : 'cursor-pointer text-on-surface'
)

// In a card the text keeps the colours the card gives it; only a disabled card dims it.
const cardContentClass = computed(() =>
  props.disabled ? 'min-w-0 flex-1 text-on-surface/38' : 'min-w-0 flex-1'
)
</script>

<template>
  <component
    :is="card ? 'label' : 'div'"
    :class="[rootClass, attrs.class]"
    :style="attrs.style"
  >
    <span class="relative inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full">
      <input
        v-bind="forwardedAttrs()"
        :id="inputId"
        type="radio"
        class="peer absolute left-1/2 top-1/2 m-0 size-12 -translate-x-1/2 -translate-y-1/2 cursor-pointer opacity-0 disabled:cursor-not-allowed"
        :name="name"
        :value="value"
        :checked="checked"
        :disabled="disabled"
        @change="onChange"
        @click="onClick"
        @keydown="onKeydown"
      >
      <span
        data-part="state-layer"
        aria-hidden="true"
        :class="stateLayerClass"
      />
      <span
        data-part="circle"
        aria-hidden="true"
        :data-checked="checked ? 'true' : 'false'"
        :class="circleClass"
      >
        <span
          v-if="checked"
          data-part="dot"
          class="block size-2.5 rounded-corner-full"
          :class="dotClass"
        />
      </span>
    </span>
    <span
      v-if="card"
      data-part="card-content"
      :class="cardContentClass"
    >
      <slot />
    </span>
    <label
      v-else
      :for="inputId"
      class="flex min-h-10 min-w-0 select-none items-center md-body-large"
      :class="labelClass"
    >
      <slot />
    </label>
  </component>
</template>
