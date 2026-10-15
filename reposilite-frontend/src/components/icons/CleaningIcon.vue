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
// Material Symbols "cleaning_services", outlined and filled (Apache 2.0). The filled glyph is the active one.
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})

const OUTLINED = 'M120-40v-290q0-78.85 55.57-134.42Q231.15-520 310-520h60v-340q0-24.75 17.63-42.38Q405.25-920 430-920h100q24.75 0 42.38 17.62Q590-884.75 590-860v340h60q78.85 0 134.42 55.58Q840-408.85 840-330v290H120Zm60-60h105v-130q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v130h105v-130q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v130h105v-130q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v130h105v-230q0-54.17-37.92-92.08Q704.17-460 650-460H310q-54.17 0-92.08 37.92Q180-384.17 180-330v230Zm350-420v-340H430v340h100Zm0 0H430h100Z'
const FILLED = 'M120-40v-290q0-78.85 55.57-134.42Q231.15-520 310-520h60v-340q0-24.75 17.63-42.38Q405.25-920 430-920h100q24.75 0 42.38 17.62Q590-884.75 590-860v340h60q78.85 0 134.42 55.58Q840-408.85 840-330v290H120Zm60-60h105v-130q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v130h105v-130q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v130h105v-130q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v130h105v-230q0-54.17-37.92-92.08Q704.17-460 650-460H310q-54.17 0-92.08 37.92Q180-384.17 180-330v230Z'
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
