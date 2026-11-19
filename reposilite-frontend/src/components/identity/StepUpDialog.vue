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
import { ref } from 'vue'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import MdDialog from '../md/MdDialog.vue'

/*
 * Asks for a fresh second factor before a sensitive action runs. The session opens it when the server refuses an action with
 * step_up_required; confirming retries the action, dismissing refuses it. The code is an authenticator code or a recovery code,
 * and the server decides which one it accepts.
 */
const { stepUp, confirmStepUp, cancelStepUp } = useSession()

const code = ref('')

const confirm = async () => {
  if (!code.value.trim() || stepUp.value?.busy) return
  await confirmStepUp(code.value.trim())
  if (stepUp.value === undefined) {
    code.value = ''
  }
}

const dismiss = () => {
  code.value = ''
  cancelStepUp()
}

const onOpenChange = (open: boolean) => {
  if (!open) dismiss()
}
</script>

<template>
  <MdDialog
    :model-value="stepUp !== undefined"
    labelledby="step-up-title"
    @update:model-value="onOpenChange"
  >
    <h2
      id="step-up-title"
      class="md-headline-small"
      data-ingot-component="step-up-dialog"
    >
      Confirm it is you
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      <template v-if="stepUp?.methods.includes('AUTHENTICATOR')">
        Enter the code of your authenticator app, or a recovery code.
      </template>
      <template v-else>
        Enter a recovery code.
      </template>
      The action continues once the code is accepted.
    </p>

    <form
      id="step-up-form"
      class="mt-6 flex flex-col gap-4"
      @submit.prevent="confirm()"
    >
      <MdTextField
        id="step-up-code"
        v-model="code"
        label="Code or recovery code"
        type="text"
        autocomplete="one-time-code"
      />
      <p
        v-if="stepUp?.message"
        data-ingot-state="error"
        role="alert"
        class="md-body-medium text-error"
      >
        {{ stepUp.message }}
      </p>
    </form>

    <div class="mt-6 flex justify-end gap-2">
      <MdButton
        variant="text"
        @click="dismiss()"
      >
        Cancel
      </MdButton>
      <MdButton
        type="submit"
        form="step-up-form"
        :disabled="!code.trim() || stepUp?.busy"
      >
        Confirm
      </MdButton>
    </div>
  </MdDialog>
</template>
