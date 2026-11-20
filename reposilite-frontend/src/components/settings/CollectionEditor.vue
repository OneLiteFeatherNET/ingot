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
import { computed, provide, ref, watch } from 'vue'
import type { JsonFormsRendererRegistryEntry, createAjv } from '@jsonforms/core'
import MdStatePanel from '../md/MdStatePanel.vue'
import EntryList from './EntryList.vue'
import EntryDetail from './EntryDetail.vue'
import { entryAddresses, idText } from './collections'
import type { CollectionEntry, collectionsOf } from './collections'
import { tagsInUse } from '../../store/maven/repositories'
import type { Searchable } from '../../store/maven/repositories'
import { guidedCreateKey } from './guidedCreate'

/* A collection as collectionsOf lists it: its key, title, description, item schema and noun. */
type Collection = ReturnType<typeof collectionsOf>[number]
/* An entry as the list and the detail read it: its fields are free, and the tags are a list of names when present. */
type EditorEntry = CollectionEntry & Searchable & { tags?: string[] }
/* The ajv instance the generated forms validate with. */
type Ajv = ReturnType<typeof createAjv>

/*
 * One collection as a list-detail layout. From 840px the list and the detail sit side by side, the
 * list in a 340px column. Below that there is one pane at a time: the list while nothing is
 * selected, the detail once an entry is. The page frame names the open entry and carries the way back to the list.
 * The description of the collection belongs to the list, so it is not drawn beside an open entry.
 *
 * The address (selectedId) comes from the route. The selection itself is the stable key of the
 * entry, resolved from the address when the address changes. So while the user edits the id of the
 * selected entry, the entry stays selected, even when the address still names the old id; the
 * settings view moves the address once the new id is usable.
 *
 * The component does not change the configuration itself. It reports the selection, creation,
 * edits and deletion, and the settings view decides how each one is stored and addressed.
 */
const props = withDefaults(defineProps<{
  collection: Collection
  entries: EditorEntry[]
  // The stable key of each entry, by position. See the store's entryKeysOf.
  keys?: string[]
  // The address in the route: an entry's id, or "~" and its key while it has no usable id.
  selectedId?: string | null
  renderers: JsonFormsRendererRegistryEntry[]
  validator: Ajv
  // True for the repositories: the detail then shows who has access to the selected one.
  showAccess?: boolean
  // True for the repositories: the detail then shows the type of the repository and the fields it takes.
  typed?: boolean
  // The message of a refused save, which the detail shows next to the type.
  serverError?: string
  // A guided flow that replaces the id dialog of "New entry", or null for the plain dialog. See guidedCreate.ts.
  guidedCreate?: (() => void) | null
}>(), {
  keys: () => [],
  selectedId: null,
  showAccess: false,
  typed: false,
  serverError: '',
  guidedCreate: null
})

const emit = defineEmits<{
  select: [address: string]
  create: [id: string]
  change: [change: { index: number; entry: CollectionEntry }]
  delete: [index: number]
}>()

provide(guidedCreateKey, computed(() => props.guidedCreate))

const selectedKey = ref<string | null>(null)
const addresses = computed(() => entryAddresses(props.entries, props.keys))
const selectedIndex = computed(() => (selectedKey.value === null ? -1 : props.keys.indexOf(selectedKey.value)))
const selectedEntry = computed(() => (selectedIndex.value >= 0 ? props.entries[selectedIndex.value] : null))
const hasSelection = computed(() => props.selectedId !== null)
const otherEntries = computed(() => props.entries.filter((_, index) => index !== selectedIndex.value))
const otherIds = computed(() => otherEntries.value.map(idText))
const otherTags = computed(() => tagsInUse(otherEntries.value))

const resolveSelection = () => {
  if (props.selectedId === null) {
    selectedKey.value = null
    return
  }
  const index = addresses.value.indexOf(props.selectedId)
  selectedKey.value = index >= 0 ? props.keys[index] : null
}

// A new address is resolved at once. The entries may arrive after the address, and an entry may
// go away, so the address is resolved again while the selected entry cannot be found.
watch(() => props.selectedId, resolveSelection, { immediate: true })
watch([addresses, () => props.keys], () => {
  if (selectedIndex.value < 0) resolveSelection()
})
</script>

<template>
  <section
    :aria-labelledby="`${collection.key}-title`"
    class="flex flex-col gap-4"
    data-ingot-component="collection-editor"
  >
    <header class="flex flex-col gap-1">
      <h2
        :id="`${collection.key}-title`"
        class="md-title-large text-on-surface"
      >
        {{ collection.title }}
      </h2>
      <p
        v-if="collection.description && !hasSelection"
        class="md-body-medium text-on-surface-variant"
      >
        {{ collection.description }}
      </p>
    </header>

    <!-- Below 840px the list and the detail take the full content width: the outer card, its border and its padding go. -->
    <div class="grid overflow-hidden expanded:rounded-corner-large expanded:border expanded:border-outline-variant expanded:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
      <div
        class="min-w-0 expanded:p-3 expanded:border-r expanded:border-outline-variant"
        :class="hasSelection ? 'max-expanded:hidden' : ''"
      >
        <EntryList
          :entries="entries"
          :keys="keys"
          :selected-key="selectedKey"
          :item-schema="collection.itemSchema"
          :collection-title="collection.title"
          :noun="collection.noun"
          @select="emit('select', $event)"
          @create="emit('create', $event)"
        />
      </div>

      <div class="min-w-0 p-4 expanded:p-6">
        <EntryDetail
          v-if="selectedId !== null"
          :id="selectedId"
          :key="selectedKey ?? `missing:${selectedId}`"
          :collection="collection"
          :entry="selectedEntry"
          :other-ids="otherIds"
          :other-tags="otherTags"
          :renderers="renderers"
          :validator="validator"
          :show-access="showAccess"
          :typed="typed"
          :server-error="serverError"
          @change="emit('change', { index: selectedIndex, entry: $event })"
          @delete="emit('delete', selectedIndex)"
        />
        <MdStatePanel
          v-else
          data-test="no-selection"
          state="empty"
          :description="`Select a ${collection.noun} to edit its settings.`"
          class="max-expanded:hidden"
        />
      </div>
    </div>
  </section>
</template>
