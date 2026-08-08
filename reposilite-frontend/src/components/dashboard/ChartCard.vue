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
  title: {
    type: String,
    required: true
  },
  /** Names the unit and the window, so the axis does not have to repeat either. */
  subtitle: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  /** Shown instead of the plot when there is genuinely nothing to draw. */
  emptyMessage: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <section class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
    <header class="pb-3">
      <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-50">{{ title }}</h2>
      <p v-if="subtitle" class="text-xs text-gray-500 dark:text-gray-400">{{ subtitle }}</p>
    </header>

    <div v-if="loading" class="skeleton-bars h-[220px] w-full rounded bg-gray-100 dark:bg-gray-800" />

    <!--
      An empty state says which switch produces the emptiness. A blank rectangle leaves the
      reader deciding between "nothing happened" and "this is broken".
    -->
    <p
      v-else-if="emptyMessage"
      class="flex h-[220px] items-center justify-center px-6 text-center text-xs text-gray-500 dark:text-gray-400"
    >
      {{ emptyMessage }}
    </p>

    <slot v-else />
  </section>
</template>
