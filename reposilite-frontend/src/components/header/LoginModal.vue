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
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import { useSession } from '../../store/session'
import CloseIcon from '../icons/CloseIcon.vue'

const { login } = useSession()
const showLogin = ref(false)
const name = ref('')
const secret = ref('')
const failure = ref(null)
const busy = ref(false)

const close = () => {
  showLogin.value = false
  failure.value = null
}

// The failure belongs in the dialog, not in a toast: the form that has to be corrected is
// right here, and a toast about it appears somewhere else entirely and then leaves.
const signin = (name, secret) => {
  busy.value = true
  failure.value = null

  return login(name, secret)
    .then(() => createSuccessToast(`Dashboard accessed as ${name}`))
    .then(() => close())
    .catch(error => {
      failure.value = errorMessage(error)
    })
    .finally(() => {
      busy.value = false
    })
}
</script>

<script>
export default {
  inheritAttrs: false,
}
</script>

<template>
  <div id="login-modal">
    <VueFinalModal
      v-model="showLogin"
      v-bind="$attrs"
      class="flex items-center justify-center"
    >
      <div class="relative rounded-2xl border border-gray-100 bg-white px-10 py-5 shadow-xl dark:border-black dark:bg-gray-900">
        <p class="pb-4 text-center text-xl font-bold">Login with access token</p>

        <form class="flex w-96 flex-col max-sm:w-65" @submit.prevent="signin(name, secret)">
          <label class="flex flex-col text-left text-sm">
            <span class="pb-1 text-gray-600 dark:text-gray-300">Name</span>
            <input
              v-model="name"
              type="text"
              autocomplete="username"
              class="rounded-md bg-gray-50 p-2 dark:bg-gray-800"
            >
          </label>

          <label class="flex flex-col pt-3 text-left text-sm">
            <span class="pb-1 text-gray-600 dark:text-gray-300">Secret</span>
            <input
              v-model="secret"
              type="password"
              autocomplete="current-password"
              class="rounded-md bg-gray-50 p-2 dark:bg-gray-800"
            >
          </label>

          <p
            v-if="failure"
            class="pt-3 text-left text-sm text-red-600 dark:text-red-400"
            role="alert"
          >
            {{ failure }}
          </p>

          <button
            type="submit"
            :disabled="busy"
            class="mt-4 cursor-pointer rounded-md bg-blue-700 py-2 font-medium text-white hover:bg-blue-800 disabled:cursor-not-allowed disabled:bg-blue-700/60"
          >
            {{ busy ? 'Signing in...' : 'Sign in' }}
          </button>

          <button
            type="button"
            class="pt-3 text-xs text-blue-600 dark:text-blue-300"
            @click="close()"
          >
            Back to index
          </button>
        </form>

        <button
          type="button"
          class="absolute top-0 right-0 mt-5 mr-5"
          aria-label="Close the login dialog"
          @click="close()"
        >
          <CloseIcon />
        </button>
      </div>
    </VueFinalModal>
    <div @click="showLogin = true">
      <slot name="button"></slot>
    </div>
  </div>
</template>
