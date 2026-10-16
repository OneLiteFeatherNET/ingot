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
// Two people, drawn for the Access section. The active one is drawn filled, the others outlined.
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
    viewBox="0 -960 960 960"
    :fill="filled ? 'currentColor' : 'none'"
    stroke="currentColor"
    stroke-width="60"
    stroke-linejoin="round"
    stroke-linecap="round"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <circle
      cx="360"
      cy="-640"
      r="120"
    />
    <path d="M100-200v-60q0-70 120-115t140-45q20 0 140 45t120 115v60Z" />
    <path d="M640-760a120 120 0 0 1 0 240M700-400q160 40 160 140v60h-100" />
  </svg>
</template>
