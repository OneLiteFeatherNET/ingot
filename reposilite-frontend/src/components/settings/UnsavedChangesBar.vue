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
import MdButton from '../md/MdButton.vue'

/*
 * The action bar of the settings view. The parent shows it only while something is unsaved, and
 * places it at the bottom of the window. Discard only drops the edits; save writes them. Save is
 * off while a domain has an invalid entry, because the server would refuse the write.
 */
const props = defineProps({
  count: {
    type: Number,
    required: true
  },
  invalid: {
    type: Boolean,
    default: false
  },
  saving: {
    type: Boolean,
    default: false
  },
  // The first tab with an error: its label and the address that shows it. The link is drawn while the domain is invalid.
  firstError: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['save', 'discard', 'review', 'show-error'])

// A plain click moves to the tab inside the page. A click with a modifier keeps the browser's own behaviour, such as a new tab.
const showFirstError = (event) => {
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('show-error')
}

const countText = computed(() => `${props.count} unsaved ${props.count === 1 ? 'change' : 'changes'}`)
</script>

<template>
  <div
    role="status"
    aria-live="polite"
    class="flex flex-col gap-3 border-t border-outline-variant bg-surface-container px-4 py-3 shadow-elevation-2 sm:flex-row sm:items-center sm:justify-between md:px-6"
    data-ingot-component="unsaved-changes-bar"
  >
    <div class="min-w-0 md-body-large">
      <p class="text-on-surface">
        <button
          type="button"
          aria-haspopup="dialog"
          class="md-state-layer relative -mx-2 rounded-corner-small px-2 py-1 text-left text-primary underline decoration-1 underline-offset-4"
          @click="emit('review')"
        >
          {{ countText }}
        </button>
        <span
          v-if="saving"
          class="text-on-surface-variant"
        >Saving…</span>
      </p>
      <p
        v-if="invalid"
        class="md-body-medium text-error"
      >
        Fix the errors before saving.
      </p>
      <a
        v-if="invalid && firstError"
        :href="firstError.href"
        class="md-state-layer md-focus-ring relative -mx-2 inline-flex items-center rounded-corner-small px-2 py-1 md-label-large text-primary underline decoration-1 underline-offset-4"
        data-test="first-error-link"
        @click="showFirstError"
      >
        Show the first error: {{ firstError.label }}
      </a>
      <p class="md-body-medium text-on-surface-variant">
        Other instances pick up saved changes within 10 seconds.
      </p>
    </div>
    <div class="flex shrink-0 items-center justify-end gap-2">
      <MdButton
        variant="text"
        type="button"
        :disabled="saving"
        @click="emit('discard')"
      >
        Discard
      </MdButton>
      <MdButton
        variant="filled"
        type="button"
        :disabled="invalid || saving"
        @click="emit('save')"
      >
        Save
      </MdButton>
    </div>
  </div>
</template>
