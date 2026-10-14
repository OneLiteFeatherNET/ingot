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
  /** Routes and the manager note belong to the token above them, so they sit one step in. */
  indent: {
    type: Boolean,
    default: false
  },
  /** Marks the row whose inline editor is open below it. */
  highlighted: {
    type: Boolean,
    default: false
  },
  hoverable: {
    type: Boolean,
    default: true
  },
  /**
   * Route rows carry an action per route, which turns the list into a wall of icons. They
   * stay out of the way until the row is hovered or something inside it takes focus. Touch screens
   * have no hover, so on a coarse pointer they are always shown.
   */
  revealActionsOnHover: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!-- A list item on the list surface. Indented rows sit under the token they belong to. -->
  <div
    class="group flex min-h-12 flex-wrap items-center gap-2 border-b border-outline-variant py-2 transition-colors"
    :class="[
      indent ? 'pl-12 pr-4' : 'px-4',
      hoverable ? 'hover:bg-surface-container' : '',
      highlighted ? 'bg-surface-container-high' : ''
    ]"
  >
    <slot />
    <span
      v-if="$slots.actions"
      class="flex items-center gap-1"
      :class="revealActionsOnHover ? 'opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100 [@media(pointer:coarse)]:opacity-100' : ''"
    >
      <slot name="actions" />
    </span>
  </div>
</template>
