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
  /** Lines the editor up with the route rows it belongs to. */
  indent: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!--
    No `indent-6` here, unlike TokenRow: text-indent inherits into the inputs and pushes
    their value 24px away from the caret, which is the one place the Windi behaviour was
    plainly an accident rather than a layout.
  -->
  <div
    class="flex flex-wrap items-center gap-2 border-b border-gray-200 bg-gray-100 py-3 dark:border-gray-800 dark:bg-gray-900"
    :class="indent ? 'pl-9.5 pr-4.5' : 'px-4.5'"
  >
    <slot />
  </div>
</template>
