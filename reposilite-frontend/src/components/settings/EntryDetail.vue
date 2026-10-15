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
import { JsonForms } from '@jsonforms/vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DeleteEntryDialog from './DeleteEntryDialog.vue'
import RepositoryAccessCard from './RepositoryAccessCard.vue'
import { entryLayout, idText, validateNewId } from './collections'

/*
 * The detail pane of one entry. The id is edited in its own field, checked the way the new-entry
 * dialog checks it: the field shows the problem, and the entry keeps the typed value either way, so
 * the list shows the problem too and the save is blocked until it is fixed. The rest of the form is
 * generated from the item schema, so the entry is edited with the same renderers as the rest of
 * the settings. A null entry means the id in the address names nothing, and the pane says so.
 */
const props = defineProps({
  collection: {
    type: Object,
    required: true
  },
  // The address in the route, which is the id of the entry, or the key marker when it has none.
  id: {
    type: String,
    required: true
  },
  entry: {
    type: Object,
    default: null
  },
  // The ids of the other entries of the collection, which the id of this one must not repeat.
  otherIds: {
    type: Array,
    default: () => []
  },
  // The tags the other entries use, which the tags field offers as suggestions.
  otherTags: {
    type: Array,
    default: () => []
  },
  // Where the not-found pane leads: the list of the domain. Given as a link, so it can be opened
  // in a new tab; the click itself is handled by the page and goes through the router.
  backHref: {
    type: String,
    default: undefined
  },
  // True for the repositories of the maven domain: the pane then shows who has access, below the form.
  showAccess: {
    type: Boolean,
    default: false
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

const emit = defineEmits(['change', 'delete', 'back'])

const noun = computed(() => props.collection.noun)
const layout = computed(() => entryLayout(props.collection.itemSchema))
const backLabel = computed(() => `Back to ${props.collection.title.toLowerCase()}`)
const headline = computed(() => idText(props.entry) || `Unnamed ${noun.value}`)
const deleteOpen = ref(false)

// The latest value of the whole entry, so an id typed in the field is written next to the fields the form has changed.
const latest = ref(props.entry)
watch(() => props.entry, (entry) => {
  latest.value = entry
})

const idDraft = ref(idText(props.entry))
watch(() => idText(props.entry), (id) => {
  idDraft.value = id
})

const idError = computed(() => validateNewId(idDraft.value, props.otherIds, noun.value))

const onIdInput = (value) => {
  idDraft.value = value
  latest.value = { ...latest.value, id: value }
  emit('change', latest.value)
}

// The tags of the other entries reach the tags field through the form configuration, the one place
// a renderer can read context from that is not its own data.
const formConfiguration = computed(() => ({ showUnfocusedDescription: true, tagSuggestions: props.otherTags }))
const onChange = (event) => {
  latest.value = event.data
  emit('change', event.data)
}
</script>

<template>
  <div
    v-if="entry === null"
    class="flex flex-col items-start gap-4"
  >
    <h3 class="md-headline-small text-on-surface">
      Repository not found
    </h3>
    <p class="md-body-large text-on-surface-variant">
      There is no {{ collection.noun }} with the id "{{ id }}" in this configuration.
    </p>
    <MdButton
      variant="text"
      type="button"
      :href="backHref"
      @click.prevent="emit('back')"
    >
      {{ backLabel }}
    </MdButton>
  </div>

  <div
    v-else
    class="flex flex-col gap-6"
  >
    <header class="flex items-center gap-2">
      <MdIconButton
        class="min-[840px]:hidden"
        :aria-label="backLabel"
        @click="emit('back')"
      >
        <svg
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z" />
        </svg>
      </MdIconButton>
      <h3
        data-test="entry-title"
        class="min-w-0 break-all md-headline-small text-on-surface"
        :class="idText(entry) ? '' : 'italic'"
      >
        {{ headline }}
      </h3>
    </header>

    <MdTextField
      id="entry-detail-id"
      label="Id"
      variant="outlined"
      autocomplete="off"
      required
      :model-value="idDraft"
      supporting-text="Letters, digits, dots, underscores and hyphens. The address follows the id."
      :error="Boolean(idError)"
      :error-text="idError"
      @update:model-value="onIdInput"
    />

    <JsonForms
      :key="id"
      :data="entry"
      :schema="collection.itemSchema"
      :uischema="layout"
      :renderers="renderers"
      :ajv="validator"
      :config="formConfiguration"
      @change="onChange"
    />

    <RepositoryAccessCard
      v-if="showAccess && idText(entry)"
      :repository="idText(entry)"
    />

    <section
      data-test="danger-zone"
      aria-labelledby="entry-danger-title"
      class="flex flex-col gap-3 rounded-corner-medium border border-error p-4"
    >
      <h4
        id="entry-danger-title"
        class="md-title-medium text-error"
      >
        Danger zone
      </h4>
      <p class="md-body-medium text-on-surface-variant">
        Deleting removes this {{ collection.noun }} from the configuration when you save.
      </p>
      <div>
        <MdButton
          variant="outlined"
          error
          type="button"
          @click="deleteOpen = true"
        >
          <template #icon>
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
            </svg>
          </template>
          Delete {{ collection.noun }}
        </MdButton>
      </div>
    </section>

    <DeleteEntryDialog
      :id="idText(entry) || 'unnamed'"
      v-model="deleteOpen"
      @confirm="emit('delete')"
    />
  </div>
</template>
