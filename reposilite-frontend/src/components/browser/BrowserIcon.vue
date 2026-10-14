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
import { GLYPHS } from './glyphs'

const props = defineProps({
  name: {
    type: String,
    required: true,
    validator: (value) => Object.hasOwn(GLYPHS, value)
  }
})

const glyph = computed(() => GLYPHS[props.name])
</script>

<template>
  <svg
    class="size-6 shrink-0"
    viewBox="0 -960 960 960"
    fill="currentColor"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="glyph" />
  </svg>
</template>
