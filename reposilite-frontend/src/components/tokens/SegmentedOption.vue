<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup>
defineProps({
  on: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!-- aria-pressed, because the blue fill is the only other sign that an option is on. -->
  <button
    type="button"
    :aria-pressed="on"
    class="h-8 border-r border-gray-300 px-3 text-xs last:border-r-0 dark:border-gray-700"
    :class="on ? 'bg-blue-600 text-white' : 'bg-white text-gray-600 dark:bg-gray-800 dark:text-gray-400'"
  >
    <slot />
  </button>
</template>
