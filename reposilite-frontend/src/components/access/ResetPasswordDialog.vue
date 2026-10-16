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
import { computed, ref } from 'vue'
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'
import { validateNewPassword } from './accessPrincipals'

/*
 * Sets a new password for a user. The user has to change it at the next sign-in, so the admin
 * never knows the password that stays in use.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   principal   the user, at least { name }
 *   api         { setPassword(name, password) }: resolves on the server's 204
 * Emits update:modelValue and done, once the server has accepted the password.
 *
 * Both fields live in refs and are cleared when the dialog closes. Nothing is stored.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  principal: {
    type: Object,
    default: null
  },
  api: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'done'])

const password = ref('')
const confirm = ref('')
const submitted = ref(false)
const busy = ref(false)
const failure = ref('')

const problems = computed(() => validateNewPassword(password.value, confirm.value))

const reset = () => {
  password.value = ''
  confirm.value = ''
  submitted.value = false
  busy.value = false
  failure.value = ''
}

const submit = async () => {
  submitted.value = true
  failure.value = ''
  if (problems.value.password || problems.value.confirm) return
  busy.value = true
  try {
    await props.api.setPassword(props.principal.name, password.value)
    createSuccessToast(`Password reset. ${props.principal.name} must choose a new one at the next sign-in.`)
    emit('done', props.principal)
    emit('update:modelValue', false)
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="reset-password-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="reset-password-title"
      class="md-headline-small"
    >
      Reset password
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      Set a new password for
      <span class="font-medium text-on-surface">{{ principal?.name }}</span>.
      They must change it the next time they sign in.
    </p>

    <form
      id="reset-password-form"
      class="mt-6 flex flex-col gap-4"
      novalidate
      @submit.prevent="submit()"
    >
      <MdTextField
        id="reset-password-new"
        v-model="password"
        label="New password"
        type="password"
        autocomplete="new-password"
        supporting-text="At least 12 characters"
        required
        :error="submitted && problems.password !== ''"
        :error-text="problems.password"
      />
      <MdTextField
        id="reset-password-confirm"
        v-model="confirm"
        label="Confirm new password"
        type="password"
        autocomplete="new-password"
        required
        :error="submitted && problems.confirm !== ''"
        :error-text="problems.confirm"
      />
      <p
        v-if="failure"
        role="alert"
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
        type="submit"
        form="reset-password-form"
        :disabled="busy"
      >
        Reset password
      </MdButton>
    </div>
  </DialogShell>
</template>
