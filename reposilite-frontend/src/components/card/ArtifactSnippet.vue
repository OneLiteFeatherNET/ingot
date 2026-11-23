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
import type { ArtifactSnippetData, SnippetConfiguration } from './snippetTypes'

const props = defineProps({
  configuration: {
    type: Object as PropType<SnippetConfiguration>,
    required: true
  },
  data: {
    type: Object as PropType<ArtifactSnippetData>,
    required: true
  }
})

// The snippet as it is shown and copied: no line break before the first line or after the last.
const text = computed(() => {
  const { groupId, artifactId, version } = props.data
  const coordinate = `${groupId}:${artifactId}:${version}`
  switch (props.configuration.lang) {
    case 'xml':
      return `<dependency>\n  <groupId>${groupId}</groupId>\n  <artifactId>${artifactId}</artifactId>\n  <version>${version}</version>\n</dependency>`
    case 'groovy':
      return `implementation "${coordinate}"`
    case 'kotlin':
      return `implementation("${coordinate}")`
    case 'scala':
      return `"${groupId}" %% "${artifactId}" %% "${version}"`
    default:
      return ''
  }
})
</script>

<template>
  <div data-ingot-component="artifact-snippet">
    <MdCopyableCode
      :text="text"
      :label="`${configuration.name} snippet, scrolls sideways`"
      copy-label="Copy snippet"
    />
  </div>
</template>
