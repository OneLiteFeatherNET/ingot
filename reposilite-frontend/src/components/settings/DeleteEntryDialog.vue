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
import { computed, ref, useId, watch } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'
import MdTextField from '../md/MdTextField.vue'

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
const titleId = useId()
const bodyId = useId()

watch(() => props.modelValue, (open) => {
  if (open) typed.value = ''
})

const close = () => emit('update:modelValue', false)

const confirm = () => {
  if (!matches.value) return
  emit('confirm')
  close()
}
</script>

<template>
  <MdDialog
    :model-value="modelValue"
    :labelledby="titleId"
    :describedby="bodyId"
    role="alertdialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div data-ingot-component="delete-entry-dialog">
      <h2
        :id="titleId"
        class="md-headline-small text-on-surface"
      >
        Delete {{ id }}?
      </h2>
      <p
        :id="bodyId"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        It is removed from the configuration when you save. Type its id to confirm.
      </p>
      <form
        id="delete-entry-form"
        novalidate
        @submit.prevent="confirm"
      >
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
        <MdDialogActions class="mt-6">
          <MdButton
            id="delete-entry-cancel"
            data-autofocus
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
        </MdDialogActions>
      </form>
    </div>
  </MdDialog>
</template>
