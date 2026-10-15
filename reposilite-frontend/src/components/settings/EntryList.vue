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
import { computed, ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import NewEntryDialog from './NewEntryDialog.vue'
import { filterEntries, summaryOf } from './collections'

/*
 * The list pane of a collection: a search field over the ids, the action that adds an entry, and
 * one 56px item per entry. The item shows the id as its headline and a short line built from the
 * schema's text values. The selected item takes the secondary-container role.
 */
const props = defineProps({
  entries: {
    type: Array,
    required: true
  },
  selectedId: {
    type: String,
    default: null
  },
  itemSchema: {
    type: Object,
    required: true
  },
  collectionTitle: {
    type: String,
    required: true
  },
  noun: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['select', 'create'])

const query = ref('')
const dialogOpen = ref(false)

const items = computed(() => filterEntries(props.entries, query.value).map(item => ({
  ...item,
  summary: summaryOf(props.itemSchema, item.entry)
})))

const existingIds = computed(() => props.entries.map(entry => String(entry?.id ?? '')))

const emptyText = computed(() => {
  if (props.entries.length === 0) return `No ${props.collectionTitle.toLowerCase()} yet.`
  return `No ${props.noun} matches "${query.value.trim()}".`
})

const isSelected = (item) => item.id === props.selectedId
</script>

<template>
  <div class="flex flex-col gap-3">
    <MdButton
      data-test="new-entry"
      variant="tonal"
      type="button"
      class="self-start"
      @click="dialogOpen = true"
    >
      <template #icon>
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
      </template>
      New {{ noun }}
    </MdButton>

    <MdTextField
      :model-value="query"
      label="Search by id"
      variant="outlined"
      autocomplete="off"
      @update:model-value="query = $event"
    >
      <template #leading-icon>
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
        >
          <path d="M15.5 14h-.8l-.3-.3A6.5 6.5 0 1 0 9.5 16a6.5 6.5 0 0 0 4.2-1.6l.3.3v.8l5 5 1.5-1.5-5-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z" />
        </svg>
      </template>
    </MdTextField>

    <ul
      v-if="items.length > 0"
      class="flex flex-col gap-1"
    >
      <li
        v-for="item in items"
        :key="item.id"
      >
        <button
          type="button"
          class="md-state-layer md-focus-ring relative flex min-h-14 w-full flex-col justify-center rounded-corner-large px-4 py-2 text-left"
          :class="isSelected(item) ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
          :aria-current="isSelected(item) ? 'true' : undefined"
          @click="emit('select', item.id)"
        >
          <span class="md-title-small [overflow-wrap:anywhere]">{{ item.id }}</span>
          <span
            v-if="item.summary"
            class="md-body-medium"
            :class="isSelected(item) ? 'text-on-secondary-container' : 'text-on-surface-variant'"
          >
            {{ item.summary }}
          </span>
        </button>
      </li>
    </ul>
    <p
      v-else
      class="px-4 py-6 md-body-medium text-on-surface-variant"
    >
      {{ emptyText }}
    </p>

    <NewEntryDialog
      v-model="dialogOpen"
      :noun="noun"
      :existing-ids="existingIds"
      @create="emit('create', $event)"
    />
  </div>
</template>
