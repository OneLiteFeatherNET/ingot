<template>
  <div v-if="control.visible">
    <DispatchRenderer
      :visible="control.visible"
      :enabled="control.enabled"
      :schema="control.schema"
      :uischema="detailUiSchema"
      :path="control.path"
      :renderers="control.renderers"
      :cells="control.cells"
    />
  </div>
</template>

<script>
import {findUISchema, isObjectControl, rankWith} from '@jsonforms/core'
import {DispatchRenderer, rendererProps, useJsonFormsControlWithDetail} from '@jsonforms/vue'
import {useVanillaControl} from '@dzikoysk/vue-vanilla'
import {inject} from 'vue'
import {OPTIONAL_PATH_KEY, useNested} from './util'
import isEmpty from 'lodash/isEmpty'

export const tester = rankWith(2, isObjectControl)

export default {
  name: 'ObjectRenderer',
  components: {
    DispatchRenderer
  },
  props: rendererProps(),
  setup: props => {
    const control = useVanillaControl(useJsonFormsControlWithDetail(props))
    const nested = useNested('object')
    // The optional switch above this object names its path: the object there draws no title of its own.
    const optionalPath = inject(OPTIONAL_PATH_KEY, undefined)
    const titleHidden = optionalPath !== undefined && optionalPath === props.path
    return {
      ...control,
      nested,
      titleHidden,
    }
  },
  computed: {
    detailUiSchema() {
      const result = findUISchema(
          this.control.uischemas,
          this.control.schema,
          this.control.uischema.scope,
          this.control.path,
          'Group',
          this.control.uischema,
          this.control.rootSchema
      )
      if (isEmpty(this.control.path)) {
        result.type = 'VerticalLayout'
      } else {
        result.label = this.control.label
        // An optional object under its own switch row: the row is the title, so the section draws no heading.
        if (this.titleHidden) {
          result.options = { ...result.options, hideTitle: true }
        }
        if (this.nested.level > 0) {
          result.options = {
            ...result.options,
            bare: true,
            alignLeft:
                this.nested.level >= 4 || this.nested.parentElement === 'array',
          }
        }
      }
      return result
    }
  }
}
</script>

<style scoped>

</style>
