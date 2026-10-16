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
import MdRadio from '../md/MdRadio.vue'
import MdTextField from '../md/MdTextField.vue'
import SegmentedControl from '../tokens/SegmentedControl.vue'
import SegmentedOption from '../tokens/SegmentedOption.vue'
import DialogShell from './DialogShell.vue'
import {
  ACCESS_OPTIONS,
  DEFAULT_ACCESS,
  DEFAULT_EXPIRY,
  EXPIRY_PRESETS,
  buildCreateBody,
  customDateLimits,
  expiresAtFor,
  formatExpiryDate,
  scopesFor,
  validateCustomDate,
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
 *   locale      the locale for the expiry date, such as en-GB. Undefined uses the browser's.
 * Emits:
 *   update:modelValue  false when the dialog closes
 *   created            { token, secret } once the server has accepted the token
 *
 * The expiry preset becomes a date at the moment of sending, counted from the injected clock.
 * Create stays disabled until the name is valid and, for a custom date, the date is too.
 * Access maps to scopes: "Same as my teams" sends no narrowing (scopes null), "Read only"
 * sends READ. A failure stays inside the dialog.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  api: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    default: undefined
  }
})

const emit = defineEmits(['update:modelValue', 'created'])

const NEVER_WARNING = 'Tokens without expiry stay valid until revoked. Prefer an expiry and rotate the token.'

const name = ref('')
const description = ref('')
const preset = ref(DEFAULT_EXPIRY)
const customDate = ref('')
const access = ref(DEFAULT_ACCESS)
const submitted = ref(false)
const busy = ref(false)
const failure = ref(null)

const isNever = computed(() => preset.value === 'never')
const isCustom = computed(() => preset.value === 'custom')
const limits = computed(() => customDateLimits())

const nameError = computed(() => (submitted.value ? validateTokenName(name.value) : ''))
// The custom date is checked as soon as it is typed; an empty one only once the form is sent.
const customIssue = computed(() => (isCustom.value ? validateCustomDate(customDate.value) : ''))
const customError = computed(() => (submitted.value || customDate.value !== '' ? customIssue.value : ''))
const canSubmit = computed(() => validateTokenName(name.value) === '' && customIssue.value === '')

// Under the control: the resulting date, or the warning when the token never expires.
const expiryNote = computed(() => {
  if (isNever.value) return NEVER_WARNING
  if (customIssue.value !== '') return ''
  const expiresAt = expiresAtFor({ preset: preset.value, customDate: customDate.value })
  // The expiry is the ISO string this dialog built itself, not a wire value from the API.
  return `Expires on ${formatExpiryDate(new Date(expiresAt), props.locale)}`
})

const reset = () => {
  name.value = ''
  description.value = ''
  preset.value = DEFAULT_EXPIRY
  customDate.value = ''
  access.value = DEFAULT_ACCESS
  submitted.value = false
  busy.value = false
  failure.value = null
}

const close = () => {
  emit('update:modelValue', false)
}

const submit = async () => {
  submitted.value = true
  failure.value = null
  if (!canSubmit.value) {
    return
  }
  busy.value = true
  try {
    const response = await props.api.create(buildCreateBody({
      name: name.value,
      description: description.value,
      preset: preset.value,
      customDate: customDate.value,
      scopes: scopesFor(access.value)
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

    <!--
      Spacing (MD3 dialog): 16px between fields, 24px between the sections (Expires, Access,
      the failure notice and the actions), 16px from the segmented control to the date field,
      and 8px from a section label to its content.
    -->
    <form
      id="create-token-form"
      class="mt-6 flex flex-col gap-6"
      novalidate
      @submit.prevent="submit()"
    >
      <div class="flex flex-col gap-4">
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
      </div>

      <div class="flex flex-col">
        <span class="md-title-small mb-2 text-on-surface-variant">Expires</span>
        <SegmentedControl
          label="Expiry"
          aria-describedby="create-token-expiry-note"
        >
          <SegmentedOption
            v-for="option in EXPIRY_PRESETS"
            :key="option.key"
            :on="preset === option.key"
            @click="preset = option.key"
          >
            {{ option.label }}
          </SegmentedOption>
        </SegmentedControl>
        <MdTextField
          v-if="isCustom"
          id="create-token-expiry-date"
          v-model="customDate"
          class="mt-4"
          label="Expiry date"
          type="date"
          variant="outlined"
          supporting-text="Last day the token is valid"
          :min="limits.min"
          :max="limits.max"
          :error="customError !== ''"
          :error-text="customError"
        />
        <p
          id="create-token-expiry-note"
          class="mt-2 md-body-small"
          :class="isNever ? 'text-error' : 'text-on-surface-variant'"
        >
          {{ expiryNote }}
        </p>
      </div>

      <fieldset class="flex flex-col border-0 p-0">
        <legend class="md-title-small mb-2 text-on-surface-variant">
          Access
        </legend>
        <div
          v-for="option in ACCESS_OPTIONS"
          :key="option.key"
          class="flex flex-col"
        >
          <MdRadio
            :id="`create-token-access-${option.key}`"
            v-model="access"
            name="create-token-access"
            :value="option.key"
            :aria-describedby="`create-token-access-${option.key}-help`"
          >
            {{ option.label }}
          </MdRadio>
          <p
            :id="`create-token-access-${option.key}-help`"
            class="pl-12 md-body-small text-on-surface-variant"
          >
            {{ option.help }}
          </p>
        </div>
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
        variant="filled"
        :disabled="busy || !canSubmit"
      >
        {{ busy ? 'Creating...' : 'Create' }}
      </MdButton>
    </div>
  </DialogShell>
</template>
