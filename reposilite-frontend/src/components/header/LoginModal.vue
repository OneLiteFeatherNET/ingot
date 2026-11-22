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

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { AxiosError } from 'axios'
import { secondFactorFailureMessage, useSession, type LoginProvider } from '../../store/session'
import { createURL, type SecondFactorChallenge } from '../../store/client'
import { isWebAuthnSupported } from '../../store/webauthn'
import { loginRequests } from '../../store/login'
import { now } from '../../helpers/clock'
import { rateLimitMessage, rateLimitedUntil } from '../../helpers/rate-limit'
import MdButton from '../md/MdButton.vue'
import MdDialog from '../md/MdDialog.vue'
import MdTextField from '../md/MdTextField.vue'
import MdTransition from '../md/MdTransition.vue'
import SegmentedControl from '../tokens/SegmentedControl.vue'
import SegmentedOption from '../tokens/SegmentedOption.vue'
import ProviderIcon from '../icons/ProviderIcon.vue'
import { providerLoginHref } from './providerLinks'

/*
 * One dialog, three faces. First run (the server says setupRequired) it is "Set up Ingot".
 * Otherwise it offers the password providers (the Ingot account and an LDAP directory, one form
 * with a switch when both exist), then an "or" divider and one button per single sign on provider,
 * and a link to the older access token form. A provider button is a link that starts the provider's
 * login with the current page as the return address. A password never leaves these refs: they are
 * cleared whenever the dialog closes, and nothing is written to a storage.
 *
 * A password sign in that the server answers with mfaRequired opens a second step in the same dialog.
 * The step offers the methods the server listed: an authenticator code, a recovery code, and a passkey
 * when the browser supports one. No session exists until the second step succeeds.
 *
 * The steps swap through the shared axis Y pattern, the old one leaving before the next enters. A field is focused
 * once the step it belongs to has entered, because during the leave the only fields in the document are the old ones.
 */
const {
  signInWithPassword,
  signInWithToken,
  completeSetup,
  completeSecondFactor,
  completePasskeySecondFactor,
  loadProviders,
  providers,
  setupRequired,
  loginFailureMessage,
  passwordFailureMessage,
  setupFailureMessage,
  passwordMinLength
} = useSession()


const showLogin = ref(false)

// Other views ask for the dialog through openLogin(); see store/login.js.
watch(loginRequests, () => {
  showLogin.value = true
})

const view = ref<'password' | 'token' | 'second'>('password')
const busy = ref(false)
const failure = ref<string | null>(null)
const providersFailed = ref(false)
const root = ref<HTMLDivElement | null>(null)

// The second step. The challenge is what the password answer listed; the fields are the two ways to type a second factor.
const challenge = ref<SecondFactorChallenge | null>(null)
const secondCode = ref('')
const recoveryCode = ref('')
const useRecovery = ref(false)
const titleRef = ref<HTMLHeadingElement | null>(null)
const offersCode = computed(() => challenge.value?.methods.includes('AUTHENTICATOR') ?? false)
const offersRecovery = computed(() => challenge.value?.methods.includes('RECOVERY_CODE') ?? false)
const offersPasskey = computed(() => (challenge.value?.methods.includes('PASSKEY') ?? false) && isWebAuthnSupported())
// The recovery field shows when the visitor picks it, or when the server lists no authenticator code.
const showRecovery = computed(() => useRecovery.value || (!offersCode.value && offersRecovery.value))
const hasCodeField = computed(() => offersCode.value || offersRecovery.value)
const stepHint = computed(() => (offersCode.value
  ? 'Enter the code from your authenticator app.'
  : 'Enter one of your recovery codes. Each code works once.'))

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

const route = useRoute()

// The providers that take a username and a password: the Ingot account and the LDAP directory.
const localProvider = computed(() => providers.value.find(provider => provider.type === 'LOCAL'))
const passwordProviders = computed(() =>
  providers.value.filter(provider => provider.type === 'LOCAL' || provider.type === 'LDAP'))
// The single sign on providers. Each one is a link to its own login, never a form.
const ssoProviders = computed(() => providers.value.filter(provider => provider.type === 'OIDC'))
const hasPasswordForm = computed(() => passwordProviders.value.length > 0)

// The provider the form signs in with. The switch changes it; the Ingot account is the default.
const selectedProviderId = ref<string | null | undefined>(null)
const activeProvider = computed<LoginProvider>(() =>
  passwordProviders.value.find(provider => provider.id === selectedProviderId.value)
    ?? localProvider.value
    ?? passwordProviders.value[0])
