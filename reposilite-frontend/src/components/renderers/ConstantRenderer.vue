<template>
  <MdList
    v-if="control.visible"
    data-ingot-component="constant-renderer"
  >
    <MdListItem
      :id="`${control.id}-input`"
      data-test="constant-row"
    >
      <span class="block md-body-large text-on-surface">{{ control.label }}</span>
      <span class="block md-body-medium text-on-surface-variant">{{ displayValue }}</span>
    </MdListItem>
  </MdList>
</template>

<script lang="ts">
import {and, isControl, rankWith, schemaMatches} from '@jsonforms/core'
import type {ControlElement} from '@jsonforms/core'
import {rendererProps, useJsonFormsControl} from '@jsonforms/vue'
import {useVanillaControl} from '@dzikoysk/vue-vanilla'
import {computed, defineComponent} from 'vue'
import MdList from '../md/MdList.vue'
import MdListItem from '../md/MdListItem.vue'

/*
 * A constant is not an input: its value is fixed by the schema, so there is nothing to type or
 * pick. It is drawn as a read-only list row, the label on the first line and the value beneath it
 * as supporting text, rather than as a greyed-out field that looks editable.
 */
export const tester = rankWith(4, and(isControl, schemaMatches(schema => 'const' in schema)))

export default defineComponent({
  name: 'ConstantRenderer',
  components: {MdList, MdListItem},
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
