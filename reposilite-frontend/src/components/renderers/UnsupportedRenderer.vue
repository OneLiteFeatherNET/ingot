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

<template>
  <MdList
    v-if="control.visible"
    data-ingot-component="unsupported-renderer"
  >
    <MdListItem data-test="unsupported-field">
      <span class="block md-body-large text-on-surface">{{ control.label }}</span>
      <span
        role="note"
        class="block md-body-medium text-on-surface-variant"
      >{{ message }}</span>
    </MdListItem>
  </MdList>
</template>

<script lang="ts">
import {isControl, rankWith} from '@jsonforms/core'
import type {ControlElement} from '@jsonforms/core'
import {rendererProps, useJsonFormsControl} from '@jsonforms/vue'
import {useVanillaControl} from '@dzikoysk/vue-vanilla'
import {defineComponent} from 'vue'
import MdList from '../md/MdList.vue'
import MdListItem from '../md/MdListItem.vue'

/*
 * The last resort for a control. A schema node that no other renderer can draw used to render as nothing,
 * so a section could vanish without a trace. This one shows the field with a pointer to the JSON download instead.
 * The rank is the lowest that still wins, and the renderer is registered after every other one, so a control
 * that another renderer can draw keeps that renderer.
 */
export const tester = rankWith(1, isControl)

export const MESSAGE = 'This section cannot be edited here, use Download as JSON'

export default defineComponent({
  name: 'UnsupportedRenderer',
  components: {MdList, MdListItem},
  props: rendererProps<ControlElement>(),
  setup(props) {
    return {
      ...useVanillaControl(useJsonFormsControl(props)),
      message: MESSAGE
    }
  }
})
</script>
