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
import { computed, onMounted, ref } from 'vue'
import { useSession } from '../../store/session'
import usePlaceholders from '../../store/placeholders'
import { createErrorToast, createSuccessToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdSwitch from '../md/MdSwitch.vue'
import ProviderIcon from '../icons/ProviderIcon.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'
import SsoProviderDialog from './SsoProviderDialog.vue'
import { removeProvider, replaceProvider } from './loginSettings'
import { useLoginSettings } from './useLoginSettings'

/*
 * The single sign on providers of the instance: the list, the switch that offers a provider at
 * sign in, and the dialog that adds, edits, tests and deletes one. Every change is a write of the
 * whole `login` domain with the tag it was read with (useLoginSettings).
 *
 * The status chip shows the last test of a provider in this visit; it is not stored anywhere.
 */
const { client } = useSession()
const {
  settings,
  loaded,
  loadError,
  saving,
  saveError,
  load,
  save
} = useLoginSettings(() => client.value.settings)

const { productionUrl } = usePlaceholders()

const dialogOpen = ref(false)
const confirmOpen = ref(false)
const editingId = ref(null)
const testResults = ref({})
const testing = ref(null)

const providers = computed(() => settings.value.oidcProviders)
const editingProvider = computed(() => providers.value.find(provider => provider.id === editingId.value) ?? null)
const takenIds = computed(() => providers.value.map(provider => provider.id).filter(id => id !== editingId.value))

const STATUS_CLASSES = {
  neutral: 'bg-surface-container-highest text-on-surface-variant',
  success: 'bg-surface-container-highest text-success',
  error: 'bg-error-container text-on-error-container'
}

const statusOf = (provider) => {
  const result = testResults.value[provider.id]
  if (!result) return { tone: 'neutral', text: 'Not tested' }
  return result.ok ? { tone: 'success', text: 'Connected' } : { tone: 'error', text: 'Test failed' }
}

const openAdd = () => {
  editingId.value = null
  dialogOpen.value = true
}

const openEdit = (provider) => {
  editingId.value = provider.id
  dialogOpen.value = true
}

// Writes the list with one provider changed, and reports a refusal in the notice.
const writeProviders = async (next, done) => {
  const ok = await save({ ...settings.value, oidcProviders: next })
  if (ok) {
    done()
  } else {
    createErrorToast(saveError.value)
  }
  return ok
}

const saveProvider = ({ provider, previousId }) =>
  writeProviders(replaceProvider(providers.value, provider, previousId), () => {
    dialogOpen.value = false
    createSuccessToast(`Saved ${provider.label}`)
  })

const setEnabled = (provider, enabled) =>
  writeProviders(replaceProvider(providers.value, { ...provider, enabled }, provider.id), () => {
    createSuccessToast(`${provider.label} ${enabled ? 'is offered' : 'is hidden'} at sign in`)
  })

const deleteProvider = () => {
  const id = editingId.value
  return writeProviders(removeProvider(providers.value, id), () => {
    confirmOpen.value = false
    dialogOpen.value = false
    createSuccessToast('Provider deleted')
  })
}

// The test reads the saved provider at the issuer. A failure is an answer too, not an exception.
const testProvider = async () => {
  const id = editingId.value
  testing.value = id
  let result
  try {
    const response = await client.value.auth.testOidcProvider(id)
    result = { ...response.data, ok: response.data?.ok === true }
  } catch (error) {
    result = { ok: false, error: errorMessage(error) }
  } finally {
    testing.value = null
  }
  testResults.value = { ...testResults.value, [id]: result }
}

onMounted(load)
</script>

<template>
  <section
    aria-labelledby="sso-title"
    class="flex flex-col gap-4"
    data-test="sso"
  >
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex min-w-0 flex-col gap-1">
        <h3
          id="sso-title"
          class="md-title-large text-on-surface"
        >
          Single sign on
        </h3>
        <p class="md-body-medium text-on-surface-variant">
          Users can sign in with an account of these providers. Each one needs a client registered at
          the provider, with the callback URL that its dialog shows.
        </p>
      </div>
      <MdButton
        data-test="add-provider"
        @click="openAdd"
      >
        Add provider
      </MdButton>
    </div>

    <p
      v-if="!loaded"
      role="status"
      class="md-body-medium text-on-surface-variant"
    >
      Loading...
    </p>
    <div
      v-else-if="loadError"
      role="alert"
      class="flex flex-wrap items-center gap-3"
    >
      <span class="md-body-medium text-error">Could not load the providers: {{ loadError }}</span>
      <MdButton
        variant="text"
        @click="load()"
      >
        Retry
      </MdButton>
    </div>
    <ul
      v-else-if="providers.length > 0"
      class="flex list-none flex-col gap-2 p-0"
      data-test="sso-list"
    >
      <li
        v-for="provider in providers"
        :key="provider.id"
        data-test="sso-row"
        class="flex flex-wrap items-center gap-3 rounded-corner-medium border border-outline-variant bg-surface-container-low p-4"
      >
        <span
          class="inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full bg-secondary-container text-on-secondary-container"
        >
          <ProviderIcon :icon="provider.icon" />
        </span>
        <div class="flex min-w-0 flex-1 flex-col">
          <p class="md-title-medium truncate text-on-surface">
            {{ provider.label }}
          </p>
          <p class="md-body-medium break-all text-on-surface-variant">
            {{ provider.issuerUrl || 'No issuer yet' }}
          </p>
        </div>
        <span
          data-test="status"
          class="rounded-corner-small px-2 py-0.5 md-label-medium"
          :class="STATUS_CLASSES[statusOf(provider).tone]"
        >
          {{ statusOf(provider).text }}
        </span>
        <div class="flex items-center gap-2">
          <span class="md-label-medium text-on-surface-variant">Enabled</span>
          <MdSwitch
            :model-value="provider.enabled"
            :aria-label="`Enabled: ${provider.label}`"
            @update:model-value="setEnabled(provider, $event)"
          />
        </div>
        <MdButton
          variant="text"
          :aria-label="`Edit ${provider.label}`"
          @click="openEdit(provider)"
        >
          Edit
        </MdButton>
      </li>
    </ul>
    <p
      v-else
      class="md-body-large text-on-surface-variant"
    >
      No provider yet. Add one to let users sign in with it.
    </p>

    <SsoProviderDialog
      v-model="dialogOpen"
      :provider="editingProvider"
      :taken-ids="takenIds"
      :production-url="productionUrl"
      :test-result="editingId ? testResults[editingId] : null"
      :testing="testing !== null && testing === editingId"
      :busy="saving"
      :failure="saveError"
      @save="saveProvider"
      @test="testProvider"
      @delete="confirmOpen = true"
    />

    <ConfirmActionDialog
      v-model="confirmOpen"
      title="Delete this provider?"
      confirm-label="Delete provider"
      danger
      :type-to-confirm="editingId ?? ''"
      type-label="Type the provider id to confirm"
      :busy="saving"
      :failure="saveError"
      @confirm="deleteProvider"
    >
      Users can no longer sign in with this provider.
    </ConfirmActionDialog>
  </section>
</template>
