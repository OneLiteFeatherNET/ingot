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
// Material Symbols "key", outlined and filled (Apache 2.0). The filled glyph is the active one.
defineProps({
  filled: {
    type: Boolean,
    default: false
  }
})

const OUTLINED = 'M280-412q-28 0-48-20t-20-48q0-28 20-48t48-20q28 0 48 20t20 48q0 28-20 48t-48 20Zm0 172q-100 0-170-70T40-480q0-100 70-170t170-70q72 0 126 34t85 103h356l113 113-167 153-88-64-88 64-75-60h-51q-25 60-78.5 98.5T280-240Zm0-60q58 0 107-38.5t63-98.5h114l54 45 88-63 82 62 85-79-51-51H450q-12-56-60-96.5T280-660q-75 0-127.5 52.5T100-480q0 75 52.5 127.5T280-300Z'
const FILLED = 'M280-351q54 0 91.5-37.5T409-480q0-54-37.5-91.5T280-609q-54 0-91.5 37.5T151-480q0 54 37.5 91.5T280-351Zm0 111q-100 0-170-70T40-480q0-100 70-170t170-70q85 0 142.5 46.5T503-555h344l73 73-127 146-103-84-84 84-71-71h-32q-19 75-80.5 121T280-240Z'
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
