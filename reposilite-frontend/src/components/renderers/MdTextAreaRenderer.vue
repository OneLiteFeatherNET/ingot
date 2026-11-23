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
import { rankWith, schemaMatches } from '@jsonforms/core'
import type { ControlElement } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed, defineComponent } from 'vue'
import MdTextArea from '../md/MdTextArea.vue'
import { useFieldBinding } from './useFieldBinding'

// A string may be this long and still fit one line of the text field. Longer limits are prose.
const SINGLE_LINE_LIMIT = 120

/*
 * A multi-line field for prose: a string whose schema limits it to more than a line's worth of text,
 * such as the 280 characters of a repository description. It shows the count against the limit. The
 * rank is above the single line field, which would otherwise take the string.
 */
export const tester = rankWith(4, schemaMatches((schema) => {
  const maxLength = (schema as { maxLength?: number }).maxLength
  return schema.type === 'string' && schema.enum === undefined && maxLength !== undefined && Number.isInteger(maxLength) && maxLength > SINGLE_LINE_LIMIT
}))

export default defineComponent({
  name: 'MdTextAreaRenderer',
  components: {
    MdTextArea
  },
  props: rendererProps<ControlElement>(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const { text: value, fieldProps, onInput } = useFieldBinding({ control: input.control, handleChange: input.handleChange })
    const maxLength = computed(() => (input.control.value.schema as { maxLength?: number } | undefined)?.maxLength ?? 0)

    return {
      ...input,
      fieldProps,
      maxLength,
      onInput,
      value
    }
  }
})
</script>

<template>
  <MdTextArea
    v-if="control.visible"
    v-bind="fieldProps"
    :model-value="value"
    :max-length="maxLength"
    @update:model-value="onInput"
  />
</template>
