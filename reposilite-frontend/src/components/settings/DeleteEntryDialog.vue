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
import { computed, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import { SCRIM_STYLE } from './dialog'

/*
 * Confirms the removal of one entry. The delete action stays disabled until the id is typed
 * exactly, with the same case and no stray spaces, so an accidental Enter cannot remove an entry.
 * `references` names the groups that list the entry as a member; they are shown before the confirm
 * button. Initial focus is on Cancel, and a dialog closed without a confirmation returns the focus to
 * the control that opened it.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  id: {
    type: String,
    required: true
  },
  references: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const typed = ref('')
const matches = computed(() => typed.value === props.id)
const cancel = ref(null)
let opener = null
let confirmed = false

watch(() => props.modelValue, (open) => {
  if (open) {
    typed.value = ''
    opener = document.activeElement
    confirmed = false
    return
  }
  if (!confirmed && opener?.isConnected) opener.focus()
})

// Cancel takes the focus as soon as it is drawn, and the focus trap of the modal starts on it too, so the modal's own
// first focus (the id field) does not take it away.
watch(cancel, (button) => {
  if (button && props.modelValue) button.$el.focus()
})
const focusTrap = {
  allowOutsideClick: true,
  initialFocus: () => cancel.value?.$el
}

const close = () => emit('update:modelValue', false)

const confirm = () => {
  if (!matches.value) return
  confirmed = true
  emit('confirm')
  close()
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    class="flex min-h-full items-center justify-center p-4"
    :focus-trap="focusTrap"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <form
      id="delete-entry-form"
      class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      aria-labelledby="delete-entry-title"
      aria-describedby="delete-entry-body"
      role="alertdialog"
      aria-modal="true"
      novalidate
      data-ingot-component="delete-entry-dialog"
      @submit.prevent="confirm"
    >
      <h2
        id="delete-entry-title"
        class="md-headline-small text-on-surface"
      >
        Delete {{ id }}?
      </h2>
      <p
        id="delete-entry-body"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        It is removed from the configuration when you save. Type its id to confirm.
      </p>
      <div
        v-if="references.length > 0"
        data-test="delete-references"
        class="mt-4 flex flex-col gap-1"
      >
        <p class="md-body-medium text-warning">
          A group lists this repository as a member. Removing it leaves the group without that member.
        </p>
        <ul class="m-0 flex list-disc flex-col gap-1 pl-5 md-body-medium text-on-surface">
          <li
            v-for="group in references"
            :key="group"
          >
            {{ group }}
          </li>
        </ul>
      </div>
      <div class="mt-6">
        <MdTextField
          id="delete-entry-confirm-input"
          label="Id"
          variant="outlined"
          autocomplete="off"
          :model-value="typed"
          :supporting-text="`Type ${id} exactly.`"
          @update:model-value="typed = $event"
        />
      </div>
      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <MdButton
          id="delete-entry-cancel"
          ref="cancel"
          variant="text"
          type="button"
          @click="close"
        >
          Cancel
        </MdButton>
        <MdButton
          id="delete-entry-confirm"
          variant="filled"
          error
          type="submit"
          :disabled="!matches"
        >
          Delete
        </MdButton>
      </div>
    </form>
  </VueFinalModal>
</template>
