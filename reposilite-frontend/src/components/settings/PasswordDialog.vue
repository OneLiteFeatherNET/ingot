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
import { computed, ref, useId, watch } from 'vue'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'
import MdTextField from '../md/MdTextField.vue'
import { MIN_PASSWORD_LENGTH, PASSWORD_PROBLEM_MESSAGES, passwordProblem } from './settingsEncryption'
import { passphraseGuidance } from './passphraseGuidance'

/*
 * Asks for the password of the settings file. An export asks twice, because a file that cannot be opened
 * is lost. The password is held here only while the dialog is open: it is cleared when the dialog closes
 * and after it is submitted, so it does not stay in the component.
 */
const props = withDefaults(defineProps<{
  open?: boolean
  mode?: 'export' | 'import'
  busy?: boolean
  error?: string
}>(), {
  open: false,
  mode: 'export',
  busy: false,
  error: ''
})

const emit = defineEmits<{
  submit: [password: string]
  cancel: []
}>()

const password = ref('')
const confirmation = ref('')
const attempted = ref(false)
const visible = ref(false)

const isExport = computed(() => props.mode === 'export')
const problem = computed(() => passwordProblem({
  password: password.value,
  confirmation: isExport.value ? confirmation.value : null
}))
const message = computed(() => (problem.value ? PASSWORD_PROBLEM_MESSAGES[problem.value] : ''))
const strength = computed(() => passphraseGuidance({
  password: password.value,
  confirmation: isExport.value ? confirmation.value : null
}))
const inputType = computed(() => (visible.value ? 'text' : 'password'))
const title = computed(() => (isExport.value ? 'Download the settings with a password' : 'Open the settings file'))

const titleId = useId()

const toggleVisible = () => {
  visible.value = !visible.value
}

const clear = () => {
  password.value = ''
  confirmation.value = ''
  attempted.value = false
  visible.value = false
}

watch(() => props.open, (open) => {
  if (!open) clear()
})

const submit = () => {
  attempted.value = true
  if (problem.value !== null || props.busy) return
  const value = password.value
  clear()
  emit('submit', value)
}

const cancel = () => {
  clear()
  emit('cancel')
}

// Escape and a click on the scrim close the dialog through the frame; that is a cancel.
const onOpenChange = (value: boolean) => {
  if (!value) cancel()
}
</script>

<template>
  <MdDialog
    :model-value="open"
    :labelledby="titleId"
    @update:model-value="onOpenChange"
  >
    <form
      novalidate
      data-ingot-component="password-dialog"
      @submit.prevent="submit"
    >
      <h2
        :id="titleId"
        class="md-headline-small mb-4 text-on-surface"
      >
        {{ title }}
      </h2>

      <MdTextField
        id="settings-password"
        v-model="password"
        label="Password"
        variant="outlined"
        :type="inputType"
        autocomplete="new-password"
        :supporting-text="isExport ? `At least ${MIN_PASSWORD_LENGTH} characters. The file cannot be opened without it, and nobody can recover it for you. A passphrase of several words is easy to remember and hard to guess, and a password manager can keep it for you.` : ''"
      />
      <div class="my-2 flex items-center justify-between gap-2">
        <p
          v-if="isExport && strength.label"
          id="settings-password-strength"
          class="md-body-small text-on-surface-variant"
          aria-live="polite"
        >
          Strength: {{ strength.label }}
        </p>
        <MdButton
          variant="text"
          type="button"
          class="ml-auto"
          data-test="toggle-passphrase-visibility"
          :aria-pressed="visible ? 'true' : 'false'"
          @click="toggleVisible"
        >
          {{ visible ? 'Hide passphrase' : 'Show passphrase' }}
        </MdButton>
      </div>

      <MdTextField
        v-if="isExport"
        id="settings-password-confirmation"
        v-model="confirmation"
        class="mb-4"
        label="Repeat the password"
        variant="outlined"
        :type="inputType"
        autocomplete="new-password"
      />

      <MdAlert
        v-if="attempted && message"
        tone="error"
        class="mb-4"
      >
        {{ message }}
      </MdAlert>
      <MdAlert
        v-if="error"
        tone="error"
        class="mb-4"
      >
        {{ error }}
      </MdAlert>

      <MdDialogActions>
        <MdButton
          variant="text"
          type="button"
          @click="cancel"
        >
          Cancel
        </MdButton>
        <MdButton
          variant="filled"
          type="submit"
          :disabled="busy"
        >
          {{ isExport ? 'Download' : 'Open' }}
        </MdButton>
      </MdDialogActions>
    </form>
  </MdDialog>
</template>
