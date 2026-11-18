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
 * Removes a policy from the settings. It does not touch any artifact: the policy is only the rule
 * that a run would apply, and a run is the only thing that deletes.
 */
withDefaults(defineProps<{
  modelValue?: boolean
  policyId: string
}>(), {
  modelValue: false
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const close = () => emit('update:modelValue', false)

const confirm = () => {
  emit('confirm')
}
</script>

<template>
  <MdDialog
    :model-value="modelValue"
    :labelledby="'delete-policy-title'"
    :describedby="'delete-policy-body'"
    role="alertdialog"
    size="md"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div data-ingot-component="delete-policy-dialog">
      <h2
        id="delete-policy-title"
        class="md-headline-small text-on-surface"
      >
        Delete {{ policyId }}?
      </h2>
      <p
        id="delete-policy-body"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        The policy is removed from the settings. No artifact is deleted: a policy only deletes when a
        run of it happens.
      </p>
      <MdDialogActions class="mt-6">
        <MdButton
          id="delete-policy-cancel"
          data-autofocus
          variant="text"
          type="button"
          @click="close"
        >
          Cancel
        </MdButton>
        <MdButton
          id="delete-policy-confirm"
          variant="filled"
          error
          type="button"
          @click="confirm"
        >
          Delete policy
        </MdButton>
      </MdDialogActions>
    </div>
  </MdDialog>
</template>
