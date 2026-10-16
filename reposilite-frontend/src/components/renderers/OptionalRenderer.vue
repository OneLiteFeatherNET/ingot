<template>
  <div>
    <SwitchField
      :id="`${control.id}-present`"
      :label="control.label"
      :model-value="present"
      :description="control.description"
      :disabled="!control.enabled"
      @update:model-value="setPresent"
    />
    <!--
      The fields of an optional object sit under their switch row as an indented sub-group: a 16px
      inset with a subtle rule, no border and no second title, because the switch row already names them.
    -->
    <div
      v-if="present && control.visible"
      class="mt-1 ml-4 border-l border-outline-variant pl-4"
    >
      <DispatchRenderer
        :visible="control.visible"
        :enabled="control.enabled"
        :schema="filteredSchema"
        :uischema="matchedUISchema"
        :path="control.path"
        :renderers="control.renderers"
        :cells="control.cells"
      />
    </div>
  </div>
</template>

<script>
import {DispatchRenderer, rendererProps, useJsonFormsControlWithDetail} from '@jsonforms/vue'
import SwitchField from './SwitchField.vue'
import {useVanillaControl} from '@dzikoysk/vue-vanilla'
import {provide, ref} from 'vue'
import {OPTIONAL_PATH_KEY} from './util'
import {and, rankWith, schemaMatches, uiTypeIs} from '@jsonforms/core'
import includes from 'lodash/includes'
import {findUISchema} from '@jsonforms/core/src/reducers'

export const tester = rankWith(2, and(uiTypeIs('Control'), schemaMatches(schema => Array.isArray(schema.type) && includes(schema.type, 'null'))))

export default {
  name: 'OptionalRenderer',
  components: {DispatchRenderer, SwitchField},
  props: rendererProps(),
  setup(props) {
    const control = useVanillaControl(useJsonFormsControlWithDetail(props))
    const present = ref(control.control.value.data !== undefined)
    const setPresent = (value) => { present.value = value }
    provide(OPTIONAL_PATH_KEY, control.control.value.path)
    return {
      ...control,
      present,
      setPresent
    }
  },
  computed: {
    filteredSchema() {
      const schema = this.control.schema
      return Array.isArray(schema.type) 
        ? {
            ...schema,
            type: schema.type.filter(t => t !== 'null')
          } 
        : schema
    },
    matchedUISchema() {
      return findUISchema(
          this.control.uischemas,
          this.control.schema,
          this.control.scope,
          this.control.path,
          undefined,
          this.control,
          this.control.rootSchema
      )
    }
  }
}
</script>

<style scoped>

</style>