const providerTitle = (provider: LoginProvider): string | undefined => (provider.type === 'LOCAL' ? 'Ingot account' : provider.label)
const selectProvider = (id: string | undefined): void => {
  selectedProviderId.value = id
  failure.value = null
}

// Where a provider sends the visitor back: the page they are on, with its query.
const ssoHref = (provider: LoginProvider): string | undefined => (provider.loginUrl
  ? providerLoginHref(provider.loginUrl, route?.fullPath ?? '/', createURL)
  : undefined)

const mode = computed<'setup' | 'password' | 'token' | 'second'>(() => (setupRequired.value ? 'setup' : view.value))
const title = computed<string>(() => ({
  setup: 'Set up Ingot',
  password: 'Sign in',
  token: 'Sign in with access token',
  second: 'Two-step verification'
}[mode.value]))
const formId = computed(() => `login-form-${mode.value}`)

const minimumHint = computed(() => `At least ${passwordMinLength.value} characters`)
const minimumError = computed(() => `Use at least ${passwordMinLength.value} characters`)
const passwordError = computed(() =>
  submitted.value && setupPassword.value.length < passwordMinLength.value)
const confirmError = computed(() =>
  submitted.value && setupConfirm.value !== setupPassword.value)

// After too many failed attempts the server answers 429 with the seconds to wait. The deadline
// is kept as an instant, so the rest of the wait is always computed from the clock when the
// dialog renders. A single timer renders it again when the shown minute changes or the wait ends;
// it is cleared with the dialog. The wait outlives closing the dialog: it is the server's limit.
const lockedUntil = ref<number | null>(null)
const tick = ref(0)
let timer: ReturnType<typeof setTimeout> | undefined

const remainingSeconds = (): number => {
  tick.value // a dependency, so that the timer can render this again
  if (lockedUntil.value === null) return 0
  return Math.max(0, Math.ceil((lockedUntil.value - now().getTime()) / 1000))
}
const isLocked = () => remainingSeconds() > 0
const lockMessage = (): string => (isLocked() ? rateLimitMessage(remainingSeconds()) : '')

const scheduleTick = () => {
  clearTimeout(timer)
  timer = undefined
  if (lockedUntil.value === null) return
  const left = lockedUntil.value - now().getTime()
  if (left <= 0) return
  timer = setTimeout(() => {
    tick.value++
    scheduleTick()
  }, (left % 60000 || 60000) + 50)
}

const lockFrom = (error: unknown) => {
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
  selectedProviderId.value = null
  challenge.value = null
  secondCode.value = ''
  recoveryCode.value = ''
  useRecovery.value = false
}

const close = () => {
  showLogin.value = false
  forgetInput()
}

// The first field is only in the document once the dialog has opened, so focus waits for that. While a step is leaving
// the only fields are the old ones, so the focus waits for the next step to enter instead.
const stepLeaving = ref(false)
let focusWhenEntered = false
const focusFirst = () => {
  if (stepLeaving.value) {
    focusWhenEntered = true
    return
  }
  root.value?.querySelector('input')?.focus()
}
const stepLeft = () => {
  stepLeaving.value = false
}
const stepEntered = () => {
  stepLeaving.value = false
  if (!focusWhenEntered) return
  focusWhenEntered = false
  focusOnOpen()
}
// The opened event, like the end of a step enter, comes after the transition. A field the visitor has already clicked
// into keeps the focus, because moving it then would send what they type to the first field.
const focusOnOpen = () => {
  if (root.value?.contains(document.activeElement)) return
  focusFirst()
}
const focusField = (id: string): Promise<void> => nextTick().then(() => document.getElementById(id)?.focus())

watch(showLogin, open => {
  if (!open) return
  providersFailed.value = false
  Promise.resolve(loadProviders()).catch(() => {
    providersFailed.value = true
  })
})

const switchView = (next: 'password' | 'token') => {
  view.value = next
  failure.value = null
  nextTick().then(focusFirst)
}

// The failure belongs in the dialog, not in a toast: the form that has to be corrected is
// right here, and a toast about it appears somewhere else entirely and then leaves. Focus
// moves to the field that carries the message, so that it is read out.
// The message for a rejected request. The session store reads the failure from the HTTP answer.
type FailureMessage = typeof setupFailureMessage

