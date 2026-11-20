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

<script setup lang="ts">
import { computed, ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'
import MdTextField from '../md/MdTextField.vue'
import MdStatePanel from '../md/MdStatePanel.vue'
import NewEntryDialog from './NewEntryDialog.vue'
import { entryAddresses, filterEntries, idText, summaryOf, validateNewId } from './collections'
import type { CollectionEntry, SettingsSchema } from './collections'
import type { Searchable } from '../../store/maven/repositories'

/*
 * The list pane of a collection: a search field over the ids, descriptions and tags, the action that
 * adds an entry, and one item per entry. Each item is keyed by the entry's stable key, not by its id,
 * so an edit to the id moves nothing else. An entry without a usable id is still listed: it reads
 * "Unnamed", and its supporting line says what is wrong. The item is opened by its address, which is
 * the id or, while there is no usable id, the key. The selected item takes the secondary-container role.
 * The tags of an entry are assist chips under its id; pressing one searches for that tag.
 */

const props = withDefaults(defineProps<{
  entries: ListEntry[]
  keys?: string[]
  selectedKey?: string | null
  itemSchema: SettingsSchema
  collectionTitle: string
  noun: string
}>(), {
  keys: () => [],
  selectedKey: null
})

const emit = defineEmits<{
  select: [address: string]
  create: [id: string]
}>()

/* An entry as the list reads it: its fields are free, and the id, description and tags are searched when present. */
type ListEntry = CollectionEntry & Searchable

interface ListItem {
  key: string
  address: string
  title: string
  unnamed: boolean
  problem: string
  summary: string
  tags: string[]
}

const tagsOf = (entry: ListEntry | undefined): string[] => {
  const tags = entry?.tags
  return Array.isArray(tags) ? tags.filter((tag): tag is string => typeof tag === 'string') : []
}

const query = ref('')
const dialogOpen = ref(false)

const items = computed((): ListItem[] => {
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
      tags: tagsOf(item.entry)
    }
  })
})

const existingIds = computed(() => props.entries.map(idText))

// A tag chip narrows the list to that tag, the same way typing `tag:name` does.
const filterBy = (tag: string) => {
  query.value = `tag:${tag}`
}

const emptyText = computed(() => {
  if (props.entries.length === 0) return `No ${props.collectionTitle.toLowerCase()} yet.`
  return `No ${props.noun} matches "${query.value.trim()}".`
})

const isSelected = (item: ListItem) => item.key === props.selectedKey
</script>

<template>
  <div
    class="flex flex-col gap-3"
    data-ingot-component="entry-list"
  >
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
      <!--
        The item is one block: the selection colour, the state layer and the tag chips all belong to the
        <li>. The button inside it holds the id and the supporting line, and is the part that selects.
      -->
      <li
        v-for="item in items"
        :key="item.key"
        class="md-state-layer rounded-corner-large"
        :class="isSelected(item) ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
      >
        <button
          type="button"
          class="md-focus-ring-inset relative flex w-full min-w-0 flex-col gap-1 rounded-corner-large px-4 pt-3 text-left"
          :class="item.tags.length > 0 ? 'pb-0' : 'pb-3'"
          :aria-current="isSelected(item) ? 'true' : undefined"
          @click="emit('select', item.address)"
        >
          <span
            class="md-title-small break-all"
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
            :title="item.summary"
            class="block truncate md-body-medium"
            :class="isSelected(item) ? 'text-on-secondary-container' : 'text-on-surface-variant'"
          >
            {{ item.summary }}
          </span>
        </button>
        <div
          v-if="item.tags.length > 0"
          class="flex flex-wrap gap-2 px-4 pb-3 pt-2"
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
    <MdStatePanel
      v-else
      data-test="empty"
      state="empty"
      :description="emptyText"
    />

    <NewEntryDialog
      v-model="dialogOpen"
      :noun="noun"
      :existing-ids="existingIds"
      @create="emit('create', $event)"
    />
  </div>
</template>
