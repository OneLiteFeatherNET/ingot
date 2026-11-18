<script setup lang="ts">
import { computed } from 'vue'

/*
 * The wrapper of a dashboard transition. A component names its pattern, and the wrapper hands the Vue <Transition> the
 * class prefix of that pattern (see src/styles/md3/motion-transitions.css), the mode and the appear flag. The wrapper
 * adds no element: the transition is applied to the slot child, so no selector of a plugin anchor changes what it
 * matches. Reduced motion is not read here. The single rule of src/style.css makes every transition an instant swap.
 */

type MotionPattern = 'fade-through' | 'shared-x' | 'shared-y' | 'menu' | 'dialog' | 'sheet' | 'snackbar' | 'expand' | 'fade'
type TransitionMode = 'in-out' | 'out-in' | 'default'

// The mode each pattern uses by default: a page, a tab or a snackbar leaves before the next one enters, the others run
// their leave and enter together.
const DEFAULT_MODE: Record<MotionPattern, TransitionMode> = {
  'fade-through': 'out-in',
  'shared-x': 'out-in',
  'shared-y': 'out-in',
  menu: 'default',
  dialog: 'default',
  sheet: 'default',
  snackbar: 'out-in',
  expand: 'default',
  fade: 'default'
}

const props = withDefaults(defineProps<{
  pattern: MotionPattern
  direction?: -1 | 0 | 1
  mode?: TransitionMode
  appear?: boolean
}>(), {
  direction: 0,
  mode: undefined,
  appear: false
})

const isKnown = (pattern: string): pattern is MotionPattern => Object.hasOwn(DEFAULT_MODE, pattern)

const known = computed(() => {
  if (!isKnown(props.pattern)) {
    console.warn(`MdTransition: unknown pattern "${props.pattern}", the slot is drawn without a transition`)
    return false
  }
  return true
})

// A sibling that comes earlier in the list moves the page the other way, so shared-x has a class for each direction.
const transitionName = computed(() => {
  const base = `motion-${props.pattern}`
  return props.pattern === 'shared-x' && props.direction === -1 ? `${base}-back` : base
})

const transitionMode = computed(() => props.mode ?? (isKnown(props.pattern) ? DEFAULT_MODE[props.pattern] : 'default'))
</script>

<template>
  <Transition
    v-if="known"
    :name="transitionName"
    :mode="transitionMode"
    :appear="appear"
  >
    <slot />
  </Transition>
  <slot v-else />
</template>
