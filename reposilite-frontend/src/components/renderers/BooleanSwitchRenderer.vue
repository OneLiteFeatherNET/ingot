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
import { isBooleanControl, rankWith } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaControl } from '@dzikoysk/vue-vanilla'
import SwitchField from './SwitchField.vue'

/*
 * Replaces the checkbox of @dzikoysk/vue-vanilla for boolean controls. The rank is higher than
 * the vanilla renderer's, so this one wins for every boolean property.
 */
export const tester = rankWith(2, isBooleanControl)

export default {
  name: 'BooleanSwitchRenderer',
  components: {
    SwitchField
  },
  props: rendererProps(),
  setup(props) {
    const input = useVanillaControl(useJsonFormsControl(props))
    const setValue = (value) => input.handleChange(input.control.value.path, value)
    return {
      ...input,
      setValue
    }
  }
}
</script>

<template>
  <SwitchField
    v-if="control.visible"
    :id="`${control.id}-input`"
    :label="control.label"
    :model-value="!!control.data"
    :disabled="!control.enabled"
    :required="control.required"
    :description="control.description"
    :errors="control.errors"
    @update:model-value="setValue"
  />
</template>
