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
  on: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!--
    aria-pressed carries the state for assistive technology. The selected segment also gets the
    check mark MD3 uses, so the state does not rest on the colour alone.
  -->
  <button
    type="button"
    :aria-pressed="on"
    class="md-state-layer md-focus-ring md-label-large relative inline-flex min-h-12 items-center gap-1.5 border-l border-outline px-4 first:border-l-0"
    :class="on ? 'bg-secondary-container text-on-secondary-container' : 'bg-transparent text-on-surface'"
  >
    <svg
      v-if="on"
      class="size-[18px] shrink-0"
      viewBox="0 -960 960 960"
      aria-hidden="true"
      focusable="false"
    >
      <path
        fill="currentColor"
        d="M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z"
      />
    </svg>
    <slot />
  </button>
</template>
