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
import { computed, nextTick, onMounted, ref } from 'vue'
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'

/*
 * The change password card.
 *
 * Props:
 *   api        { changePassword({ currentPassword, newPassword }) }: resolves on the server's 204
 *   available  false in a token session, which has no password; the form is replaced by a note
 *   focus      true moves the focus to the first field once the card is on screen
 * Emits changed, once the server has accepted the new password.
 *
 * The three passwords live in refs and are cleared after a success. Nothing is stored.
 */
const props = defineProps({
  api: {
    type: Object,
    required: true
  },
  available: {
    type: Boolean,
    default: true
  },
  focus: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['changed'])

const MIN_PASSWORD_LENGTH = 12

const current = ref('')
const next = ref('')
const confirm = ref('')
const submitted = ref(false)
const busy = ref(false)
const failure = ref(null)

const currentError = computed(() => submitted.value && current.value === '')
const nextError = computed(() => submitted.value && next.value.length < MIN_PASSWORD_LENGTH)
const confirmError = computed(() => submitted.value && confirm.value !== next.value)

onMounted(async () => {
  if (!props.focus || !props.available) return
  await nextTick()
  document.getElementById('account-current-password')?.focus()
})

const submit = async () => {
  submitted.value = true
  failure.value = null
  if (currentError.value || nextError.value || confirmError.value) {
    return
  }
  busy.value = true
  try {
    await props.api.changePassword({ currentPassword: current.value, newPassword: next.value })
    current.value = ''
    next.value = ''
    confirm.value = ''
    submitted.value = false
    createSuccessToast('Password changed')
    emit('changed')
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section
    class="flex flex-col gap-4 rounded-corner-large bg-surface-container-low px-4 py-4"
    aria-labelledby="password-title"
  >
    <h3
      id="password-title"
      class="md-title-large text-on-surface"
    >
      Change password
    </h3>

    <p
      v-if="!available"
      class="md-body-medium text-on-surface-variant"
    >
      You are signed in with an access token, so there is no password to change here.
      Sign in with your username and password to change it.
    </p>

    <form
      v-else
      id="account-password-form"
      class="flex flex-col gap-4"
      novalidate
      @submit.prevent="submit()"
    >
      <MdTextField
        id="account-current-password"
        v-model="current"
        label="Current password"
        type="password"
        autocomplete="current-password"
        required
        :error="currentError"
        error-text="Enter your current password"
      />
      <MdTextField
        id="account-new-password"
        v-model="next"
        label="New password"
        type="password"
        autocomplete="new-password"
        supporting-text="At least 12 characters"
        required
        :error="nextError"
        error-text="Use at least 12 characters"
      />
      <MdTextField
        id="account-confirm-password"
        v-model="confirm"
        label="Confirm new password"
        type="password"
        autocomplete="new-password"
        required
        :error="confirmError"
        error-text="Passwords do not match"
      />
      <p
        v-if="failure"
        role="alert"
        class="md-body-medium text-error"
      >
        {{ failure }}
      </p>
      <div class="flex justify-end">
        <MdButton
          type="submit"
          :disabled="busy"
        >
          {{ busy ? 'Changing...' : 'Change password' }}
        </MdButton>
      </div>
    </form>
  </section>
</template>
