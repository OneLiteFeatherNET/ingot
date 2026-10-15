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
// Material Symbols "history" (Apache 2.0). One glyph serves both states: the active section is
// marked by its indicator pill, so the icon itself does not need a second drawing.
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})

const GLYPH = 'M480-120q-75 0-140.5-28.5t-114-77q-48.5-48.5-77-114T120-440h60q0 125 87.5 212.5T480-140q125 0 212.5-87.5T780-440q0-125-87.5-212.5T480-740q-68 0-127 31t-103 85h110v60H130v-200h60v102q50-62 120.5-97T480-800q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-440q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-120Zm112-192L440-464v-216h60v184l144 144-43 44Z'
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
    <path :d="GLYPH" />
  </svg>
</template>
