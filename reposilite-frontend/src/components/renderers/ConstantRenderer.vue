<template>
  <div
    v-if="control.visible"
    :id="`${control.id}-input`"
    class="flex min-h-14 flex-col justify-center gap-0.5 py-2 pl-4"
    data-test="constant-row"
    data-ingot-component="constant-renderer"
  >
    <span class="md-body-large text-on-surface">{{ control.label }}</span>
    <span class="md-body-medium text-on-surface-variant">{{ displayValue }}</span>
  </div>
</template>

<script lang="ts">
import {and, isControl, rankWith, schemaMatches} from '@jsonforms/core'
import type {ControlElement} from '@jsonforms/core'
import {rendererProps, useJsonFormsControl} from '@jsonforms/vue'
import {useVanillaControl} from '@dzikoysk/vue-vanilla'
import {computed, defineComponent} from 'vue'

/*
 * A constant is not an input: its value is fixed by the schema, so there is nothing to type or
 * pick. It is drawn as a read-only list row, the label on the first line and the value beneath it
 * as supporting text, rather than as a greyed-out field that looks editable.
 */
export const tester = rankWith(4, and(isControl, schemaMatches(schema => 'const' in schema)))

export default defineComponent({
  name: 'ConstantRenderer',
  props: rendererProps<ControlElement>(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const displayValue = computed(() => {
      const value: unknown = input.control.value.schema?.const
      return value === undefined || value === null ? '' : String(value)
    })
    return {
      ...input,
      displayValue
    }
  }
})
</script>
