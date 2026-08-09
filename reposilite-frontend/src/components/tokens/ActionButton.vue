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
  /** One filled button per form: the action the form exists for. */
  primary: {
    type: Boolean,
    default: false
  },
  /** Buttons that sit inside a row of form controls match their 32px height. */
  small: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <button
    type="button"
    class="whitespace-nowrap rounded-md"
    :class="[
      small ? 'h-8 px-3' : 'h-9 px-3.5',
      primary
        ? 'bg-blue-700 font-medium text-white hover:bg-blue-800'
        : 'border border-gray-300 dark:border-gray-700'
    ]"
  >
    <slot />
  </button>
</template>
