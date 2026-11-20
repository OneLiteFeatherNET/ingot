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
import { computed, ref, watch, type PropType } from 'vue'
import type { AxiosResponse } from 'axios'
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import MdAlert from '../md/MdAlert.vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import ConfirmActionDialog from './ConfirmActionDialog.vue'

/*
 * The second factor summary of one user, for an instance administrator: how many authenticator
 * apps and passkeys the user has, and the action that removes all of them with the recovery codes
 * and ends the user's sessions. The answers carry names and times, never secrets.
 *
 * Props:
 *   name  the user whose factors are shown
 *   api   { list, resetAll }, the factor calls of one principal (client.identity.principals.factors)
 * The reset asks first. The confirmation names the user and says that the sessions end.
 */
interface SecondFactor {
  id?: number
  kind?: string
  name?: string
}

interface FactorsApi {
  list: () => Promise<AxiosResponse<SecondFactor[]>>
  resetAll: () => Promise<AxiosResponse<{ removed?: number }>>
}

const props = defineProps({
  name: {
    type: String,
    required: true
  },
  api: {
    type: Object as PropType<FactorsApi>,
    required: true
  }
})

// The kinds the server stores for a second factor. Any other kind is shown as the server names it.
const KIND_LABELS: Record<string, string> = {
  TOTP: 'Authenticator apps',
  PASSKEY: 'Passkeys'
}

const status = ref<'loading' | 'ready' | 'error'>('loading')
const failure = ref('')
const factors = ref<SecondFactor[]>([])
const confirmOpen = ref(false)
const busy = ref(false)
const resetFailure = ref('')

let latest = 0

const load = async () => {
  const request = ++latest
  status.value = 'loading'
  failure.value = ''
  try {
    const response = await props.api.list()
    if (request !== latest) return
    factors.value = response.data ?? []
    status.value = 'ready'
  } catch (error) {
    if (request !== latest) return
    failure.value = errorMessage(error)
    status.value = 'error'
  }
}

watch(() => props.name, () => {
  factors.value = []
  load()
}, { immediate: true })

const counts = computed(() => {
  const tally = new Map<string, number>()
  for (const factor of factors.value) {
    const kind = factor.kind ?? 'UNKNOWN'
    tally.set(kind, (tally.get(kind) ?? 0) + 1)
  }
  return [...tally].map(([kind, count]) => ({ kind, count, label: KIND_LABELS[kind] ?? kind }))
})

const reset = async () => {
  busy.value = true
  resetFailure.value = ''
  try {
    await props.api.resetAll()
    createSuccessToast(`Second factors of ${props.name} reset`)
    confirmOpen.value = false
    await load()
  } catch (error) {
    resetFailure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <MdCard class="min-w-0">
    <section
      class="flex min-w-0 flex-col gap-4"
      aria-labelledby="principal-factors-title"
      data-ingot-component="principal-factors-card"
    >
      <div class="flex flex-col gap-1">
        <h2
          id="principal-factors-title"
          class="m-0 md-title-medium text-on-surface"
        >
          Second factors
        </h2>
        <p class="m-0 md-body-medium text-on-surface-variant">
          Authenticator apps and passkeys of this user. Only the kinds and the counts are shown.
        </p>
      </div>

      <p
        v-if="status === 'loading'"
        role="status"
        class="m-0 md-body-medium text-on-surface-variant"
      >
        Loading...
      </p>
      <MdAlert
        v-else-if="status === 'error'"
        tone="error"
      >
        {{ failure }}
      </MdAlert>
      <template v-else>
        <p
          v-if="counts.length === 0"
          data-test="no-factors"
          class="m-0 md-body-medium text-on-surface-variant"
        >
          No second factors
        </p>
        <ul
          v-else
          class="m-0 flex list-none flex-col gap-2 p-0"
        >
          <li
            v-for="row in counts"
            :key="row.kind"
            data-test="factor-count"
            class="md-body-medium text-on-surface"
          >
            {{ row.label }}: {{ row.count }}
          </li>
        </ul>
      </template>

      <div class="flex justify-end">
        <MdButton
          variant="outlined"
          error
          data-test="reset-factors"
          :disabled="status !== 'ready'"
          @click="confirmOpen = true"
        >
          Reset second factors
        </MdButton>
      </div>

      <ConfirmActionDialog
        v-model="confirmOpen"
        title="Reset second factors"
        confirm-label="Reset second factors"
        danger
        :busy="busy"
        :failure="resetFailure"
        @confirm="reset()"
      >
        All authenticator apps, passkeys and recovery codes of {{ name }} are removed. All sessions of {{ name }} end, and
        {{ name }} has to sign in again.
      </ConfirmActionDialog>
    </section>
  </MdCard>
</template>
