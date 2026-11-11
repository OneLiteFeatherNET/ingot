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
import { onMounted, ref } from 'vue'
import { useSession } from '../../store/session'

/*
 * Warns on the System page while secret values of the shared settings are stored in clear. It names the count and the
 * key setting, never a value. The state comes from the settings area, so a principal without that permission gets a
 * 403 and sees nothing here.
 */
const { client } = useSession()
const state = ref(null)

onMounted(async () => {
  try {
    const { data } = await client.value.settings.encryptionState()
    state.value = data
  } catch {
    state.value = null
  }
})
</script>

<template>
  <div
    v-if="state && !state.secretsEncrypted && state.plaintextSecrets > 0"
    role="alert"
    class="rounded-corner-large bg-error-container p-4 text-on-error-container"
  >
    <p class="md-title-small">
      {{ state.plaintextSecrets }} stored secret {{ state.plaintextSecrets === 1 ? 'value is' : 'values are' }} kept in clear.
    </p>
    <p class="md-body-medium">
      Set INGOT_SETTINGS_KEY or INGOT_SETTINGS_KEY_FILE to seal them. The settings encryption reference explains the steps.
    </p>
  </div>
</template>
