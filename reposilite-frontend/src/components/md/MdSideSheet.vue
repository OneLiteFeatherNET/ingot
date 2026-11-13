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
import { nextTick, ref, useId, watch } from 'vue'
import MdIconButton from './MdIconButton.vue'

/*
 * A modal side sheet: a panel from the leading edge of the window, over a scrim, for one task that the page does not
 * need to stay visible for. It follows the WAI-ARIA dialog pattern. It is drawn in place, not teleported, so it sits in
 * the page it was opened from.
 *
 * Props:
 *   open   whether the sheet is shown (v-model:open)
 *   title  the heading of the sheet, which is also its accessible name
 * Slots:
 *   default  the content of the sheet
 * Emits:
 *   update:open  the sheet asks to close (Escape, the close button or the scrim); the page sets `open` to false
 *
 * Focus: on open, focus moves to the first focusable element of the sheet, and Tab and Shift Tab stay inside it. When the
 * sheet closes by its own means (Escape, the close button, the scrim), focus returns to the element that had it on
 * open. A close that the page starts, such as a choice of an entry, leaves the focus where the page puts it.
 *
 * Motion: the fade is off for a visitor who asks for reduced motion (prefers-reduced-motion).
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true }
})

const emit = defineEmits(['update:open'])

const sheet = ref(null)
const titleId = useId()
let opener = null

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(', ')
const focusables = () => [...(sheet.value?.querySelectorAll(FOCUSABLE) ?? [])]

watch(() => props.open, async (open) => {
  if (!open) return
  opener = document.activeElement
  await nextTick()
  focusables()[0]?.focus()
}, { immediate: true })

const dismiss = async () => {
  emit('update:open', false)
  await nextTick()
  opener?.focus()
}

// Tab leaves the sheet from its last element to the first, and Shift Tab from the first to the last. A focus that is
// outside the sheet is brought in, from the start.
const trapFocus = (event) => {
  const items = focusables()
  if (items.length === 0) return
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement
  const outside = !sheet.value.contains(active)
  if (event.shiftKey && (outside || active === first)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (outside || active === last)) {
    event.preventDefault()
    first.focus()
  }
}

// An Escape that a field inside the sheet has already used (it cleared its text) is not a reason to close the sheet.
const onKeydown = (event) => {
  if (event.key === 'Escape' && !event.defaultPrevented) {
    event.preventDefault()
    dismiss()
  } else if (event.key === 'Tab') {
    trapFocus(event)
  }
}
</script>

<template>
  <Transition
    appear
    enter-active-class="motion-safe:transition-opacity motion-safe:duration-200 motion-reduce:transition-none"
    enter-from-class="opacity-0"
    leave-active-class="motion-safe:transition-opacity motion-safe:duration-150 motion-reduce:transition-none"
    leave-to-class="opacity-0"
  >
    <div
      v-if="open"
      ref="sheet"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      data-ingot-component="md-side-sheet"
      class="fixed inset-0 z-50 motion-reduce:transition-none"
      @keydown="onKeydown"
    >
      <div
        class="absolute inset-0 bg-[color-mix(in_srgb,var(--md-sys-color-scrim)_32%,transparent)]"
        aria-hidden="true"
        @click="dismiss"
      />
      <div class="absolute inset-y-0 start-0 flex w-[min(320px,calc(100vw-56px))] flex-col gap-4 overflow-y-auto bg-surface-container-low p-4 text-on-surface shadow-elevation-2">
        <div class="flex items-center justify-between gap-2">
          <h2
            :id="titleId"
            class="md-title-large text-on-surface"
          >
            {{ title }}
          </h2>
          <MdIconButton
            aria-label="Close"
            @click="dismiss"
          >
            <svg
              class="size-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </MdIconButton>
        </div>
        <slot />
      </div>
    </div>
  </Transition>
</template>