const attempt = (
  action: () => Promise<unknown>,
  failureMessage: FailureMessage,
  fieldId: string
): Promise<void> => {
  busy.value = true
  failure.value = null
  return action()
    .then(() => close())
    .catch((error: AxiosError<{ message?: string }>) => {
      failure.value = failureMessage(error)
      return focusField(fieldId)
    })
    .finally(() => {
      busy.value = false
    })
}

// The first step. A user with a second factor gets the second step in place of a closed dialog.
const signinWithPassword = (): Promise<void> => {
  if (isLocked()) return Promise.resolve()
  const provider = activeProvider.value.id
  busy.value = true
  failure.value = null
  return signInWithPassword({ provider, username: username.value, password: password.value })
    .then(next => {
      if (next) {
        return enterSecondStep(next)
      }
      close()
      return undefined
    })
    .catch((error: AxiosError<{ message?: string }>) => {
      lockFrom(error)
      failure.value = passwordFailureMessage(error)
      return focusField('login-password')
    })
    .finally(() => {
      busy.value = false
    })
}

// The password is no longer needed once the server has checked it, so it is dropped as the step opens.
const enterSecondStep = (next: SecondFactorChallenge): Promise<void> => {
  challenge.value = next
  view.value = 'second'
  useRecovery.value = false
  secondCode.value = ''
  recoveryCode.value = ''
  password.value = ''
  return focusStepHeading()
}

// Focus goes to the step heading, so that the step and any message in it are read out from the top.
const focusStepHeading = (): Promise<void> => nextTick().then(() => titleRef.value?.focus())

// Both ways into the second step end the same way: success closes the dialog; a failure is announced in the step,
// the typed codes are cleared, and a rate limit locks the step for the server's Retry-After.
const finishSecondStep = (action: Promise<void>): Promise<void> => {
  busy.value = true
  failure.value = null
  return action
    .then(() => close())
    .catch((error: unknown) => {
      lockFrom(error)
      failure.value = secondFactorFailureMessage(error)
      secondCode.value = ''
      recoveryCode.value = ''
      return focusStepHeading()
    })
    .finally(() => {
      busy.value = false
    })
}

const verifySecondStep = (): Promise<void> => {
  if (isLocked()) return Promise.resolve()
  const value = (showRecovery.value ? recoveryCode.value : secondCode.value).trim()
  return finishSecondStep(completeSecondFactor(value))
}

const verifyPasskey = (): Promise<void> => {
  if (isLocked()) return Promise.resolve()
  return finishSecondStep(completePasskeySecondFactor())
}

// Shows the other field of the step: the authenticator code or the recovery code. The focus moves to the field it shows.
const toggleRecovery = (): void => {
  useRecovery.value = !useRecovery.value
  failure.value = null
  focusField(showRecovery.value ? 'login-recovery-code' : 'login-code')
}

// Cancel on the second step returns to the password form. The username stays, and the password was dropped on entry.
const backToPassword = (): void => {
  view.value = 'password'
  challenge.value = null
  secondCode.value = ''
  recoveryCode.value = ''
  useRecovery.value = false
  failure.value = null
  nextTick().then(focusFirst)
}

const cancel = (): void => {
  if (view.value === 'second') {
    backToPassword()
    return
  }
  close()
}

const signinWithToken = (): Promise<void> =>
  attempt(() => signInWithToken(name.value, secret.value), loginFailureMessage, 'login-secret')

const setup = (): Promise<void> => {
  submitted.value = true
  if (passwordError.value || confirmError.value) {
    return Promise.resolve()
  }
  const request: { setupCode: string; username: string; password: string; displayName?: string } = {
    setupCode: setupCode.value,
    username: setupUsername.value,
    password: setupPassword.value
  }
  if (setupDisplayName.value) {
    request.displayName = setupDisplayName.value
  }
  // The session store declares displayName as required, but the dialog leaves it out when it is empty.
  return attempt(() => completeSetup(request as Parameters<typeof completeSetup>[0]), setupFailureMessage, 'setup-code')
}

const submit = (): Promise<void> => {
  if (mode.value === 'setup') return setup()
  if (mode.value === 'token') return signinWithToken()
  if (mode.value === 'second') return verifySecondStep()
  return signinWithPassword()
}

const submitLabel = computed(() => {
  if (mode.value === 'setup') return busy.value ? 'Setting up...' : 'Set up'
  if (mode.value === 'second') return busy.value ? 'Verifying...' : 'Verify'
  return busy.value ? 'Signing in...' : 'Sign in'
})
</script>

