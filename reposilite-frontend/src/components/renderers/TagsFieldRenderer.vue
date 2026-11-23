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
import { and, rankWith, schemaMatches, scopeEndsWith } from '@jsonforms/core'
import type { ControlElement } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import { computed, defineComponent } from 'vue'
import MdChipInput from '../md/MdChipInput.vue'
import { useFieldBinding } from './useFieldBinding'
import { validateTag } from '../../store/maven/repositories'

/*
 * The tags of a repository as an input chip field. It takes the array named `tags` of strings, and
 * ranks above the generic list, which would draw each tag as a separate text field. The tags that
 * other entries of the collection use come from the form configuration (`tagSuggestions`), which
 * the entry detail sets, so the field offers them while it has focus.
 */
export const tester = rankWith(4, and(
  scopeEndsWith('tags'),
  schemaMatches((schema) => schema.type === 'array' && (schema as { items?: { type?: string } }).items?.type === 'string')
))

export default defineComponent({
  name: 'TagsFieldRenderer',
  components: {
    MdChipInput
  },
  props: rendererProps<ControlElement>(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const { fieldProps, change } = useFieldBinding({ control: input.control, handleChange: input.handleChange })

    const tags = computed<string[]>(() => {
      const data: unknown = input.control.value.data
      return Array.isArray(data) ? (data as string[]) : []
    })
    const suggestions = computed<string[]>(() => (input.control.value.config as { tagSuggestions?: string[] } | undefined)?.tagSuggestions ?? [])
    const maxItems = computed(() => (input.control.value.schema as { maxItems?: number } | undefined)?.maxItems ?? 0)

    // An empty list stays an empty list: removing the last chip must not drop the property.
    const onChange = (next: string[]) => {
      change(next)
    }

    return {
      ...input,
      fieldProps,
      maxItems,
      onChange,
      suggestions,
      tags,
      validateTag
    }
  }
})
</script>

<template>
  <MdChipInput
    v-if="control.visible"
    :id="`${control.id}-input`"
    :model-value="tags"
    :label="control.label || 'Tags'"
    :supporting-text="fieldProps.supportingText"
    :error="fieldProps.error"
    :error-text="fieldProps.errorText"
    :suggestions="suggestions"
    :validate="validateTag"
    :max-items="maxItems"
    :disabled="fieldProps.disabled"
    @update:model-value="onChange"
  />
</template>
