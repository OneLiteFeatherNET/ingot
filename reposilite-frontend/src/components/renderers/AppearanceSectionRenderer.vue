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
import { and, isLayout, rankWith, uiTypeIs } from '@jsonforms/core'
import { rendererProps, useJsonFormsLayout } from '@jsonforms/vue'
import { computed } from 'vue'
import SectionRenderer from './SectionRenderer.vue'
import AppearancePreview from '../settings/AppearancePreview.vue'

/* The group of the appearance object of the dashboard settings, which the settings title Appearance. */
const isAppearanceGroup = (uischema, schema) => schema?.title === 'Appearance'

export const tester = rankWith(5, and(isLayout, uiTypeIs('Group'), isAppearanceGroup))

/*
 * The Appearance section, drawn exactly as any other section, with the live preview of its colours
 * under the fields. The preview reads the values of this group, so it follows the form as it is edited.
 */
export default {
  name: 'AppearanceSectionRenderer',
  components: {
    AppearancePreview,
    SectionRenderer
  },
  props: rendererProps(),
  setup(props) {
    const { layout } = useJsonFormsLayout(props)
    const appearance = computed(() => layout.value.data ?? {})
    return { appearance }
  }
}
</script>

<template>
  <div
    class="flex min-w-0 flex-col gap-4"
    data-test="appearance-section"
  >
    <SectionRenderer v-bind="$props" />
    <AppearancePreview :appearance="appearance" />
  </div>
</template>
