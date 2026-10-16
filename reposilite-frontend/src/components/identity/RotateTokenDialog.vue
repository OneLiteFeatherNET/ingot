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
import { ref } from 'vue'
import { errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import DialogShell from './DialogShell.vue'

/*
 * Confirms that a token gets a new secret.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   token       the token to rotate, at least { name }
 *   api         { rotate(name) }: returns the axios response, whose data is { token, secret }
 * Emits:
 *   update:modelValue  false when the dialog closes
 *   rotated            { token, secret } once the server has issued the new secret
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

const emit = defineEmits(['update:modelValue', 'rotated'])

const busy = ref(false)
const failure = ref(null)

const reset = () => {
  busy.value = false
  failure.value = null
}

const rotate = async () => {
  busy.value = true
  failure.value = null
  try {
    const response = await props.api.rotate(props.token.name)
    emit('rotated', response.data)
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
    labelledby="rotate-token-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="rotate-token-title"
      class="md-headline-small"
    >
      Rotate secret
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      <span class="font-medium text-on-surface">{{ token?.name }}</span> gets a new secret.
      The old secret stops working right away, so update everything that uses it.
    </p>
    <p
      v-if="failure"
      role="alert"
      class="mt-4 md-body-medium text-error"
    >
      {{ failure }}
    </p>

    <div class="mt-6 flex justify-end gap-2">
      <MdButton
        variant="text"
        @click="emit('update:modelValue', false)"
      >
        Cancel
      </MdButton>
      <MdButton
        :disabled="busy"
        @click="rotate()"
      >
        Rotate secret
      </MdButton>
    </div>
  </DialogShell>
</template>
