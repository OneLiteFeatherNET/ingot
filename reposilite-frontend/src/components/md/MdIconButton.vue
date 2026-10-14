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
import { computed, useAttrs } from 'vue'

/*
 * Material Design 3 icon button. An icon has no accessible name of its own, so every
 * instance needs an `aria-label`. Without one the component warns, because a screen reader
 * would announce the button as unnamed. With `toggle` the button reports its state through
 * aria-pressed, and `selected` sets that state.
 */
const props = defineProps({
  variant: {
    type: String,
    default: 'standard',
    validator: (value) => ['standard', 'filled', 'tonal', 'outlined', 'error'].includes(value)
  },
  href: {
    type: String,
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  },
  toggle: {
    type: Boolean,
    default: false
  },
  selected: {
    type: Boolean,
    default: false
  },
  type: {
    type: String,
    default: 'button'
  }
})

if (!useAttrs()['aria-label']) {
  console.warn('MdIconButton: missing aria-label. An icon-only button needs an accessible name.')
}

// Each variant names the class set for: the default state, the two toggle states, and disabled.
const VARIANTS = {
  standard: {
    default: 'bg-transparent text-on-surface-variant',
    on: 'bg-transparent text-primary',
    off: 'bg-transparent text-on-surface-variant',
    disabled: 'bg-transparent text-on-surface/38'
  },
  filled: {
    default: 'bg-primary text-on-primary',
    on: 'bg-primary text-on-primary',
    off: 'bg-surface-container-highest text-primary',
    disabled: 'bg-on-surface/12 text-on-surface/38'
  },
  tonal: {
    default: 'bg-secondary-container text-on-secondary-container',
    on: 'bg-secondary-container text-on-secondary-container',
    off: 'bg-surface-container-highest text-on-surface-variant',
    disabled: 'bg-on-surface/12 text-on-surface/38'
  },
  outlined: {
    default: 'border border-outline bg-transparent text-on-surface-variant',
    on: 'border border-inverse-surface bg-inverse-surface text-inverse-on-surface',
    off: 'border border-outline bg-transparent text-on-surface-variant',
    disabled: 'border border-on-surface/12 bg-transparent text-on-surface/38'
  },
  // Destructive actions in the standard style: the trash icon, not a filled block.
  error: {
    default: 'bg-transparent text-error',
    on: 'bg-transparent text-error',
    off: 'bg-transparent text-error',
    disabled: 'bg-transparent text-on-surface/38'
  }
}

const tag = computed(() => (props.href ? 'a' : 'button'))

const classes = computed(() => {
  const variant = VARIANTS[props.variant] ?? VARIANTS.standard
  let state = variant.default
  if (props.disabled) {
    state = variant.disabled
  } else if (props.toggle) {
    state = props.selected ? variant.on : variant.off
  }
  return [
    'md-state-layer md-focus-ring relative inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-corner-full transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard [&>svg]:size-6',
    state
  ]
})
</script>

<template>
  <component
    :is="tag"
    :href="tag === 'a' && !disabled ? href : undefined"
    :type="tag === 'button' ? type : undefined"
    :disabled="tag === 'button' ? disabled : undefined"
    :aria-disabled="tag === 'a' && disabled ? 'true' : undefined"
    :aria-pressed="toggle ? String(selected) : undefined"
    :data-variant="variant"
    :class="classes"
  >
    <slot />
  </component>
</template>
