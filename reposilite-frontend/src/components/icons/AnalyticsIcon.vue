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
// Three bars of a chart. The active icon is drawn filled, the others outlined.
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <svg
    class="size-6"
    viewBox="0 0 24 24"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <g
      v-if="filled"
      fill="currentColor"
    >
      <rect
        x="3"
        y="12"
        width="4"
        height="9"
        rx="1"
      />
      <rect
        x="10"
        y="3"
        width="4"
        height="18"
        rx="1"
      />
      <rect
        x="17"
        y="8"
        width="4"
        height="13"
        rx="1"
      />
    </g>
    <g
      v-else
      fill="none"
      stroke="currentColor"
      stroke-width="2"
    >
      <rect
        x="3.5"
        y="12.5"
        width="4"
        height="8"
        rx="1"
      />
      <rect
        x="10.5"
        y="3.5"
        width="4"
        height="17"
        rx="1"
      />
      <rect
        x="17.5"
        y="8.5"
        width="4"
        height="12"
        rx="1"
      />
    </g>
  </svg>
</template>
