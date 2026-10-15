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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { useSession } from '../../store/session'
import { loginRequests } from '../../store/login'
import { now } from '../../helpers/clock'
import { rateLimitMessage, rateLimitedUntil } from '../../helpers/rate-limit'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'

/*
 * One dialog, three faces. First run (the server says setupRequired) it is "Set up Ingot".
 * Otherwise it lists the sign in providers of the server, with a password form for the local
 * one, and a link to the older access token form. A password never leaves these refs: they are
 * cleared whenever the dialog closes, and nothing is written to a storage.
 */
const {
  signInWithPassword,
  signInWithToken,
  completeSetup,
  loadProviders,
  providers,
  setupRequired,
  loginFailureMessage,
  passwordFailureMessage,
  setupFailureMessage
} = useSession()

const MIN_PASSWORD_LENGTH = 12

const showLogin = ref(false)

// Other views ask for the dialog through openLogin(); see store/login.js.
watch(loginRequests, () => {
  showLogin.value = true
})

const view = ref('password')
const busy = ref(false)
const failure = ref(null)
const providersFailed = ref(false)
const root = ref(null)

const username = ref('')
const password = ref('')
const name = ref('')
const secret = ref('')

const setupCode = ref('')
const setupUsername = ref('')
const setupDisplayName = ref('')
const setupPassword = ref('')
const setupConfirm = ref('')
const submitted = ref(false)

const localProvider = computed(() => providers.value.find(provider => provider.type === 'LOCAL'))
const otherProviders = computed(() => providers.value.filter(provider => provider.type !== 'LOCAL'))

const mode = computed(() => (setupRequired.value ? 'setup' : view.value))
const title = computed(() => ({
  setup: 'Set up Ingot',
  password: 'Sign in',
  token: 'Sign in with access token'
}[mode.value]))
const formId = computed(() => `login-form-${mode.value}`)
const hasForm = computed(() => mode.value !== 'password' || localProvider.value !== undefined)

const passwordError = computed(() =>
  submitted.value && setupPassword.value.length < MIN_PASSWORD_LENGTH)
const confirmError = computed(() =>
  submitted.value && setupConfirm.value !== setupPassword.value)

// After too many failed attempts the server answers 429 with the seconds to wait. The deadline
// is kept as an instant, so the rest of the wait is always computed from the clock when the
// dialog renders. A single timer renders it again when the shown minute changes or the wait ends;
// it is cleared with the dialog. The wait outlives closing the dialog: it is the server's limit.
const lockedUntil = ref(null)
const tick = ref(0)
let timer = null

const remainingSeconds = () => {
  tick.value // a dependency, so that the timer can render this again
  if (lockedUntil.value === null) return 0
  return Math.max(0, Math.ceil((lockedUntil.value - now().getTime()) / 1000))
}
const isLocked = () => remainingSeconds() > 0
const lockMessage = () => (isLocked() ? rateLimitMessage(remainingSeconds()) : '')

const scheduleTick = () => {
  clearTimeout(timer)
  timer = null
  if (lockedUntil.value === null) return
  const left = lockedUntil.value - now().getTime()
  if (left <= 0) return
  timer = setTimeout(() => {
    tick.value++
    scheduleTick()
  }, (left % 60000 || 60000) + 50)
}

const lockFrom = (error) => {
  lockedUntil.value = rateLimitedUntil(error, now().getTime())
  scheduleTick()
}

onBeforeUnmount(() => clearTimeout(timer))

// Every failed attempt sets or clears the deadline (lockFrom), so a deadline that has passed means
// that the message in `failure` is the old rate limit text, which no longer applies.
const limitExpired = () => lockedUntil.value !== null && !isLocked()
const fieldError = () => isLocked() || (failure.value !== null && !limitExpired())
const fieldErrorText = () => lockMessage() || (limitExpired() ? '' : failure.value ?? '')

const forgetInput = () => {
  username.value = ''
  password.value = ''
  name.value = ''
  secret.value = ''
  setupCode.value = ''
  setupUsername.value = ''
  setupDisplayName.value = ''
  setupPassword.value = ''
  setupConfirm.value = ''
  submitted.value = false
  failure.value = null
  view.value = 'password'
}

const close = () => {
  showLogin.value = false
  forgetInput()
}

// The first field is only in the document once the dialog has opened, so focus waits for that.
const focusFirst = () => root.value?.querySelector('input')?.focus()
const focusField = (id) => nextTick().then(() => document.getElementById(id)?.focus())

watch(showLogin, open => {
  if (!open) return
  providersFailed.value = false
  Promise.resolve(loadProviders()).catch(() => {
    providersFailed.value = true
  })
})

const switchView = (next) => {
  view.value = next
  failure.value = null
  nextTick().then(focusFirst)
}

// The failure belongs in the dialog, not in a toast: the form that has to be corrected is
// right here, and a toast about it appears somewhere else entirely and then leaves. Focus
// moves to the field that carries the message, so that it is read out.
const attempt = (action, failureMessage, fieldId) => {
  busy.value = true
  failure.value = null
  return action()
    .then(() => close())
    .catch(error => {
      failure.value = failureMessage(error)
      return focusField(fieldId)
    })
    .finally(() => {
      busy.value = false
    })
}