<template>
  <div data-ingot-component="login-modal">
    <MdButton
      variant="tonal"
      @click="showLogin = true"
    >
      Sign in
    </MdButton>
    <MdDialog
      v-model="showLogin"
      labelledby="login-title"
      @opened="focusOnOpen"
      @closed="forgetInput"
    >
      <template #header>
        <h2
          id="login-title"
          ref="titleRef"
          tabindex="-1"
          class="md-headline-small"
        >
          {{ title }}
        </h2>
      </template>
      <div ref="root">
        <MdTransition
          pattern="shared-y"
          @before-leave="stepLeaving = true"
          @after-leave="stepLeft"
          @after-enter="stepEntered"
        >
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
              :supporting-text="minimumHint"
              :error="passwordError"
              :error-text="minimumError"
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

          <div v-else-if="mode === 'second'">
            <p class="mt-4 md-body-medium text-on-surface-variant">
              {{ stepHint }}
            </p>

            <form
              v-if="hasCodeField"
              :id="formId"
              class="mt-6 flex flex-col gap-4"
              @submit.prevent="submit()"
            >
              <MdTextField
                v-if="!showRecovery"
                id="login-code"
                v-model="secondCode"
                label="Authenticator code"
                type="text"
                inputmode="numeric"
                autocomplete="one-time-code"
                required
                :error="failure !== null"
              />
              <MdTextField
                v-else
                id="login-recovery-code"
                v-model="recoveryCode"
                label="Recovery code"
                type="text"
                autocomplete="off"
                required
                :error="failure !== null"
              />
            </form>

            <p
              v-if="failure"
              role="alert"
              class="mt-4 md-body-medium text-error"
            >
              {{ failure }}
            </p>

            <div class="mt-6 flex flex-col gap-2">
              <MdButton
                v-if="offersCode && offersRecovery"
                variant="text"
                class="self-start"
                @click="toggleRecovery()"
              >
                {{ showRecovery ? 'Use an authenticator code' : 'Use a recovery code' }}
              </MdButton>
              <MdButton
                v-if="offersPasskey"
                variant="outlined"
                class="w-full"
                :disabled="busy || isLocked()"
                @click="verifyPasskey()"
              >
                Use a passkey
              </MdButton>
            </div>
          </div>

          <div v-else>
            <SegmentedControl
              v-if="passwordProviders.length > 1"
              label="Sign in method"
              class="mt-6 w-full"
            >
              <SegmentedOption
                v-for="provider in passwordProviders"
                :key="provider.id"
                :on="provider.id === activeProvider.id"
                class="flex-1 justify-center"
                @click="selectProvider(provider.id)"
              >
                {{ providerTitle(provider) }}
              </SegmentedOption>
            </SegmentedControl>

            <form
              v-if="hasPasswordForm"
              :id="formId"
              class="mt-4 flex flex-col gap-4"
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
              <MdButton
                type="submit"
                class="w-full"
                :disabled="busy || isLocked()"
              >
                {{ submitLabel }}
              </MdButton>
            </form>

            <div
              v-if="hasPasswordForm && ssoProviders.length > 0"
              data-test="divider"
              aria-hidden="true"
              class="mt-6 flex items-center gap-3"
            >
              <span class="h-px flex-1 bg-outline-variant" />
              <span class="md-label-medium text-on-surface-variant">or</span>
              <span class="h-px flex-1 bg-outline-variant" />
            </div>

            <ul
              v-if="ssoProviders.length > 0"
              class="mt-6 flex list-none flex-col gap-2 p-0"
            >
              <li
                v-for="provider in ssoProviders"
                :key="provider.id"
                class="flex"
              >
                <MdButton
                  variant="outlined"
                  class="w-full"
                  :href="ssoHref(provider)"
                  :disabled="!provider.loginUrl"
                >
                  <template #icon>
                    <ProviderIcon
                      :icon="provider.icon ?? undefined"
                    />
                  </template>
                  Continue with {{ provider.label }}
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
          </div>
        </MdTransition>

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
              @click="cancel()"
            >
              Cancel
            </MdButton>
            <MdButton
              v-if="mode !== 'password' && (mode !== 'second' || hasCodeField)"
              type="submit"
              :form="formId"
              :disabled="busy || (mode === 'second' && isLocked())"
            >
              {{ submitLabel }}
            </MdButton>
          </div>
        </div>
      </div>
    </MdDialog>
  </div>
</template>
