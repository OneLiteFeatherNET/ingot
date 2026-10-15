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
import { computed } from 'vue'
import EntryList from './EntryList.vue'
import EntryDetail from './EntryDetail.vue'

/*
 * One collection as a list-detail layout. From 840px the list and the detail sit side by side, the
 * list in a 340px column. Below that there is one pane at a time: the list while nothing is
 * selected, the detail once an entry is, with a back button to return to the list.
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
  selectedId: {
    type: String,
    default: null
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

const selectedIndex = computed(() => (props.selectedId === null
  ? -1
  : props.entries.findIndex(entry => String(entry?.id) === props.selectedId)))
const selectedEntry = computed(() => (selectedIndex.value >= 0 ? props.entries[selectedIndex.value] : null))
const hasSelection = computed(() => props.selectedId !== null)
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

    <div class="grid overflow-hidden rounded-corner-large border border-outline-variant min-[840px]:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
      <div
        class="min-w-0 p-3 min-[840px]:border-r min-[840px]:border-outline-variant"
        :class="hasSelection ? 'max-[839px]:hidden' : ''"
      >
        <EntryList
          :entries="entries"
          :selected-id="selectedId"
          :item-schema="collection.itemSchema"
          :collection-title="collection.title"
          :noun="collection.noun"
          @select="emit('select', $event)"
          @create="emit('create', $event)"
        />
      </div>

      <div
        class="min-w-0 p-4 md:p-6"
        :class="hasSelection ? '' : 'max-[839px]:hidden'"
      >
        <EntryDetail
          v-if="hasSelection"
          :id="selectedId"
          :key="selectedId"
          :collection="collection"
          :entry="selectedEntry"
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
