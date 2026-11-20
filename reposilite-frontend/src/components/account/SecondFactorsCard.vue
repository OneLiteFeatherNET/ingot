/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createErrorToast, createSuccessToast, errorMessage } from '../../helpers/toast'
import { createPasskey, isWebAuthnSupported } from '../../store/webauthn'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdCheckbox from '../md/MdCheckbox.vue'
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'
import MdTextField from '../md/MdTextField.vue'
import type { SecondFactor, SecondFactorsApi } from './secondFactorsApi'

/*
 * The second factors of the signed in user, inside Sign-in and security.
 *
 * Props:
 *   api          the endpoints of client.identity.me (factors); see secondFactorsApi.ts
 *   accountName  the account name the passkey belongs to, shown to the browser at registration
 *
 * The list shows each factor with its name and kind. A factor is renamed or removed in place; removal takes a current
 * code, because the server asks for one. The authenticator app is enrolled in a dialog: the seed is shown there once,
 * as text, and the dialog forgets it when it closes. The enrolment is confirmed with a code before the server stores
 * it. The recovery codes come next, in their own dialog that stays open until the user confirms they are stored, and
 * the codes are forgotten when it closes. A passkey is registered with the browser's ceremony and is named after it.
 *
 * Nothing is written to a storage, to the address or to a log. The seed and the codes live in refs of this component only.
 */
const props = withDefaults(defineProps<{
  api: SecondFactorsApi
  accountName?: string
}>(), {
  accountName: ''
})

const kindNames: Record<string, string> = {
  AUTHENTICATOR: 'Authenticator app',
  PASSKEY: 'Passkey'
}
const kindName = (kind: string) => kindNames[kind] ?? kind

const passkeysSupported = isWebAuthnSupported()

const factors = ref<SecondFactor[]>([])
const loadFailure = ref<string | null>(null)
const loaded = ref(false)

const loadFactors = async () => {
  try {
    factors.value = (await props.api.factors.list()).data
    loadFailure.value = null
  } catch (error) {
    loadFailure.value = errorMessage(error)
  } finally {
    loaded.value = true
  }
}
onMounted(loadFactors)

// Rename or remove of one row. Only one row is edited at a time.
const editing = ref<{ id: number; mode: 'rename' | 'remove' } | null>(null)
const editName = ref('')
const editCode = ref('')
const editBusy = ref(false)
const editFailure = ref<string | null>(null)

const startEdit = (factor: SecondFactor, mode: 'rename' | 'remove') => {
  editing.value = { id: factor.id, mode }
  editName.value = factor.name
  editCode.value = ''
  editFailure.value = null
}

const stopEdit = () => {
  editing.value = null
  editName.value = ''
  editCode.value = ''
  editFailure.value = null
}

const saveName = async (factor: SecondFactor) => {
  const name = editName.value.trim()
  if (name === '') return
  editBusy.value = true
  editFailure.value = null
  try {
    const updated = (await props.api.factors.rename(factor.id, name)).data
    factors.value = factors.value.map(item => (item.id === factor.id ? { ...item, ...updated } : item))
    stopEdit()
    createSuccessToast('Name saved')
  } catch (error) {
    editFailure.value = errorMessage(error)
  } finally {
    editBusy.value = false
  }
}

const removeFactor = async (factor: SecondFactor) => {
  if (editCode.value === '') return
  editBusy.value = true
  editFailure.value = null
  try {
    await props.api.factors.remove(factor.id, editCode.value)
    factors.value = factors.value.filter(item => item.id !== factor.id)
    stopEdit()
    createSuccessToast('Second factor removed')
  } catch (error) {
    editFailure.value = errorMessage(error)
  } finally {
    editBusy.value = false
  }
}

// The authenticator enrolment. The seed exists only in seed and only while the dialog is open.
const enrolOpen = ref(false)
const seed = ref('')
const enrolCode = ref('')
const enrolBusy = ref(false)
const enrolFailure = ref<string | null>(null)

const openEnrolment = () => {
  enrolOpen.value = true
  seed.value = ''
  enrolCode.value = ''
  enrolFailure.value = null
}

const closeEnrolment = () => {
  enrolOpen.value = false
  seed.value = ''
  enrolCode.value = ''
  enrolFailure.value = null
}

const startAuthenticator = async () => {
  enrolBusy.value = true
  enrolFailure.value = null
  try {
    seed.value = (await props.api.factors.startAuthenticator()).data.otpauthUri
  } catch (error) {
    enrolFailure.value = errorMessage(error)
  } finally {
    enrolBusy.value = false
  }
}

// The recovery codes of a confirmed factor. They are shown once, by their own dialog.
const recoveryCodes = ref<string[] | null>(null)
const codesStored = ref(false)

const confirmAuthenticator = async () => {
  if (enrolCode.value === '') return
  enrolBusy.value = true
  enrolFailure.value = null
  try {
    const confirmed = (await props.api.factors.confirmAuthenticator(enrolCode.value)).data
    closeEnrolment()
    recoveryCodes.value = confirmed.recoveryCodes ?? []
    codesStored.value = false
    await loadFactors()
  } catch (error) {
    enrolFailure.value = errorMessage(error)
  } finally {
    enrolBusy.value = false
  }
}

