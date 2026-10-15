<template>
  <section
    v-if="layout.visible"
    class="flex min-w-0 flex-col gap-4"
    data-test="section"
  >
    <h3
      v-if="layout.label"
      class="md-title-medium text-on-surface"
    >
      {{ layout.label }}
    </h3>
    <DispatchRenderer
      v-for="(element, index) in layout.uischema.elements"
      :key="`${layout.path}-${index}`"
      :schema="layout.schema"
      :uischema="element"
      :path="layout.path"
      :enabled="layout.enabled"
      :renderers="layout.renderers"
      :cells="layout.cells"
    />
  </section>
</template>

<script>
import {and, isLayout, rankWith, uiTypeIs} from '@jsonforms/core'
import {DispatchRenderer, rendererProps, useJsonFormsLayout} from '@jsonforms/vue'
import {useVanillaLayout} from '@dzikoysk/vue-vanilla'

/*
 * A Group is a titled section of a form, not a fieldset. The vanilla group draws a bordered box
 * with the label notched into its edge, which is not a Material Design 3 pattern. Here the label
 * is a title-medium heading, there is no border, and the elements are spaced 16px apart inside
 * the section. The rank is above the vanilla group (2), so this one is used for every group.
 */
export const tester = rankWith(4, and(isLayout, uiTypeIs('Group')))

export default {
  name: 'SectionRenderer',
  components: {
    DispatchRenderer
  },
  props: rendererProps(),
  setup(props) {
    return useVanillaLayout(useJsonFormsLayout(props))
  }
}
</script>
