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
import { computed, ref } from 'vue'
import { errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from './DialogShell.vue'

/*
 * Confirms that a token is revoked for good. The user types the token name, and only an exact
 * match enables the button.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   token       the token to revoke, at least { name }
 *   api         { revoke(name) }: deletes the token and returns the axios promise
 * Emits:
 *   update:modelValue  false when the dialog closes
 *   revoked            the token that was revoked
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  token: {
    type: Object,
    default: null
  },
  api: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'revoked'])

const typed = ref('')
const busy = ref(false)
const failure = ref(null)

const confirmed = computed(() => props.token !== null && typed.value === props.token.name)

const reset = () => {
  typed.value = ''
  busy.value = false
  failure.value = null
}

const revoke = async () => {
  if (!confirmed.value) return
  busy.value = true
  failure.value = null
  try {
    await props.api.revoke(props.token.name)
    emit('revoked', props.token)
    emit('update:modelValue', false)
  } catch (error) {
    failure.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="revoke-token-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="revoke-token-title"
      class="md-headline-small"
    >
      Revoke token
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      Anything that uses <span class="font-medium text-on-surface">{{ token?.name }}</span> stops
      working right away. This cannot be undone.
    </p>

    <form
      id="revoke-token-form"
      class="mt-6 flex flex-col gap-4"
      @submit.prevent="revoke()"
    >
      <MdTextField
        id="revoke-token-confirm"
        v-model="typed"
        label="Type the token name to confirm"
        type="text"
        autocomplete="off"
      />
      <p
        v-if="failure"
        role="alert"
        class="md-body-medium text-error"
      >
        {{ failure }}
      </p>
    </form>

    <div class="mt-6 flex justify-end gap-2">
      <MdButton
        variant="text"
        @click="emit('update:modelValue', false)"
      >
        Cancel
      </MdButton>
      <MdButton
        error
        type="submit"
        form="revoke-token-form"
        :disabled="!confirmed || busy"
      >
        Revoke
      </MdButton>
    </div>
  </DialogShell>
</template>
