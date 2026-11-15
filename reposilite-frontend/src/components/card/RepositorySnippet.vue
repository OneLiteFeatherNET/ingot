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
import XmlTag from './XmlTag.vue'
import CodeString from './CodeString.vue'
import CodeBrackets from "./CodeBrackets.vue"
import { computed, ref, type PropType } from 'vue'
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

const preElement = ref<HTMLElement | null>(null)
const content = computed(() => preElement?.value?.textContent)

defineExpose({ content })
</script>

<template>
  <!--
    The closing tag stays on the last line of each snippet: a line break before it would be shown and copied
    as an empty line.
  -->
  <pre
    v-if="configuration.lang === 'xml'"
    ref="preElement"
    class="whitespace-pre"
    data-ingot-component="repository-snippet"
  >
<XmlTag name="repository">
  <XmlTag name="id">{{ data.repoId }}</XmlTag>
  <XmlTag name="name">{{ data.title }}</XmlTag>
  <XmlTag name="url"><span class="snippet-url">{{ data.domain }}</span></XmlTag>
</XmlTag></pre>
  <pre
    v-else-if="configuration.lang === 'groovy'"
    ref="preElement"
    class="whitespace-pre"
    data-ingot-component="repository-snippet"
  >
maven <CodeBrackets
start="{"
end="}"
>
    name <CodeString>{{ gradleId }}</CodeString>
    url <CodeString><span class="snippet-url">{{ data.domain }}</span></CodeString>
</CodeBrackets></pre>
  <pre
    v-else-if="configuration.lang === 'kotlin'"
    ref="preElement"
    class="whitespace-pre"
    data-ingot-component="repository-snippet"
  >
maven <CodeBrackets
start="{"
end="}"
>
    name = <CodeString>{{ gradleId }}</CodeString>
    url = uri<CodeBrackets
start="("
end=")"
><CodeString><span class="snippet-url">{{ data.domain }}</span></CodeString></CodeBrackets>
</CodeBrackets></pre>
  <pre
    v-else-if="configuration.lang === 'scala'"
    ref="preElement"
    class="whitespace-pre"
    data-ingot-component="repository-snippet"
  >
resolvers += <CodeString>{{ data.repoId }}</CodeString> at <CodeString><span class="snippet-url">{{ data.domain }}</span></CodeString></pre>
</template>
