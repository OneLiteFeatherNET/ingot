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

<script>
import { and, isIntegerControl, isNumberControl, isStringControl, isEnumControl, not, or, rankWith } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed, ref, watch } from 'vue'
import MdTextField from '../md/MdTextField.vue'
import MdIconButton from '../md/MdIconButton.vue'
import { descriptionText } from './description.js'
import { isSensitive } from './sensitive.js'

/*
 * Outlined text field for a string, a number or an integer. It replaces the vanilla input for
 * these controls: the label sits inside the field and the description under it. The rank is
 * above the vanilla renderers, and enums are left to MdSelectRenderer.
 */
export const tester = rankWith(3, or(and(isStringControl, not(isEnumControl)), isNumberControl, isIntegerControl))

export default {
  name: 'MdTextFieldRenderer',
  components: {
    MdIconButton,
    MdTextField
  },
  props: rendererProps(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const path = computed(() => input.control.value.path)
    const numeric = computed(() => ['number', 'integer'].includes(input.control.value.schema?.type))

    // A secret is a string whose key or schema marks it as one (see sensitive.js). It is masked until
    // the user reveals it with the toggle in the trailing icon.
    const secret = computed(() => !numeric.value && isSensitive(path.value, input.control.value.schema))
    const revealed = ref(false)
    const toggleReveal = () => { revealed.value = !revealed.value }
    // Another entry, or another variant, is another value: it starts masked again.
    watch(path, () => { revealed.value = false })
    const inputType = computed(() => {
      if (secret.value && !revealed.value) return 'password'
      return numeric.value ? 'number' : 'text'
    })
    const displayValue = computed(() => {
      const value = input.control.value.data
      return value === undefined || value === null ? '' : String(value)
    })

    // An emptied field removes the value instead of storing an empty string, so the property is
    // left out of the saved configuration.
    const onInput = (raw) => {
      if (raw === '') {
        input.handleChange(path.value, undefined)
      } else if (numeric.value) {
        const parsed = Number(raw)
        input.handleChange(path.value, Number.isNaN(parsed) ? undefined : parsed)
      } else {
        input.handleChange(path.value, raw)
      }
    }

    return {
      ...input,
      descriptionText,
      displayValue,
      inputType,
      numeric,
      onInput,
      revealed,
      secret,
      toggleReveal
    }
  }
}
</script>

<template>
  <MdTextField
    v-if="control.visible"
    :id="`${control.id}-input`"
    :model-value="displayValue"
    :label="control.label"
    variant="outlined"
    :type="inputType"
    :autocomplete="secret ? 'new-password' : undefined"
    :spellcheck="secret ? 'false' : undefined"
    :supporting-text="descriptionText(control.description)"
    :error="Boolean(control.errors)"
    :error-text="control.errors || ''"
    :disabled="!control.enabled"
    :required="control.required"
    @update:model-value="onInput"
  >
    <template
      v-if="secret"
      #trailing-icon
    >
      <MdIconButton
        :aria-label="revealed ? 'Hide secret' : 'Show secret'"
        toggle
        :selected="revealed"
        :disabled="!control.enabled"
        @click="toggleReveal"
      >
        <svg
          v-if="!revealed"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z" />
        </svg>
        <svg
          v-else
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z" />
        </svg>
      </MdIconButton>
    </template>
  </MdTextField>
</template>
