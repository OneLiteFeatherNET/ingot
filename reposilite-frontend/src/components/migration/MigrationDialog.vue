<script setup>
/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// A modal dialog of the import wizard. It takes the focus when it opens, keeps Tab inside it, closes on Escape,
// and gives the focus back to the element that opened it. The page behind it is not reachable while it is open.
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  titleId: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])

const panel = ref(null)
let opener = null

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
const focusables = () => (panel.value ? [...panel.value.querySelectorAll(FOCUSABLE)] : [])

watch(() => props.modelValue, async (open) => {
  if (open) {
    opener = document.activeElement
    await nextTick()
    const first = focusables()[0]
    ;(first ?? panel.value)?.focus()
    return
  }
  if (opener && typeof opener.focus === 'function') opener.focus()
  opener = null
})

const onKeydown = (event) => {
  if (event.key === 'Escape') {
    event.stopPropagation()
    emit('update:modelValue', false)
    return
  }
  if (event.key !== 'Tab') return
  const items = focusables()
  if (items.length === 0) return
  const first = items[0]
  const last = items[items.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/md-scrim-strong p-4"
      data-ingot-component="migration-dialog"
      @keydown="onKeydown"
    >
      <div
        ref="panel"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
        class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface"
      >
        <h2
          :id="titleId"
          class="md-headline-small"
        >
          {{ title }}
        </h2>
        <div class="mt-3 md-body-medium text-on-surface-variant">
          <slot />
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
