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
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import { SCRIM_STYLE } from '../settings/dialog'

/*
 * Removes a policy from the settings. It does not touch any artifact: the policy is only the rule
 * that a run would apply, and a run is the only thing that deletes.
 */
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  policyId: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const close = () => emit('update:modelValue', false)

const confirm = () => {
  emit('confirm')
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
    <div
      class="w-[calc(100vw-2rem)] max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="delete-policy-title"
      aria-describedby="delete-policy-body"
    >
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
      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <MdButton
          id="delete-policy-cancel"
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
      </div>
    </div>
  </VueFinalModal>
</template>
