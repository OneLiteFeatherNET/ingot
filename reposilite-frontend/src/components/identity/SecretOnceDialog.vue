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
import { createErrorToast, createSuccessToast } from '../../helpers/toast'
import CopyIcon from '../icons/CopyIcon.vue'
import MdButton from '../md/MdButton.vue'
import DialogShell from './DialogShell.vue'

/*
 * Shows a secret exactly once, after a token was created or rotated.
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   secret      the secret to show. It lives only in this prop and in the field: it is never
 *               written to a storage or to the address, and the parent forgets it on close.
 *   tokenName   the name of the token, for the heading text
 *   heading     the title of the dialog
 * Emits update:modelValue (false when the one close action is used).
 *
 * Only the Close button closes the dialog; Escape and a click outside do not, so the secret
 * cannot be lost by accident.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  secret: {
    type: String,
    default: ''
  },
  tokenName: {
    type: String,
    default: ''
  },
  heading: {
    type: String,
    default: 'Your new secret'
  }
})

const emit = defineEmits(['update:modelValue'])

const copy = async () => {
  try {
    await navigator.clipboard.writeText(props.secret)
    createSuccessToast('Secret copied')
  } catch {
    createErrorToast('Could not copy the secret. Select it and copy it by hand.')
  }
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="secret-once-title"
    :dismissable="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <h2
      id="secret-once-title"
      class="md-headline-small"
    >
      {{ heading }}
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      Secret for token <span class="font-medium text-on-surface">{{ tokenName }}</span>
    </p>

    <div class="mt-4 flex items-center gap-2">
      <input
        type="text"
        readonly
        :value="secret"
        aria-label="Token secret"
        spellcheck="false"
        autocomplete="off"
        class="md-focus-ring min-w-0 flex-1 rounded-corner-small border border-outline bg-surface-container-highest px-3 py-3 font-mono text-sm text-on-surface"
        @focus="$event.target.select()"
      >
      <MdButton
        variant="tonal"
        @click="copy()"
      >
        <template #icon>
          <CopyIcon />
        </template>
        Copy
      </MdButton>
    </div>

    <p
      role="alert"
      class="mt-4 rounded-corner-medium bg-error-container px-4 py-3 md-body-medium text-on-error-container"
    >
      You will not see this secret again. Copy it now and keep it somewhere safe.
    </p>

    <div class="mt-6 flex justify-end">
      <MdButton @click="emit('update:modelValue', false)">
        Close
      </MdButton>
    </div>
  </DialogShell>
</template>
