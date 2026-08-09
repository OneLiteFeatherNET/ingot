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
  /** Reserved for the one property that changes what a token may reach. */
  accent: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <span
    class="whitespace-nowrap rounded-full px-1.5 py-0.5 text-[0.7rem]"
    :class="accent
      ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-200'
      : 'bg-gray-150 text-gray-600 dark:bg-gray-800 dark:text-gray-400'"
  >
    <slot />
  </span>
</template>
