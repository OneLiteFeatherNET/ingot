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
defineProps({
  /** Reserved for the one property that changes what a token may reach. */
  accent: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!-- A static MD3 badge: a label, not an action, so it is not a chip. -->
  <span
    class="md-label-small inline-flex items-center whitespace-nowrap rounded-corner-small px-2 py-0.5"
    :class="accent
      ? 'bg-tertiary-container text-on-tertiary-container'
      : 'bg-secondary-container text-on-secondary-container'"
  >
    <slot />
  </span>
</template>
