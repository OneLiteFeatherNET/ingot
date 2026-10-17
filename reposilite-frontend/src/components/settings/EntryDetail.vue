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
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import DeleteEntryDialog from './DeleteEntryDialog.vue'
import RepositoryAccessCard from './RepositoryAccessCard.vue'
import RepositoryMembersPicker from './RepositoryMembersPicker.vue'
import { entryLayout, idText, validateNewId } from './collections'
import { membersOf, proxiedOfMembers } from './groupMembers'
import {
  SETTING_TYPE_OPTIONS,
  hiddenFormFields,
  removalsFor,
  switchedEntry,
  unusedStorageNoteOf,
  violationsOf,
  warningsFor
} from '../../store/maven/repositoryTypes'

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
  // True for the repositories of the maven domain: the pane shows the type first, and the fields that type takes.
  typed: {
    type: Boolean,
    default: false
  },
  // The message the server gave when the settings were refused, shown next to the type it may be about.
  serverError: {
    type: String,
    default: ''
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

/*
 * The type of a repository. A repository without one behaves as before, and the choice that says so
 * is the empty value. Switching asks first when the new type would take settings away, and names them.
 */
const TYPE_HELP = {
  '': 'No type set. The repository behaves as before. Choose a type to enforce its rules.',
  HOSTED: 'Files are uploaded here and kept on storage.',
  PROXY: 'Files are fetched from remote repositories and cached in the cache storage. Nothing can be uploaded.',
  GROUP: 'Lists other repositories, in order. Nothing can be uploaded.'
}
const typeValue = computed(() => latest.value?.type ?? '')
const typeHelp = computed(() => TYPE_HELP[typeValue.value] ?? '')
const typeLabelOf = (type) => SETTING_TYPE_OPTIONS.find(option => option.value === type)?.label ?? ''
const keepLabel = computed(() => (typeValue.value === '' ? 'Keep it as before' : `Keep ${typeLabelOf(typeValue.value)}`))
// Shown under the type of a group that still has storage, which a group does not use.
const unusedStorageNote = computed(() => unusedStorageNoteOf(latest.value))
const pendingType = ref(null)
const typeNotice = ref('')
const pendingRemovals = computed(() => (pendingType.value === null
  ? []
  : removalsFor(latest.value, typeValue.value, pendingType.value)))
const pendingWarnings = computed(() => (pendingType.value === null
  ? []
  : warningsFor(latest.value, typeValue.value, pendingType.value)))
const confirmLabel = computed(() => (pendingRemovals.value.length > 0 ? 'Switch and remove' : 'Switch'))

const applyType = (type) => {
  const next = switchedEntry(latest.value, type)
  latest.value = next
  pendingType.value = null
  typeNotice.value = `Type set to ${typeLabelOf(type).toLowerCase() || 'not set'}.`
  emit('change', next)
}

// A switch that removes or leaves behind a setting is asked about first, and names it. Nothing is deleted silently.
const requestType = (type) => {
  if (type === typeValue.value) return
  if (removalsFor(latest.value, typeValue.value, type).length === 0 && warningsFor(latest.value, typeValue.value, type).length === 0) {
    applyType(type)
    return
  }
  pendingType.value = type
  typeNotice.value = ''
}

const confirmType = () => applyType(pendingType.value)
const cancelType = () => {
  pendingType.value = null
}

// The rules the type breaks, checked here so the problem is shown before the save. Only for the typed repositories.
const violations = computed(() => (props.typed ? violationsOf(latest.value, { otherIds: props.otherIds }) : []))

// A group's members are its mirrored entries read as repository ids, and are edited as a list.
const members = computed(() => membersOf(latest.value.proxied))
const onMembers = (ids) => {
  const next = { ...latest.value, proxied: proxiedOfMembers(ids) }
  latest.value = next
  emit('change', next)
}

// The fields the type does not take are left out of the form. The type itself is shown by the selector above it.
const withoutFields = (node, hidden) => {
  const elements = (node.elements ?? [])
    .filter(element => !(element.type === 'Control' && hidden.has(element.scope.replace('#/properties/', ''))))
    .map(element => (element.elements ? withoutFields(element, hidden) : element))
    .filter(element => !(element.type === 'Group' && element.elements.length === 0))
  return { ...node, elements }
}

// A proxy's storage is the storage its cache is kept in, so the form names it that way.
const labelled = (node, type) => ({
  ...node,
  elements: (node.elements ?? []).map(element => {
    if (element.elements) return labelled(element, type)
    if (type === 'PROXY' && element.type === 'Control' && element.scope === '#/properties/storageProvider') {
      return { ...element, label: 'Cache storage' }
    }
    return element
  })
})

const formLayout = computed(() => {
  const hidden = new Set([...hiddenFormFields(typeValue.value), ...(props.typed ? ['type'] : [])])
  return labelled(withoutFields(layout.value, hidden), typeValue.value)
})
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

    <section
      v-if="typed"
      data-test="repository-type"
      aria-labelledby="entry-type-title"
      class="flex flex-col gap-4"
    >
      <h4
        id="entry-type-title"
        class="md-title-medium text-on-surface"
      >
        Type
      </h4>
      <MdSelect
        id="entry-detail-type"
        :model-value="typeValue"
        label="Type"
        :options="SETTING_TYPE_OPTIONS"
        :supporting-text="typeHelp"
        @update:model-value="requestType"
      />
      <p
        v-if="unusedStorageNote"
        data-test="unused-storage"
        role="status"
        class="md-body-medium text-warning"
      >
        {{ unusedStorageNote }}
      </p>
      <p
        v-if="typeNotice"
        role="status"
        class="md-body-medium text-on-surface-variant"
      >
        {{ typeNotice }}
      </p>
      <div
        v-if="pendingType !== null"
        data-test="type-removals"
        role="group"
        aria-labelledby="entry-type-removals-title"
        class="flex flex-col gap-3 rounded-corner-medium bg-surface-container-high p-4"
      >
        <h5
          id="entry-type-removals-title"
          class="md-title-small text-on-surface"
        >
          Switching to {{ typeLabelOf(pendingType).toLowerCase() }}{{ pendingRemovals.length > 0 ? ' removes' : '' }}
        </h5>
        <ul
          v-if="pendingRemovals.length > 0"
          class="m-0 flex list-disc flex-col gap-1 pl-5 md-body-medium text-on-surface"
        >
          <li
            v-for="item in pendingRemovals"
            :key="item.key"
          >
            {{ item.label }}
          </li>
        </ul>
        <p
          v-for="warning in pendingWarnings"
          :key="warning"
          data-test="type-warning"
          class="md-body-medium text-warning"
        >
          {{ warning }}
        </p>
        <p
          v-if="pendingRemovals.length > 0"
          class="md-body-medium text-on-surface-variant"
        >
          They are removed from the repository when you save.
        </p>
        <div class="flex flex-wrap gap-2">
          <MdButton
            variant="filled"
            type="button"
            @click="confirmType"
          >
            {{ confirmLabel }}
          </MdButton>
          <MdButton
            variant="text"
            type="button"
            @click="cancelType"
          >
            {{ keepLabel }}
          </MdButton>
        </div>
      </div>
      <ul
        v-if="violations.length > 0"
        data-test="type-violations"
        role="alert"
        class="m-0 flex list-none flex-col gap-1 p-0 md-body-medium text-error"
      >
        <li
          v-for="message in violations"
          :key="message"
        >
          {{ message }}
        </li>
      </ul>
      <p
        v-if="serverError"
        data-test="server-violation"
        role="alert"
        class="md-body-medium text-error"
      >
        {{ serverError }}
      </p>
      <div
        v-if="typeValue === 'GROUP'"
        class="flex flex-col gap-3"
      >
        <h5 class="md-title-small text-on-surface">
          Members
        </h5>
        <RepositoryMembersPicker
          id-prefix="entry-members"
          :model-value="members"
          :candidates="otherIds"
          @update:model-value="onMembers"
        />
      </div>
    </section>

    <div data-test="entry-form">
      <!-- The form holds the latest value, so a change of the type is never answered with the value before it. -->
      <JsonForms
        :key="id"
        :data="latest"
        :schema="collection.itemSchema"
        :uischema="formLayout"
        :renderers="renderers"
        :ajv="validator"
        :config="formConfiguration"
        @change="onChange"
      />
    </div>

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
