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
import MdButton from '../md/MdButton.vue'
import { MIN_PASSWORD_LENGTH, PASSWORD_PROBLEM_MESSAGES, passwordProblem } from './settingsEncryption'

/*
 * Asks for the password of the settings file. An export asks twice, because a file that cannot be opened
 * is lost. The password is held here only while the dialog is open: it is cleared when the dialog closes
 * and after it is submitted, so it does not stay in the component.
 */
const props = defineProps({
  open: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'export',
    validator: value => value === 'export' || value === 'import'
  },
  busy: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['submit', 'cancel'])

const password = ref('')
const confirmation = ref('')
const attempted = ref(false)

const isExport = computed(() => props.mode === 'export')
const problem = computed(() => passwordProblem({
  password: password.value,
  confirmation: isExport.value ? confirmation.value : null
}))
const message = computed(() => (problem.value ? PASSWORD_PROBLEM_MESSAGES[problem.value] : ''))
const title = computed(() => (isExport.value ? 'Download the settings with a password' : 'Open the settings file'))

const clear = () => {
  password.value = ''
  confirmation.value = ''
  attempted.value = false
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

const onKeydown = (event) => {
  if (event.key === 'Escape') cancel()
}
</script>

<template>
  <div
    v-if="open"
    class="fixed inset-0 z-50 flex items-center justify-center p-4"
    :style="{ backgroundColor: 'color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)' }"
    @keydown="onKeydown"
  >
    <form
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface"
      @submit.prevent="submit"
    >
      <h2 class="md-headline-small mb-4">
        {{ title }}
      </h2>

      <label class="md-body-medium mb-1 block" for="settings-password">Password</label>
      <input
        id="settings-password"
        v-model="password"
        type="password"
        autocomplete="new-password"
        class="mb-2 w-full rounded-corner-small border border-outline bg-surface px-3 py-2"
        :aria-describedby="isExport ? 'settings-password-hint' : undefined"
      >
      <p
        v-if="isExport"
        id="settings-password-hint"
        class="md-body-small mb-4 text-on-surface-variant"
      >
        At least {{ MIN_PASSWORD_LENGTH }} characters. The file cannot be opened without it, and nobody can recover it for you.
      </p>

      <template v-if="isExport">
        <label class="md-body-medium mb-1 block" for="settings-password-confirmation">Repeat the password</label>
        <input
          id="settings-password-confirmation"
          v-model="confirmation"
          type="password"
          autocomplete="new-password"
          class="mb-4 w-full rounded-corner-small border border-outline bg-surface px-3 py-2"
        >
      </template>

      <p
        v-if="attempted && message"
        role="alert"
        class="md-body-medium mb-4 text-error"
      >
        {{ message }}
      </p>
      <p
        v-if="error"
        role="alert"
        class="md-body-medium mb-4 text-error"
      >
        {{ error }}
      </p>

      <div class="flex justify-end gap-2">
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
      </div>
    </form>
  </div>
</template>
