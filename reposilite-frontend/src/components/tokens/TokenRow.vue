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
   * stay out of the way until the row is hovered or something inside it takes focus.
   */
  revealActionsOnHover: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!--
    The text-indent is not decoration, it is restored behaviour: Windi shipped an `indent`
    utility of its own, so every element carrying this component's `indent` class silently
    picked up `text-indent: 1.5rem` on top of the padding. Tailwind spells that utility
    `indent-6` and never matched the bare name, which moved these rows 24px to the left.
    Whether these rows want two separate indents at all is a question for the redesign.
  -->
  <div
    class="group flex h-11.5 items-center gap-2 border-b border-gray-200 transition-colors dark:border-gray-800"
    :class="[
      indent ? 'pl-9.5 pr-4.5 indent-6' : 'px-4.5',
      hoverable ? 'hover:bg-gray-50 dark:hover:bg-gray-900' : '',
      highlighted ? 'bg-gray-100 dark:bg-gray-900' : ''
    ]"
  >
    <slot />
    <span
      v-if="$slots.actions"
      class="flex items-center gap-1"
      :class="revealActionsOnHover ? 'opacity-0 transition-opacity focus-within:opacity-100 group-hover:opacity-100' : ''"
    >
      <slot name="actions" />
    </span>
  </div>
</template>
