<!--
  - Copyright (c) 2023 dzikoysk
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
import { nextTick, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { useSession } from '../../store/session'
import { loginRequests } from '../../store/login'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'

const { login, loginFailureMessage } = useSession()
const showLogin = ref(false)

// Other views ask for the dialog through openLogin(); see store/login.js.
watch(loginRequests, () => {
  showLogin.value = true
})
const name = ref('')
const secret = ref('')
const failure = ref(null)
const busy = ref(false)

const NAME_FIELD_ID = 'login-name'
const SECRET_FIELD_ID = 'login-secret'

const close = () => {
  showLogin.value = false
  failure.value = null
}

// The field is only in the document once the dialog has opened, so focus waits for that.
const focusName = () => document.getElementById(NAME_FIELD_ID)?.focus()

// The failure belongs in the dialog, not in a toast: the form that has to be corrected is
// right here, and a toast about it appears somewhere else entirely and then leaves. Focus
// moves to the secret field, which carries the message, so that it is read out.
const signin = () => {
  const user = name.value
  busy.value = true
  failure.value = null

  return login(user, secret.value)
    .then(() => close())
    .catch(error => {
      failure.value = loginFailureMessage(error)
      return nextTick().then(() => document.getElementById(SECRET_FIELD_ID)?.focus())
    })
    .finally(() => {
      busy.value = false
    })
}

// A scrim in the role colour. The modal's own stylesheet paints the overlay in a fixed black
// and is not layered, so the colour goes in as an inline style that wins over it.
const overlayStyle = {
  backgroundColor: 'color-mix(in srgb, var(--md-sys-color-scrim) 32%, transparent)'
}
</script>

<template>
  <div>
    <MdButton
      variant="tonal"
      @click="showLogin = true"
    >
      Sign in
    </MdButton>
    <VueFinalModal
      v-model="showLogin"
      class="flex items-center justify-center"
      content-class="w-full px-6"
      teleport-to="body"
      :overlay-style="overlayStyle"
      aria-labelledby="login-title"
      @opened="focusName"
    >
      <!-- The 24px side inset is set on the modal's content wrapper; the dialog caps at 560px. -->
      <div class="mx-auto w-full min-w-70 max-w-140 rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3">
        <h2
          id="login-title"
          class="md-headline-small"
        >
          Sign in with access token
        </h2>

        <form
          class="mt-6 flex flex-col gap-4"
          @submit.prevent="signin()"
        >
          <MdTextField
            id="login-name"
            v-model="name"
            label="Name"
            type="text"
            autocomplete="username"
            required
            :error="failure !== null"
          />

          <MdTextField
            id="login-secret"
            v-model="secret"
            label="Secret"
            type="password"
            autocomplete="current-password"
            required
            :error="failure !== null"
            :error-text="failure || ''"
          />

          <div class="mt-2 flex justify-end gap-2">
            <MdButton
              variant="text"
              @click="close()"
            >
              Cancel
            </MdButton>
            <MdButton
              type="submit"
              :disabled="busy"
            >
              {{ busy ? 'Signing in...' : 'Sign in' }}
            </MdButton>
          </div>
        </form>
      </div>
    </VueFinalModal>
  </div>
</template>
