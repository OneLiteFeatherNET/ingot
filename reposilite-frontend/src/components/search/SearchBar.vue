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
/*
 * The M3 search bar: a search landmark with a 56px pill on the raised surface and no outline. The
 * text is the value of the component; the default slot sits under the pill, where a page puts its
 * filter chips. Shared by the repository overview and the search page.
 */
defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, required: true }
})

const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <form
    role="search"
    class="flex flex-col gap-3"
    @submit.prevent
  >
    <div class="flex h-14 items-center gap-2 rounded-corner-full bg-surface-container-high pl-4 pr-2 text-on-surface has-[input:focus-visible]:outline-3 has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-secondary">
      <svg
        class="size-6 shrink-0 text-on-surface-variant"
        viewBox="0 0 24 24"
        fill="currentColor"
        aria-hidden="true"
      >
        <path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1 0 9.5 16a6.5 6.5 0 0 0 4.2-1.6l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z" />
      </svg>
      <input
        type="search"
        class="md-embedded-input min-w-0 flex-1 md-body-large text-on-surface placeholder:text-on-surface-variant [&::-webkit-search-cancel-button]:hidden"
        :aria-label="label"
        :placeholder="label"
        autocomplete="off"
        :value="modelValue"
        @input="emit('update:modelValue', $event.target.value)"
      >
      <button
        v-if="modelValue !== ''"
        type="button"
        class="md-state-layer md-focus-ring md-touch-target relative inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant"
        aria-label="Clear search"
        @click="emit('update:modelValue', '')"
      >
        <svg
          class="size-6"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
          focusable="false"
        >
          <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
        </svg>
      </button>
    </div>
    <slot />
  </form>
</template>
