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
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCheckbox from '../md/MdCheckbox.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'
import { buildCreateBody, validatePrincipalName } from './accessPrincipals'

/*
 * Creates a machine account. Its tokens are created on its own page afterwards, and its rights
 * come from the teams it joins.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   api         { create(body) }
 * Emits update:modelValue and created (the name of the new machine account).
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  api: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'created'])

const name = ref('')
const displayName = ref('')
const disabled = ref(false)
const submitted = ref(false)
const busy = ref(false)
const failure = ref('')

const nameProblem = computed(() => validatePrincipalName(name.value))

const reset = () => {
  name.value = ''
  displayName.value = ''
  disabled.value = false
  submitted.value = false
  busy.value = false
  failure.value = ''
}

const submit = async () => {
  submitted.value = true
  failure.value = ''
  if (nameProblem.value) return
  busy.value = true
  const machine = name.value.trim()
  try {
    await props.api.create(buildCreateBody({
      kind: 'MACHINE',
      name: machine,
      displayName: displayName.value,
      disabled: disabled.value
    }))
    createSuccessToast(`Machine account ${machine} created`)
    emit('created', machine)
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
    labelledby="new-machine-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="new-machine-title"
      class="md-headline-small"
    >
      New machine account
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      A machine account signs in builds and scripts with tokens. Add it to a team to give it rights.
    </p>

    <form
      id="new-machine-form"
      class="mt-6 flex flex-col gap-4"
      novalidate
      @submit.prevent="submit()"
    >
      <MdTextField
        id="new-machine-name"
        v-model="name"
        label="Name"
        type="text"
        autocomplete="off"
        required
        :error="submitted && nameProblem !== ''"
        :error-text="nameProblem"
      />
      <MdTextField
        id="new-machine-display-name"
        v-model="displayName"
        label="Display name"
        type="text"
        autocomplete="off"
        supporting-text="Optional"
      />
      <MdCheckbox
        id="new-machine-disabled"
        v-model="disabled"
      >
        Create it disabled
      </MdCheckbox>
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
        type="submit"
        form="new-machine-form"
        :disabled="busy"
      >
        Create machine account
      </MdButton>
    </div>
  </DialogShell>
</template>
