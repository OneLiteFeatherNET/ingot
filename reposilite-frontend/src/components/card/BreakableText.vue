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

<script setup>
import { computed } from 'vue'

/*
 * Text such as a URL that may wrap, drawn with a line break opportunity after each path slash and before
 * each "<" placeholder. Only the drawing changes: the <wbr> elements hold no text, so the text content, and
 * with it the copied snippet, stays the same.
 */
const props = defineProps({
  text: {
    type: String,
    required: true
  }
})

// Splits after a slash that does not start a "//", and before a "<" placeholder.
const pieces = computed(() => props.text.split(/(?<=\/)(?!\/)|(?=<)/).filter(piece => piece !== ''))
</script>

<template>
  <template
    v-for="(piece, index) in pieces"
    :key="index"
  ><wbr v-if="index > 0">{{ piece }}</template>
</template>
