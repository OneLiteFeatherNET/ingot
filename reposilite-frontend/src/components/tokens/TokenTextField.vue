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
const model = defineModel({
  type: String,
  default: ''
})

defineProps({
  /**
   * Left empty for plain text on purpose. @tailwindcss/forms only reaches inputs that name
   * a type, and its own font size and padding then fight the utilities below.
   */
  type: {
    type: String,
    default: null
  },
  /** Fills the remaining width of the form row. */
  grow: {
    type: Boolean,
    default: false
  },
  /** Repository paths are read character by character, so they get the mono face. */
  mono: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <input
    v-model="model"
    :type="type"
    class="h-8 rounded-md border border-gray-300 bg-white px-3 text-sm text-gray-800 placeholder-gray-500 outline-none focus:border-blue-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-400"
    :class="[grow ? 'min-w-48 flex-1' : '', mono ? 'font-mono' : '']"
  >
</template>
