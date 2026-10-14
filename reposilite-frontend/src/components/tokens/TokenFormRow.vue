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
  <!-- An editor sits on a raised surface, so it reads as the open part of the list. -->
  <div
    class="flex flex-wrap items-center gap-3 border-b border-outline-variant bg-surface-container-high py-4"
    :class="indent ? 'pl-12 pr-4' : 'px-4'"
  >
    <slot />
  </div>
</template>
