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

<script lang="ts">
import { isEnumControl, isOneOfEnumControl, rankWith } from '@jsonforms/core'
import type { ControlElement, JsonSchema } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed, defineComponent } from 'vue'
import { useFieldBinding } from './useFieldBinding'
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
export const optionsOf = (schema?: JsonSchema): Array<{ value: unknown; label: string }> => {
  if (Array.isArray(schema?.enum)) {
    return schema.enum.map(value => ({ value, label: String(value) }))
  }
  const branches = (schema?.oneOf ?? []) as Array<{ const?: unknown; title?: string }>
  return branches.map(branch => ({ value: branch.const, label: branch.title ?? String(branch.const) }))
}

export default defineComponent({
  name: 'MdSelectRenderer',
  components: {
    MdSelect
  },
  props: rendererProps<ControlElement>(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const choices = computed(() => optionsOf(input.control.value.schema))
    const options = computed(() => choices.value.map(choice => ({ value: String(choice.value), label: choice.label })))
    const { text: selected, fieldProps, change } = useFieldBinding({ control: input.control, handleChange: input.handleChange })

    const onSelect = (text: string) => {
      const match = choices.value.find(choice => String(choice.value) === text)
      change(match?.value)
    }

    return {
      ...input,
      fieldProps,
      options,
      selected,
      onSelect
    }
  }
})
</script>

<template>
  <MdSelect
    v-if="control.visible"
    v-bind="fieldProps"
    :model-value="selected"
    :options="options"
    @update:model-value="onSelect"
  />
</template>
