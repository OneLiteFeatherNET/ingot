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
// Material Symbols "home", outlined and filled (Apache 2.0). The filled glyph is the active one.
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})

const OUTLINED = 'M220-180h150v-250h220v250h150v-390L480-765 220-570v390Zm-60 60v-480l320-240 320 240v480H530v-250H430v250H160Zm320-353Z'
const FILLED = 'M160-120v-480l320-240 320 240v480H560v-280H400v280H160Z'
</script>

<template>
  <svg
    class="size-6"
    viewBox="0 -960 960 960"
    fill="currentColor"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="filled ? FILLED : OUTLINED" />
  </svg>
</template>
