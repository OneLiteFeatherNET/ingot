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
import MdCheckbox from '../md/MdCheckbox.vue'
import MdTextField from '../md/MdTextField.vue'
import SegmentedControl from '../tokens/SegmentedControl.vue'
import SegmentedOption from '../tokens/SegmentedOption.vue'
import DialogShell from './DialogShell.vue'
import {
  DEFAULT_EXPIRY,
  EXPIRY_PRESETS,
  SCOPE_OPTIONS,
  buildCreateBody,
  validateTokenName
} from './identityTokens'

/*
 * The dialog that creates a token.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   api         { create(body) }: sends the request and returns the axios response, whose
 *               data is { token, secret }. The self service page and the admin pages pass
 *               their own endpoints.
 * Emits:
 *   update:modelValue  false when the dialog closes
 *   created            { token, secret } once the server has accepted the token
 *
 * The expiry preset becomes a date at the moment of sending, counted from the injected clock.
 * No checked scope means no narrowing (scopes null). A failure stays inside the dialog.
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
const description = ref('')
const preset = ref(DEFAULT_EXPIRY)
const scopes = ref([])
const submitted = ref(false)
const busy = ref(false)
const failure = ref(null)

const nameError = computed(() => (submitted.value ? validateTokenName(name.value) : ''))

const reset = () => {
  name.value = ''
  description.value = ''
  preset.value = DEFAULT_EXPIRY
  scopes.value = []
  submitted.value = false
  busy.value = false
  failure.value = null
}

const close = () => {
  emit('update:modelValue', false)
}

const toggleScope = (action, checked) => {
  scopes.value = checked
    ? SCOPE_OPTIONS.map(option => option.action).filter(candidate => candidate === action || scopes.value.includes(candidate))
    : scopes.value.filter(candidate => candidate !== action)
}

const submit = async () => {
  submitted.value = true
  failure.value = null
  if (validateTokenName(name.value) !== '') {
    return
  }
  busy.value = true
  try {
    const response = await props.api.create(buildCreateBody({
      name: name.value,
      description: description.value,
      preset: preset.value,
      scopes: scopes.value
    }))
    emit('created', response.data)
    close()
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
    labelledby="create-token-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="create-token-title"
      class="md-headline-small"
    >
      Create token
    </h2>

    <form
      id="create-token-form"
      class="mt-6 flex flex-col gap-4"
      novalidate
      @submit.prevent="submit()"
    >
      <MdTextField
        id="create-token-name"
        v-model="name"
        label="Name"
        type="text"
        autocomplete="off"
        required
        :error="nameError !== ''"
        :error-text="nameError"
      />
      <MdTextField
        id="create-token-description"
        v-model="description"
        label="Description (optional)"
        type="text"
        autocomplete="off"
      />

      <div class="flex flex-col gap-2">
        <span class="md-label-large text-on-surface-variant">Expires</span>
        <SegmentedControl label="Expiry">
          <SegmentedOption
            v-for="option in EXPIRY_PRESETS"
            :key="option.key"
            :on="preset === option.key"
            @click="preset = option.key"
          >
            {{ option.label }}
          </SegmentedOption>
        </SegmentedControl>
      </div>

      <fieldset class="flex flex-col gap-1 border-0 p-0">
        <legend class="md-label-large text-on-surface-variant">
          Limit to
        </legend>
        <div class="flex flex-wrap gap-x-6">
          <MdCheckbox
            v-for="option in SCOPE_OPTIONS"
            :id="`create-token-scope-${option.action.toLowerCase()}`"
            :key="option.action"
            :model-value="scopes.includes(option.action)"
            @update:model-value="toggleScope(option.action, $event)"
          >
            {{ option.label }}
          </MdCheckbox>
        </div>
        <p class="md-body-small text-on-surface-variant">
          Check nothing to give the token all rights of your teams.
        </p>
      </fieldset>

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
        @click="close()"
      >
        Cancel
      </MdButton>
      <MdButton
        type="submit"
        form="create-token-form"
        :disabled="busy"
      >
        {{ busy ? 'Creating...' : 'Create' }}
      </MdButton>
    </div>
  </DialogShell>
</template>
