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
import { isEnumControl, isOneOfEnumControl, rankWith } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed } from 'vue'
import MdSelect from '../md/MdSelect.vue'

/*
 * Outlined select for an enum, and for a oneOf whose branches are all constants. The option
 * values are compared as text, because a select can only carry text, and the chosen option is
 * written back as the value it stands for. The vanilla enum-oneOf control ranks 5, so the
 * oneOf form is registered above that.
 */
export const tester = rankWith(3, isEnumControl)
export const oneOfTester = rankWith(6, isOneOfEnumControl)

/* The values of an enum, or the constants of a oneOf, with their titles as labels. */
export const optionsOf = (schema) => {
  if (Array.isArray(schema?.enum)) {
    return schema.enum.map(value => ({ value, label: String(value) }))
  }
  return (schema?.oneOf ?? []).map(branch => ({ value: branch.const, label: branch.title ?? String(branch.const) }))
}

export default {
  name: 'MdSelectRenderer',
  components: {
    MdSelect
  },
  props: rendererProps(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const choices = computed(() => optionsOf(input.control.value.schema))
    const options = computed(() => choices.value.map(choice => ({ value: String(choice.value), label: choice.label })))
    const selected = computed(() => {
      const value = input.control.value.data
      return value === undefined || value === null ? '' : String(value)
    })

    const onSelect = (text) => {
      const match = choices.value.find(choice => String(choice.value) === text)
      input.handleChange(input.control.value.path, match?.value)
    }

    return {
      ...input,
      options,
      selected,
      onSelect
    }
  }
}
</script>

<template>
  <MdSelect
    v-if="control.visible"
    :id="`${control.id}-input`"
    :model-value="selected"
    :label="control.label"
    :options="options"
    :supporting-text="control.description || ''"
    :error="Boolean(control.errors)"
    :error-text="control.errors || ''"
    :disabled="!control.enabled"
    :required="control.required"
    @update:model-value="onSelect"
  />
</template>
