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
import { nextTick, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'

/*
 * A modal dialog: a vue-final-modal teleported to the body, over a scrim, with a 24px side inset. The frame is 280px at
 * least and 560px at most (DESIGN.md, "Rules for contributors"). It follows the WAI-ARIA dialog pattern, as MdSideSheet
 * does: on open, focus moves to the element with data-autofocus or to the first focusable element, Tab and Shift Tab
 * stay inside, and on close focus returns to the element that opened the dialog (or to its nearest ancestor that is
 * still in the page).
 *
 * Props:
 *   modelValue   whether the dialog is open (v-model)
 *   labelledby   the id of the heading in the `header` slot, for the dialog's accessible name
 *   describedby  the id of the text that describes the dialog, optional
 *   role         dialog, or alertdialog for a dialog that asks for an answer
 *   size         sm (360px), md (480px) or lg (560px, the default)
 *   dismissable  false keeps the dialog open on Escape and on a click outside, so that only its own buttons close it
 * Slots:
 *   header   the title and the description
 *   default  the body
 *   actions  the buttons, normally an MdDialogActions
 * Emits update:modelValue, opened and closed.
 */
const props = withDefaults(defineProps<{
  modelValue?: boolean
  labelledby: string
  describedby?: string
  role?: 'dialog' | 'alertdialog'
  size?: 'sm' | 'md' | 'lg'
  dismissable?: boolean
}>(), {
  modelValue: false,
  role: 'dialog',
  size: 'lg',
  dismissable: true
})

defineSlots<{
  header?(): unknown
  default?(): unknown
  actions?(): unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  opened: []
  closed: []
}>()

const SIZES = {
  sm: 'max-w-90',
  md: 'max-w-120',
  lg: 'max-w-140'
} as const

const frame = ref<HTMLElement | null>(null)
let openerChain: HTMLElement[] = []

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(', ')
const focusables = (): HTMLElement[] => [...(frame.value?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])]

// The opener and its ancestors, nearest first, so that focus can fall back to a parent when the opener is gone.
const chainFrom = (element: HTMLElement | null): HTMLElement[] => {
  const chain: HTMLElement[] = []
  for (let current = element; current; current = current.parentElement) chain.push(current)
  return chain
}

// The frame exists once the modal has rendered its content, which can be after the open flag changes. Focus moves when
// the frame appears in the page, so that it lands on the rendered buttons.
watch(frame, (element) => {
  if (!element) return
  const target = element.querySelector<HTMLElement>('[data-autofocus]') ?? focusables()[0]
  target?.focus()
}, { flush: 'post' })

// The dialog role and its name belong to one element. vue-final-modal renders its root with the fixed role "dialog", and
// a role passed through attrs does not replace it, so the root gets the role of the `role` prop after it renders. The
// frame carries neither. Limit: the root's role is written to the DOM outside the template. Vue does not reset it on a
// later render, because the template value stays "dialog". A vue-final-modal release that binds the role from a prop
// needs this step removed.
const applyRootRole = () => {
  frame.value?.closest('.vfm')?.setAttribute('role', props.role)
}
watch(frame, applyRootRole, { flush: 'post' })
watch(() => props.role, applyRootRole, { flush: 'post' })

watch(() => props.modelValue, async (open) => {
  if (open) {
    openerChain = chainFrom(document.activeElement as HTMLElement | null)
  } else {
    await nextTick()
    openerChain.find(element => element.isConnected)?.focus()
    openerChain = []
  }
}, { immediate: true })

// Tab leaves the dialog from its last element to the first, and Shift Tab from the first to the last. A focus that is
// outside the dialog is brought in, from the start.
const onKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Tab') return
  const items = focusables()
  if (items.length === 0) return
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  const outside = !frame.value?.contains(active)
  if (event.shiftKey && (outside || active === first)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (outside || active === last)) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    class="z-md-dialog flex items-center justify-center"
    overlay-class="bg-scrim/md-scrim!"
    content-class="w-full px-6"
    teleport-to="body"
    :aria-labelledby="labelledby"
    :aria-describedby="describedby"
    :focus-trap="false"
    :click-to-close="dismissable"
    :esc-to-close="dismissable"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('opened')"
    @closed="emit('closed')"
  >
    <div
      ref="frame"
      :class="[SIZES[size], 'mx-auto max-h-[90dvh] w-full min-w-70 overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3']"
      data-ingot-component="dialog-shell"
      @keydown="onKeydown"
    >
      <slot name="header" />
      <slot />
      <slot name="actions" />
    </div>
  </VueFinalModal>
</template>
