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
import { computed, ref, watch } from 'vue'
import EntryList from './EntryList.vue'
import EntryDetail from './EntryDetail.vue'
import { entryAddresses, idText } from './collections'
import { tagsInUse } from '../../store/maven/repositories'

/*
 * One collection as a list-detail layout. From 840px the list and the detail sit side by side, the
 * list in a 340px column. Below that there is one pane at a time: the list while nothing is
 * selected, the detail once an entry is, with a back button to return to the list.
 *
 * The address (selectedId) comes from the route. The selection itself is the stable key of the
 * entry, resolved from the address when the address changes. So while the user edits the id of the
 * selected entry, the entry stays selected, even when the address still names the old id; the
 * settings view moves the address once the new id is usable.
 *
 * The component does not change the configuration itself. It reports the selection, creation,
 * edits and deletion, and the settings view decides how each one is stored and addressed.
 */
const props = defineProps({
  collection: {
    type: Object,
    required: true
  },
  entries: {
    type: Array,
    required: true
  },
  // The stable key of each entry, by position. See the store's entryKeysOf.
  keys: {
    type: Array,
    default: () => []
  },
  // The address in the route: an entry's id, or "~" and its key while it has no usable id.
  selectedId: {
    type: String,
    default: null
  },
  backHref: {
    type: String,
    default: undefined
  },
  renderers: {
    type: Array,
    required: true
  },
  validator: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['select', 'create', 'change', 'delete', 'back'])

const selectedKey = ref(null)
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
  >
    <header class="flex flex-col gap-1">
      <h2
        :id="`${collection.key}-title`"
        class="md-title-large text-on-surface"
      >
        {{ collection.title }}
      </h2>
      <p
        v-if="collection.description"
        class="md-body-medium text-on-surface-variant"
      >
        {{ collection.description }}
      </p>
    </header>

    <!-- Below 840px the list and the detail take the full content width: the outer card, its border and its padding go. -->
    <div class="grid overflow-hidden min-[840px]:rounded-corner-large min-[840px]:border min-[840px]:border-outline-variant min-[840px]:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
      <div
        class="min-w-0 min-[840px]:p-3 min-[840px]:border-r min-[840px]:border-outline-variant"
        :class="hasSelection ? 'max-[839px]:hidden' : ''"
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

      <div
        class="min-w-0 min-[840px]:p-6"
        :class="hasSelection ? '' : 'max-[839px]:hidden'"
      >
        <EntryDetail
          v-if="hasSelection"
          :id="selectedId"
          :key="selectedKey ?? `missing:${selectedId}`"
          :collection="collection"
          :entry="selectedEntry"
          :other-ids="otherIds"
          :other-tags="otherTags"
          :back-href="backHref"
          :renderers="renderers"
          :validator="validator"
          @change="emit('change', { index: selectedIndex, entry: $event })"
          @delete="emit('delete', selectedIndex)"
          @back="emit('back')"
        />
        <p
          v-else
          class="md-body-large text-on-surface-variant"
        >
          Select a {{ collection.noun }} to edit its settings.
        </p>
      </div>
    </div>
  </section>
</template>
