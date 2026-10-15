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
  }
})

const emit = defineEmits(['save', 'discard'])

const countText = computed(() => `${props.count} unsaved ${props.count === 1 ? 'change' : 'changes'}`)
</script>

<template>
  <div
    role="status"
    aria-live="polite"
    class="flex flex-col gap-3 border-t border-outline-variant bg-surface-container px-4 py-3 shadow-elevation-2 sm:flex-row sm:items-center sm:justify-between md:px-6"
  >
    <div class="min-w-0 md-body-large">
      <p class="text-on-surface">
        {{ countText }}
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
