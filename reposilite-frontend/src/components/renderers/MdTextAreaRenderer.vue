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
import { rankWith, schemaMatches } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed } from 'vue'
import MdTextArea from '../md/MdTextArea.vue'
import { descriptionText } from './description.js'

// A string may be this long and still fit one line of the text field. Longer limits are prose.
const SINGLE_LINE_LIMIT = 120

/*
 * A multi-line field for prose: a string whose schema limits it to more than a line's worth of text,
 * such as the 280 characters of a repository description. It shows the count against the limit. The
 * rank is above the single line field, which would otherwise take the string.
 */
export const tester = rankWith(4, schemaMatches((schema) =>
  schema.type === 'string' && schema.enum === undefined && Number.isInteger(schema.maxLength) && schema.maxLength > SINGLE_LINE_LIMIT
))

export default {
  name: 'MdTextAreaRenderer',
  components: {
    MdTextArea
  },
  props: rendererProps(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const path = computed(() => input.control.value.path)
    const maxLength = computed(() => input.control.value.schema?.maxLength ?? 0)
    const value = computed(() => {
      const data = input.control.value.data
      return data === undefined || data === null ? '' : String(data)
    })

    // An emptied field removes the value, so the property is left out and the server default applies.
    const onInput = (raw) => {
      input.handleChange(path.value, raw === '' ? undefined : raw)
    }

    return {
      ...input,
      descriptionText,
      maxLength,
      onInput,
      value
    }
  }
}
</script>

<template>
  <MdTextArea
    v-if="control.visible"
    :id="`${control.id}-input`"
    :model-value="value"
    :label="control.label"
    :max-length="maxLength"
    :supporting-text="descriptionText(control.description)"
    :error="Boolean(control.errors)"
    :error-text="control.errors || ''"
    :disabled="!control.enabled"
    :required="control.required"
    @update:model-value="onInput"
  />
</template>
