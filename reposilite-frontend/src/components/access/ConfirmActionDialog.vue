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
import { computed, ref, useId } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'

/*
 * A confirmation before a change that matters. With `typeToConfirm` the user has to type that
 * text first, and only an exact match enables the button.
 *
 * Props:
 *   modelValue     whether the dialog is open (v-model)
 *   title          the heading
 *   confirmLabel   the text of the confirming button
 *   danger         draws the confirming button in the error colour
 *   typeToConfirm  the text to type, or an empty string for a plain confirmation
 *   typeLabel      the label of the typing field
 *   busy           true while the caller's request runs; the button is disabled
 *   failure        the message of a failed request, shown in the dialog (a 409 for the last
 *                  instance admin arrives here with the server's own wording)
 * Slot: the explanation under the heading.
 * Emits update:modelValue and confirm. The caller makes the request and sets busy and failure.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    required: true
  },
  confirmLabel: {
    type: String,
    required: true
  },
  danger: {
    type: Boolean,
    default: false
  },
  typeToConfirm: {
    type: String,
    default: ''
  },
  typeLabel: {
    type: String,
    default: 'Type the name to confirm'
  },
  busy: {
    type: Boolean,
    default: false
  },
  failure: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const titleId = `confirm-title-${useId()}`
const formId = `confirm-form-${useId()}`
const typed = ref('')

const confirmed = computed(() => props.typeToConfirm === '' || typed.value === props.typeToConfirm)

const submit = () => {
  if (!confirmed.value || props.busy) return
  emit('confirm')
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    :labelledby="titleId"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="typed = ''"
  >
    <h2
      :id="titleId"
      class="md-headline-small"
    >
      {{ title }}
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      <slot />
    </p>

    <form
      :id="formId"
      class="mt-6 flex flex-col gap-4"
      @submit.prevent="submit()"
    >
      <MdTextField
        v-if="typeToConfirm !== ''"
        v-model="typed"
        data-test="confirm-typed"
        :label="typeLabel"
        type="text"
        autocomplete="off"
      />
      <p
        v-if="failure"
        role="alert"
        data-test="confirm-failure"
        class="md-body-medium text-error"
      >
        {{ failure }}
      </p>
    </form>

    <div class="mt-6 flex justify-end gap-2">
      <MdButton
        variant="text"
        @click="emit('update:modelValue', false)"
      >
        Cancel
      </MdButton>
      <MdButton
        :error="danger"
        type="submit"
        :form="formId"
        data-test="confirm-button"
        :disabled="!confirmed || busy"
      >
        {{ confirmLabel }}
      </MdButton>
    </div>
  </DialogShell>
</template>
