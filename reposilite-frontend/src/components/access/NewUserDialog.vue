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
import { createErrorToast, createSuccessToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'
import { MIN_PASSWORD_LENGTH, buildCreateBody, validatePrincipalName } from './accessPrincipals'

/*
 * Creates a user: the principal first, then its initial password. The user has to change that
 * password at the first sign-in.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   api         { create(body), setPassword(name, password) }
 * Emits update:modelValue and created (the username). It is also emitted when the user exists
 * but the password could not be set; the admin is told and can reset the password from the list.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  api: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'created'])

const name = ref('')
const displayName = ref('')
const password = ref('')
const submitted = ref(false)
const busy = ref(false)
const failure = ref('')

const nameProblem = computed(() => validatePrincipalName(name.value))
const passwordProblem = computed(() =>
  password.value.length < MIN_PASSWORD_LENGTH ? `Use at least ${MIN_PASSWORD_LENGTH} characters` : '')

const reset = () => {
  name.value = ''
  displayName.value = ''
  password.value = ''
  submitted.value = false
  busy.value = false
  failure.value = ''
}

const submit = async () => {
  submitted.value = true
  failure.value = ''
  if (nameProblem.value || passwordProblem.value) return
  busy.value = true
  const username = name.value.trim()
  try {
    await props.api.create(buildCreateBody({ kind: 'USER', name: username, displayName: displayName.value }))
  } catch (error) {
    failure.value = errorMessage(error)
    busy.value = false
    return
  }
  try {
    await props.api.setPassword(username, password.value)
    createSuccessToast(`User ${username} created`)
  } catch (error) {
    createErrorToast(`User ${username} was created, but the password could not be set: ${errorMessage(error)}`)
  }
  busy.value = false
  emit('created', username)
  emit('update:modelValue', false)
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="new-user-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="new-user-title"
      class="md-headline-small"
    >
      New user
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      The user must change the initial password at the first sign-in.
    </p>

    <form
      id="new-user-form"
      class="mt-6 flex flex-col gap-4"
      novalidate
      @submit.prevent="submit()"
    >
      <MdTextField
        id="new-user-name"
        v-model="name"
        label="Username"
        type="text"
        autocomplete="off"
        required
        :error="submitted && nameProblem !== ''"
        :error-text="nameProblem"
      />
      <MdTextField
        id="new-user-display-name"
        v-model="displayName"
        label="Display name"
        type="text"
        autocomplete="off"
        supporting-text="Optional"
      />
      <MdTextField
        id="new-user-password"
        v-model="password"
        label="Initial password"
        type="password"
        autocomplete="new-password"
        supporting-text="At least 12 characters"
        required
        :error="submitted && passwordProblem !== ''"
        :error-text="passwordProblem"
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
        form="new-user-form"
        :disabled="busy"
      >
        Create user
      </MdButton>
    </div>
  </DialogShell>
</template>
