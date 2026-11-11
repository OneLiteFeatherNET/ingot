<script setup>
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

// Step 1: the Nexus address and the credential. The connection test is the one call that answers whether the
// credential works; on success the connection is kept in the page and the literal password in the holder.
import { computed, inject, nextTick, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import PasswordField from './PasswordField.vue'
import ProblemAlert from './ProblemAlert.vue'
import { migrationApi } from '../../store/migration/migrationApi'
import { MIGRATION_API, MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'

const api = inject(MIGRATION_API, migrationApi)
const wizard = inject(MIGRATION_WIZARD, useWizard())

const url = ref('')
const user = ref('')
const mode = ref('literal')
const password = ref('')
const reference = ref('')
const errors = reactive({ url: '', user: '', password: '', reference: '' })
const report = ref(null)
const problem = ref(null)
const pending = ref(false)

const connected = computed(() => wizard.state.connection !== null)
const summary = ref(null)
const forgotten = ref(false)
const passwordHeld = computed(() => wizard.passwords.recall() !== null)

// The operator asks for the password to be dropped. The next call that needs it asks for it again.
const forgetPassword = () => {
  wizard.passwords.forget()
  forgotten.value = true
}

// The fields that are invalid, each with its message. The summary links to them and takes the focus on a failed submit.
const errorItems = computed(() => [
  { id: 'migration-connect-url', text: errors.url },
  { id: 'migration-connect-user', text: errors.user },
  { id: 'migration-connect-password', text: errors.password },
  { id: 'migration-connect-reference', text: errors.reference },
].filter(item => item.text !== ''))

const focusField = (id) => {
  document.getElementById(id)?.focus()
}

// The trust failure names the one setting that fixes it. The page has no trust control of its own.
const TRUST_PATTERN = /certificate|trust|pkix|ssl/i
const trustFailure = computed(() => report.value !== null && !report.value.reachable && TRUST_PATTERN.test(report.value.problem ?? ''))
const refusedCredential = computed(() => report.value !== null && report.value.credentialAccepted === false)

const validate = () => {
  errors.url = url.value.trim() ? '' : 'Enter the Nexus URL.'
  errors.user = user.value.trim() ? '' : 'Enter the user name.'
  errors.password = mode.value === 'literal' && !password.value ? 'Enter the password, or use a secret reference.' : ''
  errors.reference = mode.value === 'reference' && !reference.value.trim() ? 'Enter the secret reference.' : ''
  return !errors.url && !errors.user && !errors.password && !errors.reference
}

const submit = async () => {
  if (!validate()) {
    await nextTick()
    summary.value?.focus()
    return
  }
  pending.value = true
  problem.value = null
  report.value = null
  const body = {
    url: url.value.trim(),
    user: user.value.trim(),
    password: mode.value === 'reference' ? reference.value.trim() : password.value,
  }
  try {
    const result = await api.testConnection(body)
    report.value = result
    if (result.reachable && result.credentialAccepted !== false && !result.problem) {
      wizard.setConnection({
        url: body.url,
        user: body.user,
        passwordMode: mode.value,
        reference: mode.value === 'reference' ? body.password : undefined,
        version: result.version,
        edition: result.edition,
        canListRepositories: result.canListRepositories,
        canReadSettings: result.canReadSettings,
      })
      if (mode.value === 'literal') wizard.passwords.remember(body.password)
      password.value = ''
    }
  } catch (error) {
    problem.value = error
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-connect-title"
  >
    <h2
      id="migration-connect-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Connect
    </h2>
    <p class="md-body-large text-on-surface-variant">
      Enter the address of the Nexus Repository 3 instance and a user that may read it. Ingot asks Nexus
      directly; the browser never talks to Nexus.
    </p>
    <form
      id="migration-connect-form"
      class="flex max-w-xl flex-col gap-4"
      novalidate
      @submit.prevent="submit"
    >
      <div
        v-if="errorItems.length"
        id="migration-connect-errors"
        ref="summary"
        tabindex="-1"
        role="alert"
        class="flex flex-col gap-1 rounded-corner-small bg-error-container p-3 text-on-error-container"
      >
        <p class="md-title-small">
          Fix the fields below before the connection can be tested.
        </p>
        <ul class="list-disc pl-6">
          <li
            v-for="item in errorItems"
            :key="item.id"
          >
            <a
              :href="`#${item.id}`"
              class="underline"
              @click.prevent="focusField(item.id)"
            >{{ item.text }}</a>
          </li>
        </ul>
      </div>
      <MdTextField
        id="migration-connect-url"
        label="Nexus URL"
        type="url"
        variant="outlined"
        autocomplete="url"
        required
        :model-value="url"
        :error="errors.url !== ''"
        :error-text="errors.url"
        :aria-invalid="errors.url !== '' ? 'true' : undefined"
        @update:model-value="url = $event"
      />
      <MdTextField
        id="migration-connect-user"
        label="User name"
        variant="outlined"
        autocomplete="username"
        required
        :model-value="user"
        :error="errors.user !== ''"
        :error-text="errors.user"
        @update:model-value="user = $event"
      />
      <fieldset class="flex flex-col gap-2">
        <legend class="md-label-large text-on-surface">
          Password
        </legend>
        <label class="flex items-center gap-2 md-body-large">
          <input
            v-model="mode"
            type="radio"
            name="migration-password-mode"
            value="literal"
          >
          Enter the password
        </label>
        <label class="flex items-center gap-2 md-body-large">
          <input
            v-model="mode"
            type="radio"
            name="migration-password-mode"
            value="reference"
          >
          Use a secret reference
        </label>
      </fieldset>
      <PasswordField
        v-if="mode === 'literal'"
        id="migration-connect-password"
        label="Password"
        :model-value="password"
        :error="errors.password !== ''"
        :error-text="errors.password"
        supporting-text="The password stays in this browser tab while the wizard is open, and is sent only to Ingot."
        @update:model-value="password = $event"
      />
      <MdTextField
        v-else
        id="migration-connect-reference"
        label="Secret reference"
        variant="outlined"
        autocomplete="off"
        :model-value="reference"
        :error="errors.reference !== ''"
        :error-text="errors.reference"
        supporting-text="For example ${env:NEXUS_PASSWORD}. Only the reference is sent, not the value."
        @update:model-value="reference = $event"
      />
      <div>
        <MdButton
          type="submit"
          variant="filled"
          :disabled="pending"
        >
          Test connection
        </MdButton>
      </div>
      <ProblemAlert
        id="migration-connect-problem"
        :problem="problem"
      />
    </form>

    <div
      v-if="report"
      role="status"
      class="flex max-w-xl flex-col gap-2 md-body-large text-on-surface"
    >
      <p v-if="report.reachable && !refusedCredential && !report.problem">
        Connected to Nexus {{ report.version ?? 'version not reported' }}{{ report.edition ? ` (${report.edition})` : '' }}.
        This credential may
        {{ report.canListRepositories ? 'list repositories' : 'not list repositories' }}
        and
        {{ report.canReadSettings ? 'read the repository settings' : 'not read the repository settings' }}.
      </p>
      <p v-if="refusedCredential">
        Nexus refused this credential.
      </p>
      <p v-if="trustFailure">
        Ingot could not verify the certificate of this Nexus. Provide its certificate authority through
        INGOT_EXTRA_CA_CERTS and restart Ingot, then test again.
      </p>
      <p v-else-if="report.problem">
        {{ report.problem }}
      </p>
      <ul
        v-if="report.warnings?.length"
        class="list-disc pl-6"
      >
        <li
          v-for="warning in report.warnings"
          :key="warning"
        >
          {{ warning }}
        </li>
      </ul>
    </div>

    <div
      v-if="passwordHeld || forgotten"
      class="flex max-w-xl flex-col items-start gap-2 md-body-medium text-on-surface-variant"
    >
      <p v-if="forgotten && !passwordHeld">
        The password was forgotten. The steps that need it will ask for it again.
      </p>
      <MdButton
        v-if="passwordHeld"
        id="migration-forget-password"
        type="button"
        variant="outlined"
        @click="forgetPassword"
      >
        Forget the password
      </MdButton>
    </div>

    <p v-if="connected">
      <RouterLink
        class="md-focus-ring md-label-large text-primary"
        :to="{ name: 'settings-migration', params: { step: 'discover' } }"
      >
        Continue to Discover
      </RouterLink>
    </p>
  </section>
</template>
