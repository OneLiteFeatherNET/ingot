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
    One segment of a SegmentedControl. It is a radio: aria-checked carries the state and the
    roving tabindex puts only the selected segment in the tab order. The segment is 40px tall.
    Dividers are drawn by the segment itself: a top and a left border, moved 1px over the
    track's own border by a transform (no layout change) so the track's edge is the only line. Below sm the segment never
    shrinks below its label (min-w-max) and the row wraps; from sm it shares the row equally
    (min-w-0). The selected segment is filled with secondary-container inside the track's own
    rounded edge, so there is no separate pill and no ring. The focus ring is inset and shows
    for keyboard focus only.
  -->
  <button
    type="button"
    role="radio"
    :aria-checked="on ? 'true' : 'false'"
    :tabindex="on ? 0 : -1"
    class="md-state-layer md-focus-ring-inset md-label-large relative inline-flex h-10 min-w-max flex-1 basis-0 items-center justify-center gap-1 whitespace-nowrap border-l border-t border-outline px-2 -translate-x-px -translate-y-px first:border-l-0 sm:min-w-0"
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
