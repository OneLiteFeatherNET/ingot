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
  /**
   * Both the accessible name and the tooltip. Name the object as well as the verb, since a
   * screen reader reads these buttons one after another with no row context around them.
   */
  label: {
    type: String,
    required: true
  },
  /** Marks an action that destroys something a user cannot get back. */
  destructive: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!--
    The destructive button carries a border of its own at rest, so it is told apart from the
    two harmless ones by shape, not by the red that only arrives on hover. `[&>svg]` sizes
    the icon from here: the icon components ship `w-6 h-6`, and a class passed to them would
    lose to it on source order alone.
  -->
  <button
    type="button"
    :aria-label="label"
    :title="label"
    class="inline-flex h-7 w-7 items-center justify-center rounded-md ring-1 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 [&>svg]:size-4"
    :class="destructive
      ? 'text-gray-600 ring-gray-300 hover:bg-red-50 hover:text-red-600 hover:ring-red-400 dark:text-gray-300 dark:ring-gray-700 dark:hover:bg-red-900/40 dark:hover:text-red-400 dark:hover:ring-red-700'
      : 'text-gray-500 ring-transparent hover:bg-gray-150 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100'"
  >
    <slot />
  </button>
</template>
