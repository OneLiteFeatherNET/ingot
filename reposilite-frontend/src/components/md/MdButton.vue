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
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/*
 * Material Design 3 button. Renders an anchor when `href` is set, so a control that
 * navigates stays a link for keyboard users, middle click and assistive technology.
 * Use one `filled` button per view as the primary action.
 *
 * Destructive actions set `error` (the one documented way). It recolours any variant with
 * the error roles. `variant="error"` is a compatibility alias for a filled error button.
 *
 * `to` makes the button a router link: a plain click moves the router and the link keeps its href.
 * `inverse` draws the button for an inverse surface, such as a snackbar. `error` wins over `inverse`.
 */
const props = withDefaults(defineProps<{
  variant?: 'filled' | 'tonal' | 'outlined' | 'text' | 'elevated' | 'error'
  href?: string
  to?: RouteLocationRaw
  disabled?: boolean
  type?: string
  error?: boolean
  inverse?: boolean
}>(), {
  variant: 'filled',
  href: undefined,
  to: undefined,
  type: 'button',
  inverse: false
})

defineSlots<{
  icon?(): unknown
  default?(): unknown
}>()

const VARIANTS = {
  filled: {
    base: 'bg-primary text-on-primary',
    error: 'bg-error text-on-error',
    inverse: 'bg-inverse-primary text-inverse-surface',
    disabled: 'bg-on-surface/md-disabled-container text-on-surface/md-disabled-content'
  },
  tonal: {
    base: 'bg-secondary-container text-on-secondary-container',
    error: 'bg-error-container text-on-error-container',
    inverse: 'bg-inverse-surface text-inverse-on-surface',
    disabled: 'bg-on-surface/md-disabled-container text-on-surface/md-disabled-content'
  },
  outlined: {
    base: 'border border-outline bg-transparent text-primary',
    error: 'border border-error bg-transparent text-error',
    inverse: 'border border-inverse-on-surface bg-transparent text-inverse-on-surface',
    disabled: 'border border-on-surface/md-disabled-container bg-transparent text-on-surface/md-disabled-content'
  },
  text: {
    base: 'bg-transparent text-primary',
    error: 'bg-transparent text-error',
    inverse: 'bg-transparent text-inverse-primary',
    disabled: 'bg-transparent text-on-surface/md-disabled-content'
  },
  elevated: {
    base: 'bg-surface-container-low text-primary shadow-elevation-1',
    error: 'bg-surface-container-low text-error shadow-elevation-1',
    inverse: 'bg-inverse-surface text-inverse-primary shadow-elevation-1',
    disabled: 'bg-on-surface/md-disabled-container text-on-surface/md-disabled-content shadow-none'
  }
}

// A button with `to` is a router link. A disabled one is an anchor with no destination, as a disabled `href` is.
const kind = computed(() => {
  if (props.to !== undefined && !props.disabled) return 'link'
  if (props.to !== undefined || props.href !== undefined) return 'anchor'
  return 'button'
})

const element = computed(() => {
  if (kind.value === 'link') return RouterLink
  return kind.value === 'button' ? 'button' : 'a'
})

// Each kind takes only its own attributes. An undefined attribute here would override the router link's own href.
const attrs = computed(() => {
  if (kind.value === 'link') return { to: props.to }
  if (kind.value === 'anchor') {
    return { href: props.disabled ? undefined : props.href, 'aria-disabled': props.disabled ? 'true' : undefined }
  }
  return { type: props.type, disabled: props.disabled }
})

// `variant="error"` is the compatibility alias for a filled error button.
const resolvedVariant = computed(() => (props.variant === 'error' ? 'filled' : props.variant))
const isError = computed(() => props.error || props.variant === 'error')

const classes = computed(() => {
  const variant = VARIANTS[resolvedVariant.value] ?? VARIANTS.filled
  let tone = variant.base
  if (props.disabled) tone = variant.disabled
  else if (isError.value) tone = variant.error
  else if (props.inverse) tone = variant.inverse
  return [
    'md-state-layer md-focus-ring md-touch-target relative inline-flex h-10 items-center justify-center gap-2 rounded-corner-full pr-6 md-label-large no-underline transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
    tone
  ]
})
</script>

<template>
  <component
    :is="element"
    v-bind="attrs"
    :data-variant="variant"
    :class="[classes, $slots.icon ? 'pl-4' : 'pl-6']"
    data-ingot-component="md-button"
  >
    <span
      v-if="$slots.icon"
      class="inline-flex shrink-0 md-icon-svg"
      aria-hidden="true"
    >
      <slot name="icon" />
    </span>
    <slot />
  </component>
</template>
