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
import { ref } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { createSuccessToast } from '../../helpers/toast'
import { useSession } from '../../store/session'
import CloseIcon from '../icons/CloseIcon.vue'
import MenuButton from './MenuButton.vue'

const { login, loginFailureMessage } = useSession()
const showLogin = ref(false)
const name = ref('')
const secret = ref('')
const failure = ref(null)
const busy = ref(false)

const NAME_FIELD_ID = 'login-name'

const close = () => {
  showLogin.value = false
  failure.value = null
}

// The field is only in the document once the dialog has opened, so focus waits for that.
const focusName = () => document.getElementById(NAME_FIELD_ID)?.focus()

// The failure belongs in the dialog, not in a toast: the form that has to be corrected is
// right here, and a toast about it appears somewhere else entirely and then leaves.
const signin = () => {
  const user = name.value
  busy.value = true
  failure.value = null

  return login(user, secret.value)
    .then(() => createSuccessToast(`Dashboard accessed as ${user}`))
    .then(() => close())
    .catch(error => {
      failure.value = loginFailureMessage(error)
    })
    .finally(() => {
      busy.value = false
    })
}
</script>

<template>
  <div>
    <MenuButton @click="showLogin = true">
      Sign in
    </MenuButton>
    <VueFinalModal
      v-model="showLogin"
      class="flex items-center justify-center"
      aria-labelledby="login-title"
      @opened="focusName"
    >
      <div class="relative rounded-2xl border border-gray-100 bg-white px-10 py-5 shadow-xl dark:border-black dark:bg-gray-900">
        <h2
          id="login-title"
          class="pb-4 text-center text-xl font-bold"
        >
          Sign in with access token
        </h2>

        <form
          class="flex w-96 flex-col max-sm:w-65"
          @submit.prevent="signin()"
        >
          <label
            for="login-name"
            class="flex flex-col text-left text-sm"
          >
            <span class="pb-1 text-gray-600 dark:text-gray-300">Name</span>
            <input
              id="login-name"
              v-model="name"
              type="text"
              autocomplete="username"
              required
              :aria-invalid="failure ? 'true' : undefined"
              :aria-describedby="failure ? 'login-failure' : undefined"
              class="rounded-md bg-gray-50 p-2 dark:bg-gray-800"
            >
          </label>

          <label
            for="login-secret"
            class="flex flex-col pt-3 text-left text-sm"
          >
            <span class="pb-1 text-gray-600 dark:text-gray-300">Secret</span>
            <input
              id="login-secret"
              v-model="secret"
              type="password"
              autocomplete="current-password"
              required
              :aria-invalid="failure ? 'true' : undefined"
              :aria-describedby="failure ? 'login-failure' : undefined"
              class="rounded-md bg-gray-50 p-2 dark:bg-gray-800"
            >
          </label>

          <p
            v-if="failure"
            id="login-failure"
            class="pt-3 text-left text-sm text-error"
            role="alert"
          >
            {{ failure }}
          </p>

          <button
            type="submit"
            :disabled="busy"
            class="mt-4 cursor-pointer rounded-md bg-primary py-2 font-medium text-on-primary hover:bg-primary/90 disabled:cursor-not-allowed disabled:bg-primary/60"
          >
            {{ busy ? 'Signing in...' : 'Sign in' }}
          </button>

          <button
            type="button"
            class="pt-3 text-xs text-primary"
            @click="close()"
          >
            Cancel
          </button>
        </form>

        <button
          type="button"
          class="absolute top-0 right-0 mt-5 mr-5"
          aria-label="Close the sign in dialog"
          @click="close()"
        >
          <CloseIcon />
        </button>
      </div>
    </VueFinalModal>
  </div>
</template>
