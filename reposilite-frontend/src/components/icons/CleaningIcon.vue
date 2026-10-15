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
// A broom for the maintenance section: a handle, a collar and a tapered head. One outline serves
// both states. The filled glyph is the solid broom, the outlined one is the same shape stroked.
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})

const BROOM = 'M20.93 1.93L22.07 3.07L13.07 12.07L11.93 10.93Z M11.65 10.65L13.35 12.35L7.76 20.76L3.24 16.24Z'
</script>

<template>
  <svg
    class="size-6"
    viewBox="0 0 24 24"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <path
      :d="BROOM"
      :fill="filled ? 'currentColor' : 'none'"
      :stroke="filled ? 'none' : 'currentColor'"
      stroke-width="1.6"
      stroke-linejoin="round"
    />
  </svg>
</template>
