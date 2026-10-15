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
import { computed } from 'vue'
import MdTextField from '../md/MdTextField.vue'

/*
 * Outlined text field for a string, a number or an integer. It replaces the vanilla input for
 * these controls: the label sits inside the field and the description under it. The rank is
 * above the vanilla renderers, and enums are left to MdSelectRenderer.
 */
export const tester = rankWith(3, or(and(isStringControl, not(isEnumControl)), isNumberControl, isIntegerControl))

export default {
  name: 'MdTextFieldRenderer',
  components: {
    MdTextField
  },
  props: rendererProps(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const path = computed(() => input.control.value.path)
    const numeric = computed(() => ['number', 'integer'].includes(input.control.value.schema?.type))
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
      displayValue,
      numeric,
      onInput
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
    :type="numeric ? 'number' : 'text'"
    :supporting-text="control.description || ''"
    :error="Boolean(control.errors)"
    :error-text="control.errors || ''"
    :disabled="!control.enabled"
    :required="control.required"
    @update:model-value="onInput"
  />
</template>
