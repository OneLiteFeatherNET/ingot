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
import { ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import { SCRIM_STYLE } from '../settings/dialog'

/*
 * Asks before a declared type drops empty mirrored entries. Confirm writes the type; Cancel and Escape leave the
 * repository as it is. Initial focus is on Cancel, and when the dialog closes without a confirmation the focus goes
 * back to the button that opened it.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  // The name of the type that is declared, such as Proxy.
  typeLabel: {
    type: String,
    required: true
  },
  // The line that says how many empty entries go, or nothing.
  notice: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const cancel = ref(null)
let opener = null
let confirmed = false

watch(() => props.modelValue, (open) => {
  if (open) {
    opener = document.activeElement
    confirmed = false
    return
  }
  if (!confirmed && opener?.isConnected) opener.focus()
})

// Cancel takes the focus as soon as it is drawn, and the focus trap of the modal starts on it too.
watch(cancel, (button) => {
  if (button && props.modelValue) button.$el.focus()
})
const focusTrap = {
  allowOutsideClick: true,
  initialFocus: () => cancel.value?.$el
}

const close = () => emit('update:modelValue', false)

const confirm = () => {
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
      data-test="declare-type-dialog"
      class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      aria-labelledby="declare-type-title"
      aria-describedby="declare-type-body"
      role="alertdialog"
      aria-modal="true"
      novalidate
      @submit.prevent="confirm"
    >
      <h2
        id="declare-type-title"
        class="md-headline-small text-on-surface"
      >
        Declare {{ typeLabel }}?
      </h2>
      <p
        id="declare-type-body"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        The repository gets the {{ typeLabel }} type. {{ notice }}
      </p>
      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <MdButton
          ref="cancel"
          variant="text"
          type="button"
          @click="close"
        >
          Cancel
        </MdButton>
        <MdButton
          variant="filled"
          type="submit"
        >
          Declare {{ typeLabel }}
        </MdButton>
      </div>
    </form>
  </VueFinalModal>
</template>
