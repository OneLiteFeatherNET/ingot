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

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useSession } from '../../store/session'

/*
 * The account throttle switch is read from the environment when the instance starts, so the page shows it and never
 * edits it. The instance status reports the switch: its sign-in counters are present exactly while the switch is on.
 * When the status cannot be read, the value is shown as not available instead of guessed.
 */
const session = useSession()
const state = ref<'loading' | 'on' | 'off' | 'unavailable'>('loading')

onMounted(async () => {
  try {
    const response = await session.client.value.status.instance()
    state.value = response.data?.signIn ? 'on' : 'off'
  } catch {
    state.value = 'unavailable'
  }
})

const labels = {
  loading: 'Loading...',
  on: 'On',
  off: 'Off',
  unavailable: 'Not available',
} as const
</script>

<template>
  <section
    class="flex flex-col gap-2 rounded-corner-large bg-surface-container-low p-4"
    data-test="account-throttle-switch"
    data-ingot-component="sign-in-switch-notice"
  >
    <h4 class="md-title-small text-on-surface">
      Account throttle switch
    </h4>
    <p class="md-body-medium text-on-surface-variant">
      Set in the environment with <code>INGOT_LOGIN_ACCOUNT_THROTTLE</code>, or with its fallback
      <code>REPOSILITE_LOGIN_ACCOUNT_THROTTLE</code>, and read when the instance starts. It cannot be changed here.
      The limits below have no effect while it is off.
    </p>
    <p class="md-body-medium text-on-surface">
      Current value: {{ labels[state] }}
    </p>
  </section>
</template>
