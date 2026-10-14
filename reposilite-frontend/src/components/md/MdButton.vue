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
import { computed } from 'vue'

/*
 * Material Design 3 button. Renders an anchor when `href` is set, so a control that
 * navigates stays a link for keyboard users, middle click and assistive technology.
 * Use one `filled` button per view as the primary action.
 */
const props = defineProps({
  variant: {
    type: String,
    default: 'filled',
    validator: (value) => ['filled', 'tonal', 'outlined', 'text', 'elevated'].includes(value)
  },
  href: {
    type: String,
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  },
  type: {
    type: String,
    default: 'button'
  }
})

const VARIANTS = {
  filled: {
    base: 'bg-primary text-on-primary',
    disabled: 'bg-on-surface/12 text-on-surface/38'
  },
  tonal: {
    base: 'bg-secondary-container text-on-secondary-container',
    disabled: 'bg-on-surface/12 text-on-surface/38'
  },
  outlined: {
    base: 'border border-outline bg-transparent text-primary',
    disabled: 'border border-on-surface/12 bg-transparent text-on-surface/38'
  },
  text: {
    base: 'bg-transparent text-primary',
    disabled: 'bg-transparent text-on-surface/38'
  },
  elevated: {
    base: 'bg-surface-container-low text-primary shadow-elevation-1',
    disabled: 'bg-on-surface/12 text-on-surface/38 shadow-none'
  }
}

const tag = computed(() => (props.href ? 'a' : 'button'))

const classes = computed(() => {
  const variant = VARIANTS[props.variant] ?? VARIANTS.filled
  return [
    'md-state-layer md-focus-ring relative inline-flex min-h-12 items-center justify-center gap-2 rounded-corner-full px-6 md-label-large no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
    props.disabled ? variant.disabled : variant.base
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
    :data-variant="variant"
    :class="classes"
  >
    <span
      v-if="$slots.icon"
      class="inline-flex shrink-0 [&>svg]:size-[18px]"
      aria-hidden="true"
    >
      <slot name="icon" />
    </span>
    <slot />
  </component>
</template>
