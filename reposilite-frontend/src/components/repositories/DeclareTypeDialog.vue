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
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'

/*
 * Asks before a declared type drops empty mirrored entries. Confirm writes the type; Cancel and Escape leave the
 * repository as it is. Initial focus is on Cancel. When the dialog closes, the focus goes back to the button that
 * opened it, or to its nearest ancestor once the declaration has removed that button.
 */
withDefaults(defineProps<{
  modelValue?: boolean
  // The name of the type that is declared, such as Proxy.
  typeLabel: string
  // The line that says how many empty entries go, or nothing.
  notice?: string
}>(), {
  modelValue: false,
  notice: ''
})

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  confirm: []
}>()

const close = () => emit('update:modelValue', false)

const confirm = () => {
  emit('confirm')
  close()
}
</script>

<template>
  <MdDialog
    :model-value="modelValue"
    labelledby="declare-type-title"
    describedby="declare-type-body"
    role="alertdialog"
    size="md"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <form
      data-test="declare-type-dialog"
      novalidate
      data-ingot-component="declare-type-dialog"
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
      <MdDialogActions class="mt-6">
        <MdButton
          data-autofocus
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
      </MdDialogActions>
    </form>
  </MdDialog>
</template>