const signinWithPassword = () => {
  if (isLocked()) return Promise.resolve()
  return attempt(
    () => signInWithPassword({ username: username.value, password: password.value }),
    (error) => {
      lockFrom(error)
      return passwordFailureMessage(error)
    },
    'login-password'
  )
}

const signinWithToken = () =>
  attempt(() => signInWithToken(name.value, secret.value), loginFailureMessage, 'login-secret')

const setup = () => {
  submitted.value = true
  if (passwordError.value || confirmError.value) {
    return Promise.resolve()
  }
  const request = {
    setupCode: setupCode.value,
    username: setupUsername.value,
    password: setupPassword.value
  }
  if (setupDisplayName.value) {
    request.displayName = setupDisplayName.value
  }
  return attempt(() => completeSetup(request), setupFailureMessage, 'setup-code')
}

const submit = () => {
  if (mode.value === 'setup') return setup()
  if (mode.value === 'token') return signinWithToken()
  return signinWithPassword()
}

const submitLabel = computed(() => {
  if (mode.value === 'setup') return busy.value ? 'Setting up...' : 'Set up'
  return busy.value ? 'Signing in...' : 'Sign in'
})

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
      @opened="focusFirst"
      @closed="forgetInput"
    >
      <!-- The 24px side inset is set on the modal's content wrapper; the dialog caps at 560px. -->
      <div
        ref="root"
        class="mx-auto max-h-[90dvh] w-full min-w-70 max-w-140 overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      >
        <h2
          id="login-title"
          class="md-headline-small"
        >
          {{ title }}
        </h2>

        <form
          v-if="mode === 'setup'"
          :id="formId"
          class="mt-6 flex flex-col gap-4"
          @submit.prevent="submit()"
        >
          <MdTextField
            id="setup-code"
            v-model="setupCode"
            label="Setup code"
            type="text"
            autocomplete="off"
            supporting-text="Find it in the server log"
            required
          />
          <MdTextField
            id="setup-username"
            v-model="setupUsername"
            label="Admin username"
            type="text"
            autocomplete="username"
            required
          />
          <MdTextField
            id="setup-display-name"
            v-model="setupDisplayName"
            label="Display name (optional)"
            type="text"
            autocomplete="name"
          />
          <MdTextField
            id="setup-password"
            v-model="setupPassword"
            label="Password"
            type="password"
            autocomplete="new-password"
            supporting-text="At least 12 characters"
            :error="passwordError"
            error-text="Use at least 12 characters"
            required
          />
          <MdTextField
            id="setup-confirm"
            v-model="setupConfirm"
            label="Confirm password"
            type="password"
            autocomplete="new-password"
            :error="confirmError"
            error-text="Passwords do not match"
            required
          />
          <p
            v-if="failure"
            role="alert"
            class="md-body-medium text-error"
          >
            {{ failure }}
          </p>
        </form>

        <form
          v-else-if="mode === 'token'"
          :id="formId"
          class="mt-6 flex flex-col gap-4"
          @submit.prevent="submit()"
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
        </form>

        <template v-else>
          <form
            v-if="localProvider"
            :id="formId"
            class="mt-6 flex flex-col gap-4"
            @submit.prevent="submit()"
          >
            <MdTextField
              id="login-username"
              v-model="username"
              label="Username"
              type="text"
              autocomplete="username"
              required
              :error="failure !== null"
            />
            <MdTextField
              id="login-password"
              v-model="password"
              label="Password"
              type="password"
              autocomplete="current-password"
              required
              :error="fieldError()"
              :error-text="fieldErrorText()"
            />
          </form>

          <ul
            v-if="otherProviders.length > 0"
            class="mt-4 flex list-none flex-col gap-2 p-0"
          >
            <li
              v-for="provider in otherProviders"
              :key="provider.id"
              class="flex"
            >
              <MdButton
                variant="outlined"
                class="w-full"
                :href="provider.loginUrl"
                :disabled="!provider.loginUrl"
              >
                {{ provider.label }}
              </MdButton>
            </li>
          </ul>

          <p
            v-if="providersFailed"
            role="alert"
            class="mt-4 md-body-medium text-error"
          >
            Sign in options could not be loaded
          </p>
        </template>

        <div class="mt-6 flex flex-wrap items-center justify-between gap-2">
          <MdButton
            v-if="mode === 'password'"
            variant="text"
            @click="switchView('token')"
          >
            Use an access token instead
          </MdButton>
          <MdButton
            v-else-if="mode === 'token'"
            variant="text"
            @click="switchView('password')"
          >
            Use a password instead
          </MdButton>
          <span v-else />

          <div class="flex justify-end gap-2">
            <MdButton
              variant="text"
              @click="close()"
            >
              Cancel
            </MdButton>
            <MdButton
              v-if="hasForm"
              type="submit"
              :form="formId"
              :disabled="busy || (mode === 'password' && isLocked())"
            >
              {{ submitLabel }}
            </MdButton>
          </div>
        </div>
      </div>
    </VueFinalModal>
  </div>
</template>
