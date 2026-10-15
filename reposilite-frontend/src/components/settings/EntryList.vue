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
import MdChip from '../md/MdChip.vue'
import MdTextField from '../md/MdTextField.vue'
import NewEntryDialog from './NewEntryDialog.vue'
import { entryAddresses, filterEntries, idText, summaryOf, validateNewId } from './collections'

/*
 * The list pane of a collection: a search field over the ids, descriptions and tags, the action that
 * adds an entry, and one item per entry. Each item is keyed by the entry's stable key, not by its id,
 * so an edit to the id moves nothing else. An entry without a usable id is still listed: it reads
 * "Unnamed", and its supporting line says what is wrong. The item is opened by its address, which is
 * the id or, while there is no usable id, the key. The selected item takes the secondary-container role.
 * The tags of an entry are assist chips under its id; pressing one searches for that tag.
 */
const props = defineProps({
  entries: {
    type: Array,
    required: true
  },
  keys: {
    type: Array,
    default: () => []
  },
  selectedKey: {
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

const items = computed(() => {
  const addresses = entryAddresses(props.entries, props.keys)
  const ids = props.entries.map(idText)
  return filterEntries(props.entries, query.value).map((item) => {
    const problem = validateNewId(item.id, ids.filter((_, other) => other !== item.index), props.noun)
    return {
      key: props.keys[item.index] ?? String(item.index),
      address: addresses[item.index],
      title: item.id !== '' ? item.id : `Unnamed ${props.noun}`,
      unnamed: item.id === '',
      problem,
      summary: problem ? '' : summaryOf(props.itemSchema, item.entry),
      tags: Array.isArray(item.entry?.tags) ? item.entry.tags.filter(tag => typeof tag === 'string') : []
    }
  })
})

const existingIds = computed(() => props.entries.map(idText))

// A tag chip narrows the list to that tag, the same way typing `tag:name` does.
const filterBy = (tag) => {
  query.value = `tag:${tag}`
}

const emptyText = computed(() => {
  if (props.entries.length === 0) return `No ${props.collectionTitle.toLowerCase()} yet.`
  return `No ${props.noun} matches "${query.value.trim()}".`
})

const isSelected = (item) => item.key === props.selectedKey
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
      label="Search by id, description or tag"
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
        :key="item.key"
        class="flex flex-col"
      >
        <button
          type="button"
          class="md-state-layer md-focus-ring relative flex min-h-14 w-full flex-col justify-center rounded-corner-large px-4 py-2 text-left"
          :class="isSelected(item) ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
          :aria-current="isSelected(item) ? 'true' : undefined"
          @click="emit('select', item.address)"
        >
          <span
            class="md-title-small [overflow-wrap:anywhere]"
            :class="item.unnamed ? 'italic' : ''"
          >{{ item.title }}</span>
          <span
            v-if="item.problem"
            class="md-body-medium text-error"
          >
            {{ item.problem }}
          </span>
          <span
            v-else-if="item.summary"
            class="line-clamp-2 md-body-medium"
            :class="isSelected(item) ? 'text-on-secondary-container' : 'text-on-surface-variant'"
          >
            {{ item.summary }}
          </span>
        </button>
        <div
          v-if="item.tags.length > 0"
          class="flex flex-wrap gap-1 px-4 pb-2"
        >
          <MdChip
            v-for="tag in item.tags"
            :key="tag"
            data-test="tag-chip"
            :aria-label="`Show repositories tagged ${tag}`"
            @click="filterBy(tag)"
          >
            {{ tag }}
          </MdChip>
        </div>
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