const closeRecoveryCodes = () => {
  recoveryCodes.value = null
  codesStored.value = false
}

const copyCodes = async () => {
  try {
    await navigator.clipboard.writeText((recoveryCodes.value ?? []).join('\n'))
    createSuccessToast('Recovery codes copied')
  } catch {
    createErrorToast('Could not copy the codes. Select them and copy them by hand.')
  }
}

const downloadCodes = () => {
  const blob = new Blob([`${(recoveryCodes.value ?? []).join('\n')}\n`], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'ingot-recovery-codes.txt'
  link.click()
  URL.revokeObjectURL(url)
}

// The passkey registration: a name, then the browser's ceremony, then the server stores it under that name.
const passkeyOpen = ref(false)
const passkeyName = ref('')
const passkeyBusy = ref(false)
const passkeyFailure = ref<string | null>(null)

const openPasskey = () => {
  passkeyOpen.value = true
  passkeyName.value = ''
  passkeyFailure.value = null
}

const closePasskey = () => {
  passkeyOpen.value = false
  passkeyName.value = ''
  passkeyFailure.value = null
}

const createPasskeyFactor = async () => {
  const name = passkeyName.value.trim()
  if (name === '') return
  passkeyBusy.value = true
  passkeyFailure.value = null
  try {
    const options = (await props.api.factors.startPasskey()).data
    const attestation = await createPasskey({
      challenge: options.challenge,
      rpId: options.rpId,
      userName: props.accountName,
      userHandle: options.userHandle ?? ''
    })
    const registered = (await props.api.factors.registerPasskey({ ...attestation, challengeId: options.challengeId })).data
    await props.api.factors.rename(registered.id, name)
    closePasskey()
    await loadFactors()
  } catch (error) {
    passkeyFailure.value = errorMessage(error)
  } finally {
    passkeyBusy.value = false
  }
}

const emptyList = computed(() => loaded.value && loadFailure.value === null && factors.value.length === 0)
</script>

<template>
  <section
    class="flex flex-col"
    aria-labelledby="second-factors-title"
    data-ingot-component="second-factors-card"
  >
    <MdCard
      variant="low"
      class="flex flex-col gap-4 border border-outline"
    >
      <h3
        id="second-factors-title"
        class="md-title-large text-on-surface"
      >
        Second factors
      </h3>

      <p
        v-if="loadFailure"
        role="alert"
        class="md-body-medium text-error"
      >
        {{ loadFailure }}
      </p>
      <p
        v-else-if="emptyList"
        class="md-body-medium text-on-surface-variant"
      >
        No second factor is set up yet.
      </p>

      <ul
        v-else
        class="flex flex-col gap-3"
      >
        <li
          v-for="factor in factors"
          :key="factor.id"
          class="flex flex-col gap-2 rounded-corner-small border border-outline-variant px-4 py-3"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex min-w-0 flex-col">
              <span class="md-body-large text-on-surface">{{ factor.name }}</span>
              <span class="md-body-medium text-on-surface-variant">{{ kindName(factor.kind) }}</span>
            </div>
            <div class="flex gap-2">
              <MdButton
                variant="text"
                @click="startEdit(factor, 'rename')"
              >
                Rename
              </MdButton>
              <MdButton
                variant="text"
                @click="startEdit(factor, 'remove')"
              >
                Remove
              </MdButton>
            </div>
          </div>

          <form
            v-if="editing?.id === factor.id && editing.mode === 'rename'"
            class="flex flex-wrap items-end gap-3"
            novalidate
            @submit.prevent="saveName(factor)"
          >
            <MdTextField
              id="second-factor-name"
              v-model="editName"
              label="Name"
              required
            />
            <MdButton
              type="submit"
              :disabled="editBusy"
            >
              Save name
            </MdButton>
            <MdButton
              variant="text"
              @click="stopEdit()"
            >
              Cancel
            </MdButton>
            <p
              v-if="editFailure"
              role="alert"
              class="w-full md-body-medium text-error"
            >
              {{ editFailure }}
            </p>
          </form>

          <form
            v-if="editing?.id === factor.id && editing.mode === 'remove'"
            class="flex flex-wrap items-end gap-3"
            novalidate
            @submit.prevent="removeFactor(factor)"
          >
            <MdTextField
              id="second-factor-remove-code"
              v-model="editCode"
              label="Current code"
              autocomplete="one-time-code"
              required
            />
            <MdButton
              type="submit"
              :disabled="editBusy || editCode === ''"
            >
              Remove factor
            </MdButton>
            <MdButton
              variant="text"
              @click="stopEdit()"
            >
              Cancel
            </MdButton>
            <p
              v-if="editFailure"
              role="alert"
              class="w-full md-body-medium text-error"
            >
              {{ editFailure }}
            </p>
          </form>
        </li>
      </ul>

      <p
        v-if="!passkeysSupported"
        class="md-body-medium text-on-surface-variant"
      >
        Passkeys are not supported in this browser.
      </p>

      <div class="flex flex-wrap justify-end gap-2">
        <MdButton
          variant="tonal"
          @click="openEnrolment()"
        >
          Add an authenticator app
        </MdButton>
        <MdButton
          v-if="passkeysSupported"
          variant="tonal"
          @click="openPasskey()"
        >
          Add a passkey
        </MdButton>
      </div>
    </MdCard>

    <MdDialog
      v-if="enrolOpen"
      :model-value="enrolOpen"
      labelledby="second-factor-enrol-title"
      :dismissable="false"
      @update:model-value="closeEnrolment()"
    >
      <template #header>
        <h2
          id="second-factor-enrol-title"
          class="md-headline-small"
        >
          Add an authenticator app
        </h2>
      </template>

      <div
        v-if="seed === ''"
        class="mt-2 flex flex-col gap-3"
      >
        <p class="md-body-large text-on-surface-variant">
          Start the enrolment to get a key for your authenticator app. The key is shown once, here.
        </p>
      </div>

      <div
        v-else
        class="mt-2 flex flex-col gap-3"
      >
        <p class="md-body-large text-on-surface-variant">
          Add this address to your authenticator app, then enter the six-digit code it shows.
        </p>
        <pre class="overflow-x-auto rounded-corner-small border border-outline-variant bg-surface-container-highest px-3 py-3 font-mono md-body-medium break-all whitespace-pre-wrap text-on-surface">{{ seed }}</pre>
        <MdTextField
          id="second-factor-enrol-code"
          v-model="enrolCode"
          label="Code from the app"
          autocomplete="one-time-code"
          required
        />
      </div>

      <p
        v-if="enrolFailure"
        role="alert"
        class="mt-3 md-body-medium text-error"
      >
        {{ enrolFailure }}
      </p>

      <template #actions>
        <MdDialogActions>
          <MdButton
            variant="text"
            @click="closeEnrolment()"
          >
            Cancel
          </MdButton>
          <MdButton
            v-if="seed === ''"
            :disabled="enrolBusy"
            @click="startAuthenticator()"
          >
            Start
          </MdButton>
          <MdButton
            v-else
            :disabled="enrolBusy || enrolCode === ''"
            @click="confirmAuthenticator()"
          >
            Confirm
          </MdButton>
        </MdDialogActions>
      </template>
    </MdDialog>

    <MdDialog
      v-if="recoveryCodes !== null"
      :model-value="recoveryCodes !== null"
      labelledby="second-factor-codes-title"
      :dismissable="false"
      @update:model-value="closeRecoveryCodes()"
    >
      <template #header>
        <h2
          id="second-factor-codes-title"
          class="md-headline-small"
        >
          Your recovery codes
        </h2>
      </template>

      <p class="mt-2 md-body-large text-on-surface-variant">
        Each code signs you in once when the app is not available. They are shown only now.
      </p>
      <pre class="mt-4 overflow-x-auto rounded-corner-small border border-outline-variant bg-surface-container-highest px-3 py-3 font-mono md-body-medium text-on-surface">{{ recoveryCodes.join('\n') }}</pre>

      <div class="mt-4 flex flex-wrap gap-2">
        <MdButton
          variant="tonal"
          @click="copyCodes()"
        >
          Copy codes
        </MdButton>
        <MdButton
          variant="tonal"
          @click="downloadCodes()"
        >
          Download codes
        </MdButton>
      </div>

      <label class="mt-4 flex items-center gap-2 md-body-large text-on-surface">
        <MdCheckbox
          id="second-factor-recovery-check"
          v-model="codesStored"
        />
        I have stored these codes
      </label>

      <template #actions>
        <MdDialogActions>
          <MdButton
            :disabled="!codesStored"
            @click="closeRecoveryCodes()"
          >
            Done
          </MdButton>
        </MdDialogActions>
      </template>
    </MdDialog>

    <MdDialog
      v-if="passkeyOpen"
      :model-value="passkeyOpen"
      labelledby="second-factor-passkey-title"
      :dismissable="false"
      @update:model-value="closePasskey()"
    >
      <template #header>
        <h2
          id="second-factor-passkey-title"
          class="md-headline-small"
        >
          Add a passkey
        </h2>
      </template>

      <div class="mt-2 flex flex-col gap-3">
        <p class="md-body-large text-on-surface-variant">
          Name the passkey, then confirm it with this device or your security key.
        </p>
        <MdTextField
          id="second-factor-passkey-name"
          v-model="passkeyName"
          label="Passkey name"
          required
        />
      </div>

      <MdAlert
        v-if="passkeyFailure"
        tone="error"
        class="mt-3"
      >
        {{ passkeyFailure }}
      </MdAlert>

      <template #actions>
        <MdDialogActions>
          <MdButton
            variant="text"
            @click="closePasskey()"
          >
            Cancel
          </MdButton>
          <MdButton
            :disabled="passkeyBusy || passkeyName.trim() === ''"
            @click="createPasskeyFactor()"
          >
            Create passkey
          </MdButton>
        </MdDialogActions>
      </template>
    </MdDialog>
  </section>
</template>
