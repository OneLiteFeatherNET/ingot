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
import { computed, useAttrs } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/*
 * Material Design 3 icon button. An icon has no accessible name of its own, so every
 * instance needs an `aria-label`. Without one the component warns, because a screen reader
 * would announce the button as unnamed. With `toggle` the button reports its state through
 * aria-pressed, and `selected` sets that state.
 *
 * `size` `compact` draws a 24px button; its hit area stays 48px. `to` makes the button a router link, as on `MdButton`.
 * `inverse` draws the button for an inverse surface, such as a snackbar. It replaces the variant's colours, and the
 * disabled colours win over it.
 */
const props = withDefaults(defineProps<{
  variant?: 'standard' | 'filled' | 'tonal' | 'outlined' | 'error'
  size?: 'default' | 'compact'
  href?: string
  to?: RouteLocationRaw
  disabled?: boolean
  toggle?: boolean
  selected?: boolean
  inverse?: boolean
  type?: string
}>(), {
  variant: 'standard',
  size: 'default',
  href: undefined,
  to: undefined,
  inverse: false,
  type: 'button'
})

defineSlots<{
  default?(): unknown
}>()

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

// A button with `to` is a router link. A disabled one is an anchor with no destination, as a disabled `href` is.
const kind = computed(() => {
  if (props.to !== undefined) return props.disabled ? 'anchor' : 'link'
  return props.href ? 'anchor' : 'button'
})

const element = computed(() => {
  if (kind.value === 'link') return RouterLink
  return kind.value === 'anchor' ? 'a' : 'button'
})

// Each kind takes only its own attributes. An undefined attribute here would override the router link's own href.
const attrs = computed(() => {
  if (kind.value === 'link') return { to: props.to }
  if (kind.value === 'anchor') {
    return { href: props.disabled ? undefined : props.href, 'aria-disabled': props.disabled ? 'true' : undefined }
  }
  return { type: props.type, disabled: props.disabled }
})

// The inverse colours replace the variant and toggle colours. A disabled button keeps its disabled colours.
const INVERSE = 'bg-transparent text-inverse-on-surface'

const classes = computed(() => {
  const variant = VARIANTS[props.variant] ?? VARIANTS.standard
  let state = variant.default
  if (props.disabled) {
    state = variant.disabled
  } else if (props.inverse) {
    state = INVERSE
  } else if (props.toggle) {
    state = props.selected ? variant.on : variant.off
  }
  const size = props.size === 'compact' ? 'h-6 w-6' : 'h-10 w-10'
  return [
    'md-state-layer md-focus-ring md-touch-target relative inline-flex shrink-0 items-center justify-center rounded-corner-full transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard [&>svg]:size-6',
    size,
    state
  ]
})
</script>

<template>
  <component
    :is="element"
    v-bind="attrs"
    :aria-pressed="toggle ? String(selected) : undefined"
    :data-variant="variant"
    :class="classes"
    data-ingot-component="md-icon-button"
  >
    <slot />
  </component>
</template>
