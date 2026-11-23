<!--
  - Copyright (c) 2023 dzikoysk
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

<script setup lang="ts">
import MdCopyableCode from '../md/MdCopyableCode.vue'
import { computed, type PropType } from 'vue'
import type { RepositorySnippetData, SnippetConfiguration } from './snippetTypes'

const props = defineProps({
  configuration: {
    type: Object as PropType<SnippetConfiguration>,
    required: true
  },
  data: {
    type: Object as PropType<RepositorySnippetData>,
    required: true
  }
})

const gradleId = computed(() => {
  const gradleIdUppercase = props.data.repoId
      .split('-')
      .map(it => it.charAt(0).toUpperCase() + it.slice(1))
      .join('')

  return gradleIdUppercase.charAt(0).toLowerCase() + gradleIdUppercase.slice(1)
})

// The snippet as it is shown and copied: no line break before the first line or after the last.
const text = computed(() => {
  const { repoId, title, domain } = props.data
  switch (props.configuration.lang) {
    case 'xml':
      return `<repository>\n  <id>${repoId}</id>\n  <name>${title}</name>\n  <url>${domain}</url>\n</repository>`
    case 'groovy':
      return `maven {\n    name "${gradleId.value}"\n    url "${domain}"\n}`
    case 'kotlin':
      return `maven {\n    name = "${gradleId.value}"\n    url = uri("${domain}")\n}`
    case 'scala':
      return `resolvers += "${repoId}" at "${domain}"`
    default:
      return ''
  }
})
</script>

<template>
  <div data-ingot-component="repository-snippet">
    <MdCopyableCode
      :text="text"
      :label="`${configuration.name} snippet, scrolls sideways`"
      copy-label="Copy snippet"
    />
  </div>
</template>
