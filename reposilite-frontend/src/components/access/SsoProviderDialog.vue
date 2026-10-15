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
import { computed, nextTick, ref, watch } from 'vue'
import DialogShell from '../identity/DialogShell.vue'
import MdButton from '../md/MdButton.vue'
import MdChipInput from '../md/MdChipInput.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdSwitch from '../md/MdSwitch.vue'
import MdTextField from '../md/MdTextField.vue'
import ProviderIcon from '../icons/ProviderIcon.vue'
import CopyIcon from '../icons/CopyIcon.vue'
import { createErrorToast, createSuccessToast } from '../../helpers/toast'
import {
  PROVIDER_TYPES,
  callbackUrlFor,
  emptyProvider,
  providerTypeOf,
  validateProvider
} from './loginSettings'

/*
 * Adds or edits one single sign on provider. A new provider starts with the kind picker, which
 * prefills its name, mark and scopes. The callback URL to register at the provider is shown with a
 * copy button, and the registration steps of the kind sit above it.
 *
 * Props:
 *   modelValue     whether the dialog is open (v-model)
 *   provider       the saved provider being edited, or null for a new one
 *   takenIds       the ids of the other providers
 *   productionUrl  the origin and base path the dashboard is served under
 *   testResult     the last test of this provider: { ok, issuer, endpoints } or { ok: false, error }
 *   testing        true while the test runs
 *   busy           true while the save runs
 *   failure        the message of a refused save
 * Emits update:modelValue, save ({ provider, previousId }), delete and test.
 * The test runs against the saved provider only, so it is offered when the form matches it.
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  provider: { type: Object, default: null },
  takenIds: { type: Array, default: () => [] },
  productionUrl: { type: String, required: true },
  testResult: { type: Object, default: null },
  testing: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  failure: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'save', 'delete', 'test'])

const clone = (value) => JSON.parse(JSON.stringify(value))

const form = ref(null)
const submitted = ref(false)
const root = ref(null)

const isEditing = computed(() => props.provider !== null)
const kind = computed(() => (form.value ? providerTypeOf(form.value.icon) : null))
const problems = computed(() => (form.value ? validateProvider(form.value, { takenIds: props.takenIds }) : {}))
const errorOf = (field) => (submitted.value ? problems.value[field] ?? '' : '')

// A test runs on the saved provider, so it needs a saved one and a form that still matches it.
const testable = computed(() =>
  isEditing.value && form.value !== null && JSON.stringify(form.value) === JSON.stringify(props.provider))

const ID_PATTERN = /^[a-z][a-z0-9-]{1,39}$/
const callbackUrl = computed(() => (form.value && ID_PATTERN.test(form.value.id)
  ? callbackUrlFor(form.value.id, props.productionUrl)
  : ''))

const endpointLines = computed(() => {
  const endpoints = props.testResult?.endpoints
  if (Array.isArray(endpoints)) return endpoints.map(String)
  if (endpoints && typeof endpoints === 'object') return Object.entries(endpoints).map(([name, value]) => `${name}: ${value}`)
  return []
})

const reset = () => {
  submitted.value = false
  form.value = isEditing.value ? clone(props.provider) : null
}

// A new provider opens on the kind picker; a saved one opens on its form.
watch(() => props.modelValue, (open) => {
  if (open) reset()
}, { immediate: true })

const chooseKind = (type) => {
  form.value = emptyProvider(type)
}

const copyCallback = () => {
  const write = navigator.clipboard?.writeText(callbackUrl.value)
  Promise.resolve(write)
    .then(() => createSuccessToast('Callback URL copied'))
    .catch(() => createErrorToast('The callback URL could not be copied, select it and copy it by hand'))
}

const save = () => {
  submitted.value = true
  if (Object.keys(problems.value).length > 0) {
    nextTick(() => root.value?.querySelector('[aria-invalid="true"]')?.focus())
    return
  }
  emit('save', { provider: clone(form.value), previousId: props.provider?.id })
}

const validateScope = (text, existing) => {
  if (/\s/.test(text)) return 'A scope has no spaces'
  return existing.includes(text) ? 'Already added' : ''
}

const validateTextItem = (text, existing) => (existing.includes(text) ? 'Already added' : '')

const validateDomain = (text, existing) => {
  if (existing.includes(text)) return 'Already added'
  return /^[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(text) ? '' : 'Enter a domain such as example.org'
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="sso-dialog-title"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div ref="root">
      <div class="flex items-center gap-3">
        <span
          v-if="kind"
          class="inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full bg-secondary-container text-on-secondary-container"
        >
          <ProviderIcon :icon="kind.icon" />
        </span>
        <h2
          id="sso-dialog-title"
          class="md-headline-small"
        >
          {{ isEditing ? `Edit ${provider.label}` : (form ? 'Add a provider' : 'Add an SSO provider') }}
        </h2>
      </div>

      <!-- The kind picker: one card per kind of provider. -->
      <section
        v-if="!form"
        class="mt-6 flex flex-col gap-4"
        aria-labelledby="sso-kind-title"
      >
        <h3
          id="sso-kind-title"
          class="md-title-medium text-on-surface"
        >
          Which provider is it?
        </h3>
        <p class="md-body-medium text-on-surface-variant">
          The kind fills in the name, the mark and the scopes. You add the client details next.
        </p>
        <ul class="grid list-none grid-cols-2 gap-2 p-0">
          <li
            v-for="type in PROVIDER_TYPES"
            :key="type.id"
          >
            <button
              type="button"
              class="md-state-layer md-focus-ring md-touch-target relative flex w-full items-center gap-3 rounded-corner-medium border border-outline-variant bg-surface-container-low p-3 text-left text-on-surface"
              @click="chooseKind(type)"
            >
              <ProviderIcon :icon="type.icon" />
              <span class="md-label-large">{{ type.label }}</span>
            </button>
          </li>
        </ul>
      </section>

      <form
        v-else
        id="sso-provider-form"
        class="mt-6 flex flex-col gap-6"
        novalidate
        @submit.prevent="save()"
      >
        <!-- The callback the provider has to accept, and how to register it there. -->
        <section
          class="flex flex-col gap-3 rounded-corner-medium bg-surface-container p-4"
          aria-labelledby="sso-register-title"
        >
          <h3
            id="sso-register-title"
            class="md-title-small text-on-surface"
          >
            Register Ingot at the provider
          </h3>
          <p class="md-body-medium text-on-surface-variant">
            {{ kind.register }}
          </p>
          <div
            role="group"
            aria-label="Callback URL"
            class="flex items-center gap-2"
          >
            <code
              data-test="callback-url"
              class="min-w-0 flex-1 break-all md-body-medium text-on-surface"
            >{{ callbackUrl || 'Enter an id to see the callback URL' }}</code>
            <MdIconButton
              v-if="callbackUrl"
              variant="standard"
              aria-label="Copy callback URL"
              @click="copyCallback"
            >
              <CopyIcon />
            </MdIconButton>
          </div>
        </section>

        <div class="grid gap-4 sm:grid-cols-2">
          <MdTextField
            id="sso-label"
            v-model="form.label"
            label="Label on the sign in button"
            type="text"
            :error="errorOf('label') !== ''"
            :error-text="errorOf('label')"
            required
          />
          <MdTextField
            id="sso-id"
            v-model="form.id"
            label="Id"
            type="text"
            autocomplete="off"
            :disabled="isEditing"
            :supporting-text="isEditing ? 'The id is part of the callback URL and of team links, so it cannot change' : 'Lowercase letters, digits and dashes, for example keycloak'"
            :error="errorOf('id') !== ''"
            :error-text="errorOf('id')"
            required
          />
        </div>

        <MdTextField
          id="sso-issuer"
          v-model="form.issuerUrl"
          label="Issuer URL"
          type="url"
          autocomplete="off"
          :placeholder="kind.issuerHint"
          :supporting-text="kind.issuerNote ?? (kind.issuerHint ? `For example ${kind.issuerHint}` : '')"
          :error="errorOf('issuerUrl') !== ''"
          :error-text="errorOf('issuerUrl')"
          required
        />

        <div class="grid gap-4 sm:grid-cols-2">
          <MdTextField
            id="sso-client-id"
            v-model="form.clientId"
            label="Client id"
            type="text"
            autocomplete="off"
            :error="errorOf('clientId') !== ''"
            :error-text="errorOf('clientId')"
            required
          />
          <MdTextField
            id="sso-client-secret"
            v-model="form.clientSecret"
            label="Client secret"
            type="password"
            autocomplete="off"
            supporting-text="A stored secret shows as ********. Type a new value to replace it."
            :error="errorOf('clientSecret') !== ''"
            :error-text="errorOf('clientSecret')"
            required
          />
        </div>

        <MdChipInput
          id="sso-scopes"
          v-model="form.scopes"
          label="Scopes"
          supporting-text="Add each scope as a chip. openid is required."
          :validate="validateScope"
          :error="errorOf('scopes') !== ''"
          :error-text="errorOf('scopes')"
        />

        <MdChipInput
          id="sso-domains"
          v-model="form.allowedEmailDomains"
          label="Allowed email domains"
          supporting-text="Only these domains can sign in. Leave empty to accept any."
          :validate="validateDomain"
          :error="errorOf('allowedEmailDomains') !== ''"
          :error-text="errorOf('allowedEmailDomains')"
        />

        <!-- Group mapping: which groups of the provider make an instance admin, and what the sync reads. -->
        <section
          class="flex flex-col gap-3"
          aria-labelledby="sso-groups-title"
        >
          <h3
            id="sso-groups-title"
            class="md-title-small text-on-surface"
          >
            Groups
          </h3>
          <p class="md-body-medium text-on-surface-variant">
            Members of an admin group become instance admins at sign in. A team whose external
            reference is <code>oidc:{{ form.id || 'id' }}:&lt;group&gt;</code> is synced from the groups
            claim: its members follow the group, and they cannot be removed by hand.
          </p>
          <MdChipInput
            id="sso-admin-groups"
            v-model="form.adminGroups"
            label="Admin groups"
            supporting-text="Group names as the provider sends them"
            :validate="validateTextItem"
          />
        </section>

        <MdSwitch
          id="sso-enabled"
          v-model="form.enabled"
        >
          Enabled: offer this provider at sign in
        </MdSwitch>
        <MdSwitch
          id="sso-create-users"
          v-model="form.autoCreateUsers"
        >
          Create an account at the first sign in
        </MdSwitch>

        <!-- The claims are rarely changed, so they wait behind a disclosure. -->
        <details class="rounded-corner-medium border border-outline-variant p-4">
          <summary class="cursor-pointer md-title-small text-on-surface">
            Advanced: claims
          </summary>
          <div class="mt-4 grid gap-4 sm:grid-cols-2">
            <MdTextField
              id="sso-claim-username"
              v-model="form.usernameClaim"
              label="Username claim"
              type="text"
              autocomplete="off"
            />
            <MdTextField
              id="sso-claim-name"
              v-model="form.displayNameClaim"
              label="Display name claim"
              type="text"
              autocomplete="off"
            />
            <MdTextField
              id="sso-claim-email"
              v-model="form.emailClaim"
              label="Email claim"
              type="text"
              autocomplete="off"
            />
            <MdTextField
              id="sso-claim-groups"
              v-model="form.groupsClaim"
              label="Groups claim"
              type="text"
              autocomplete="off"
            />
          </div>
        </details>

        <!-- The test reads the saved provider, so it waits for the save. -->
        <section
          class="flex flex-col gap-3 border-t border-outline-variant pt-4"
          aria-labelledby="sso-test-title"
        >
          <h3
            id="sso-test-title"
            class="md-title-small text-on-surface"
          >
            Test connection
          </h3>
          <p class="md-body-medium text-on-surface-variant">
            {{ testable
              ? 'Checks the saved settings of this provider with the issuer.'
              : (isEditing ? 'Save your changes first, then test the saved settings.' : 'Save the provider first, then test it.') }}
          </p>
          <div class="flex flex-wrap items-center gap-3">
            <MdButton
              variant="outlined"
              :disabled="!testable || testing"
              @click="$emit('test')"
            >
              {{ testing ? 'Testing...' : 'Test connection' }}
            </MdButton>
          </div>
          <div
            v-if="testResult"
            role="status"
            data-test="test-result"
            class="md-body-medium"
            :class="testResult.ok ? 'text-success' : 'text-error'"
          >
            <p v-if="testResult.ok">
              Connected. Issuer {{ testResult.issuer }}
            </p>
            <p v-else>
              The test failed: {{ testResult.error }}
            </p>
            <ul
              v-if="testResult.ok && endpointLines.length"
              class="mt-2 list-none p-0 text-on-surface-variant"
            >
              <li
                v-for="line in endpointLines"
                :key="line"
              >
                {{ line }}
              </li>
            </ul>
          </div>
        </section>

        <p
          v-if="failure"
          role="alert"
          class="md-body-medium text-error"
        >
          {{ failure }}
        </p>

        <div class="flex flex-wrap items-center justify-between gap-2">
          <MdButton
            v-if="isEditing"
            variant="text"
            error
            @click="$emit('delete')"
          >
            Delete provider
          </MdButton>
          <span v-else />
          <div class="flex gap-2">
            <MdButton
              variant="text"
              @click="$emit('update:modelValue', false)"
            >
              Cancel
            </MdButton>
            <MdButton
              type="submit"
              form="sso-provider-form"
              :disabled="busy"
            >
              {{ busy ? 'Saving...' : 'Save' }}
            </MdButton>
          </div>
        </div>
      </form>

      <div
        v-if="!form"
        class="mt-6 flex justify-end"
      >
        <MdButton
          variant="text"
          @click="$emit('update:modelValue', false)"
        >
          Cancel
        </MdButton>
      </div>
    </div>
  </DialogShell>
</template>
