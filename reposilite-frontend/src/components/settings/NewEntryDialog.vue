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
import { validateNewId } from './collections'
import { SCRIM_STYLE } from './dialog'

/*
 * Asks for the id of a new entry and nothing else. The id is the only field every entry needs,
 * and the rest of the entry is edited in the detail pane once it exists. Validation is shown after
 * the first keystroke or the first attempt to submit, so the dialog does not open with an error.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  noun: {
    type: String,
    default: 'entry'
  },
  existingIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'create'])

const id = ref('')
const touched = ref(false)

const error = computed(() => validateNewId(id.value, props.existingIds, props.noun))
const shownError = computed(() => (touched.value ? error.value : ''))

// Every opening starts from an empty field, so a refused id from an earlier attempt does not linger.
watch(() => props.modelValue, (open) => {
  if (open) {
    id.value = ''
    touched.value = false
  }
})

const onInput = (value) => {
  id.value = value
  touched.value = true
}

const close = () => emit('update:modelValue', false)

const submit = () => {
  touched.value = true
  if (error.value) return
  emit('create', id.value)
  close()
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    class="flex min-h-full items-center justify-center p-4"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <form
      id="new-entry-form"
      class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3 [--md-field-container:var(--md-sys-color-surface-container-high)]"
      aria-labelledby="new-entry-title"
      aria-describedby="new-entry-body"
      novalidate
      role="alertdialog"
      aria-modal="true"
      @submit.prevent="submit"
    >
      <h2
        id="new-entry-title"
        class="md-headline-small text-on-surface"
      >
        New {{ noun }}
      </h2>
      <p
        id="new-entry-body"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        The id is how the {{ noun }} is addressed. The other settings are edited once it exists.
      </p>
      <div class="mt-6">
        <MdTextField
          id="new-entry-id"
          label="Id"
          variant="outlined"
          autocomplete="off"
          required
          :model-value="id"
          supporting-text="Letters, digits, dots, underscores and hyphens."
          :error="Boolean(shownError)"
          :error-text="shownError"
          @update:model-value="onInput"
        />
      </div>
      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <MdButton
          id="new-entry-cancel"
          variant="text"
          type="button"
          @click="close"
        >
          Cancel
        </MdButton>
        <MdButton
          id="new-entry-submit"
          variant="filled"
          type="submit"
        >
          Create
        </MdButton>
      </div>
    </form>
  </VueFinalModal>
</template>
