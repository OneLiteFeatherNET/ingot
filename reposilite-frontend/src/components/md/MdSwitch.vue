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
 * Material Design 3 switch for a boolean setting. The switch is a button with role="switch",
 * so the state reaches assistive technology through aria-checked, and Space and Enter toggle
 * it. The default slot is the label, tied to the switch through <label for>.
 *
 * Attributes that are not props (aria-labelledby, aria-describedby, name and so on) go to the
 * switch element, where assistive technology reads them. class and style stay on the root.
 *
 * Sizes are MD3's. The track is 52 by 32px with a 2px outline when off. The handle is 16px
 * when off, 24px when on and 28px while pressed. The 40px state layer sits around the handle
 * and the 48px hit area comes from md-touch-target. The handle centres are 14px and 34px
 * from the padding edge of the track, which is where MD3 puts them.
 */
defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  disabled: {
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
const switchId = computed(() => props.id || `md-switch-${generatedId}`)

// Every listener and attribute except class and style goes to the switch element.
const forwardedAttrs = () =>
  Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style'))

const toggle = () => {
  if (!props.disabled) emit('update:modelValue', !props.modelValue)
}

// Every class name is spelled out in full so that Tailwind finds it in this file.
const trackClass = computed(() => {
  const base = 'group md-focus-ring md-touch-target relative inline-flex h-8 w-13 shrink-0 items-center rounded-corner-full border-2 transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard disabled:cursor-not-allowed'
  if (props.disabled) {
    return [
      base,
      props.modelValue ? 'border-transparent bg-on-surface/12' : 'border-on-surface/12 bg-surface-container-highest'
    ]
  }
  return [
    base,
    'cursor-pointer',
    props.modelValue ? 'border-primary bg-primary' : 'border-outline bg-surface-container-highest'
  ]
})

const handleClass = computed(() => {
  const base = 'absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-corner-full transition-all duration-(--md-sys-motion-duration-short3) ease-md-standard'
  const position = props.modelValue ? 'left-[34px]' : 'left-[14px]'
  if (props.disabled) {
    return [base, position, props.modelValue ? 'size-6 bg-surface' : 'size-4 bg-on-surface/38']
  }
  return [
    base,
    position,
    props.modelValue
      ? 'size-6 bg-on-primary group-active:size-7'
      : 'size-4 bg-outline group-active:size-7'
  ]
})

// The state layer is 40px and centred on the handle. It is on-primary when on, on-surface when
// off. Hover is 8%, focus and pressed are 12%, and a disabled switch has none.
const stateLayerClass = computed(() => {
  const base = 'absolute top-1/2 -translate-x-1/2 -translate-y-1/2 size-10 rounded-corner-full pointer-events-none opacity-0 transition-opacity duration-(--md-sys-motion-duration-short3) ease-md-standard'
  const position = props.modelValue ? 'left-[34px]' : 'left-[14px]'
  if (props.disabled) return [base, position]
  return [
    base,
    position,
    props.modelValue ? 'bg-primary' : 'bg-on-surface',
    'group-hover:opacity-[0.08] group-focus-visible:opacity-[0.12] group-active:opacity-[0.12]'
  ]
})

const labelClass = computed(() =>
  props.disabled ? 'cursor-not-allowed text-on-surface/38' : 'cursor-pointer text-on-surface'
)
</script>

<template>
  <div
    class="flex items-center gap-3"
    :class="attrs.class"
    :style="attrs.style"
  >
    <button
      v-bind="forwardedAttrs()"
      :id="switchId"
      type="button"
      role="switch"
      :aria-checked="modelValue ? 'true' : 'false'"
      :disabled="disabled"
      :class="trackClass"
      @click="toggle"
    >
      <span
        data-part="state-layer"
        aria-hidden="true"
        :class="stateLayerClass"
      />
      <span
        data-part="handle"
        aria-hidden="true"
        :class="handleClass"
      />
    </button>
    <label
      :for="switchId"
      class="md-body-large select-none"
      :class="labelClass"
    >
      <slot />
    </label>
  </div>
</template>
