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
import { ref } from 'vue'
import MdButton from '../md/MdButton.vue'

/*
 * The download of the settings. The default is the saved state, the last one the server confirmed.
 * The draft, edits included, is offered only when there are unsaved changes, and it is labelled as
 * such, because it is not a backup of what the server holds.
 */
defineProps({
  disabled: {
    type: Boolean,
    default: false
  },
  hasUnsaved: {
    type: Boolean,
    default: false
  },
  canIncludeUnsaved: {
    type: Boolean,
    default: true
  },
  secretsHidden: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['saved', 'draft'])

const open = ref(false)

const choose = (event) => {
  open.value = false
  emit(event)
}

const onKeydown = (event) => {
  if (event.key === 'Escape') open.value = false
}

// Closes the menu when the focus leaves it, so a click elsewhere does not leave it hanging open.
const onFocusout = (event) => {
  if (!event.currentTarget.contains(event.relatedTarget)) open.value = false
}
</script>

<template>
  <div
    class="relative shrink-0"
    @keydown="onKeydown"
    @focusout="onFocusout"
  >
    <MdButton
      variant="outlined"
      type="button"
      :disabled="disabled"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      @click="open = !open"
    >
      Download as JSON
    </MdButton>
    <div
      v-if="open"
      role="menu"
      aria-label="Download options"
      class="absolute right-0 z-20 mt-2 flex w-[min(22rem,calc(100vw-2rem))] flex-col gap-1 rounded-corner-extra-small bg-surface-container p-2 shadow-elevation-2"
    >
      <button
        type="button"
        role="menuitem"
        class="md-state-layer relative flex flex-col items-start rounded-corner-small px-3 py-2 text-left text-on-surface"
        @click="choose('saved')"
      >
        <span class="md-label-large">Download saved settings</span>
        <span class="md-body-small text-on-surface-variant">The last state the server confirmed.</span>
      </button>
      <button
        v-if="hasUnsaved"
        type="button"
        role="menuitem"
        class="md-state-layer relative flex flex-col items-start rounded-corner-small px-3 py-2 text-left text-on-surface disabled:text-on-surface/38"
        :disabled="!canIncludeUnsaved"
        @click="choose('draft')"
      >
        <span class="md-label-large">Download including unsaved changes</span>
        <span class="md-body-small text-on-surface-variant">Includes edits that are not saved yet, so it is not a copy of the server state.</span>
      </button>
      <p
        v-if="secretsHidden"
        class="px-3 pt-1 pb-1 md-body-small text-on-surface-variant"
      >
        Secrets are not included. The server hides them, and the file holds ******** in their place.
      </p>
    </div>
  </div>
</template>
