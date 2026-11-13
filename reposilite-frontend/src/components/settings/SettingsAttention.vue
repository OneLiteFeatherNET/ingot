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
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'

/*
 * Needs attention on the Overview page of /settings: one row per settings page with an unsaved draft, a control that opens
 * the review of all drafts, and a row for a save conflict. With none of these it reads "Nothing needs attention.". The status
 * is text with an icon, never colour alone. The frame draws it above the index below 1240 pixels, and beside the page wider.
 *
 * Props:
 *   rows      [{ key, label, to }], the tree entries with an unsaved draft (attentionRows builds them)
 *   conflict  the save conflict of the store, or null
 *
 * Events: review (opens the review of all drafts), review-conflict (opens the review of the stored settings)
 */
defineProps({
  rows: { type: Array, required: true },
  conflict: { type: Object, default: null }
})

defineEmits(['review', 'review-conflict'])
</script>

<template>
  <section
    aria-labelledby="settings-attention-title"
    class="flex flex-col gap-3"
    data-test="settings-attention"
    data-ingot-component="settings-attention"
  >
    <h2
      id="settings-attention-title"
      class="md-title-medium text-on-surface"
    >
      Needs attention
    </h2>

    <p
      v-if="rows.length === 0 && !conflict"
      class="md-body-medium text-on-surface-variant"
    >
      Nothing needs attention.
    </p>

    <ul
      v-else
      class="flex list-none flex-col gap-2 p-0"
    >
      <li
        v-for="row in rows"
        :key="row.key"
      >
        <RouterLink
          :to="row.to"
          :aria-label="`${row.label}, Unsaved changes`"
          class="md-state-layer md-focus-ring relative flex min-h-12 items-center justify-between gap-3 rounded-corner-small px-3 py-2 no-underline text-on-surface"
          data-test="attention-row"
        >
          <span class="md-body-large">{{ row.label }}</span>
          <span class="inline-flex shrink-0 items-center gap-1 md-label-medium text-primary">
            <svg
              class="size-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <circle
                cx="12"
                cy="12"
                r="4"
                fill="currentColor"
              />
            </svg>
            Unsaved changes
          </span>
        </RouterLink>
      </li>

      <li
        v-if="conflict"
        class="flex flex-wrap items-center justify-between gap-3 rounded-corner-small px-3 py-2"
        data-test="attention-conflict"
      >
        <span class="md-body-large text-error">{{ conflict.title }} changed elsewhere</span>
        <MdButton
          variant="text"
          type="button"
          @click="$emit('review-conflict')"
        >
          Review conflict
        </MdButton>
      </li>
    </ul>

    <div v-if="rows.length > 0">
      <MdButton
        variant="text"
        type="button"
        @click="$emit('review')"
      >
        Review changes
      </MdButton>
    </div>
  </section>
</template>
