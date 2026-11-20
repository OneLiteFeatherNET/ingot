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
import { computed, ref, watch } from 'vue'
import type { JsonFormsRendererRegistryEntry, UISchemaElement, createAjv } from '@jsonforms/core'
import { JsonForms } from '@jsonforms/vue'
import MdButton from '../md/MdButton.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import DeleteEntryDialog from './DeleteEntryDialog.vue'
import RepositoryAccessCard from './RepositoryAccessCard.vue'
import RepositoryMembersPicker from './RepositoryMembersPicker.vue'
import { entryLayout, idText, validateNewId } from './collections'
import type { SettingsSchema } from './collections'
import { nestedUischemas } from './nestedLayouts'
import { unavailableStorageTypeOf } from './repositoryWizard'
import {
  SETTING_TYPE_OPTIONS,
  blankEntriesNotice,
  blankEntriesRemovedBy,
  hiddenFormFields,
  removalsFor,
  switchedEntry,
  unusedStorageNoteOf,
  violationsOf,
  warningsFor
} from '../../store/maven/repositoryTypes'
import type { Entry, Mirror } from '../../store/maven/repositoryTypes'

/* The parts of a collection the pane reads: the noun of one entry and the schema its form is generated from. */
interface DetailCollection {
  noun: string
  itemSchema: SettingsSchema
}
/* The ajv instance the generated forms validate with. */
type Ajv = ReturnType<typeof createAjv>
/* A node of the generated form layout: a control names its field in `scope`, a layout or group holds `elements`. */
type LayoutNode = UISchemaElement & { elements?: LayoutNode[]; scope?: string }

/*
 * The detail pane of one entry. The id is edited in its own field, checked the way the new-entry
 * dialog checks it: the field shows the problem, and the entry keeps the typed value either way, so
 * the list shows the problem too and the save is blocked until it is fixed. The rest of the form is
 * generated from the item schema, so the entry is edited with the same renderers as the rest of
 * the settings. A null entry means the id in the address names nothing, and the pane says so.
 */
const props = withDefaults(defineProps<{
  collection: DetailCollection
  // The address in the route, which is the id of the entry, or the key marker when it has none.
  id: string
  // Null when the address names no entry; the pane then says so instead of showing a form.
  entry?: Entry | null
  // The ids of the other entries of the collection, which the id of this one must not repeat.
  otherIds?: string[]
  // The tags the other entries use, which the tags field offers as suggestions.
  otherTags?: string[]
  // True for the repositories of the maven domain: the pane then shows who has access, below the form.
  showAccess?: boolean
  // True for the repositories of the maven domain: the pane shows the type first, and the fields that type takes.
  typed?: boolean
  // The message the server gave when the settings were refused, shown next to the type it may be about.
  serverError?: string
  renderers: JsonFormsRendererRegistryEntry[]
  validator: Ajv
  // Fields of the entry that the page edits with its own control, so the generated form leaves them out.
  omit?: string[]
  // The groups that list this entry as a member, named by the delete confirmation before it can be used.
  references?: unknown[]
  // The part of a page this pane draws. Empty draws every part, as before. The repository page draws each part in
  // its own pane: 'type' (the type section), 'danger' (the danger zone), or the key of a tab, which draws the Id
  // field on General, the access card on Access and the form of the fields the tab names.
  section?: string
  // The fields of the form in the order they are drawn, when the page names one (the fields of a tab). Null keeps the order of the schema.
  order?: string[] | null
}>(), {
  entry: null,
  otherIds: () => [],
  otherTags: () => [],
  showAccess: false,
  typed: false,
  serverError: '',
  omit: () => [],
  references: () => [],
  section: '',
  order: null
})

const emit = defineEmits<{
  change: [entry: Entry]
  delete: []
}>()

const noun = computed(() => props.collection.noun)
const whole = computed(() => props.section === '')
const shows = (name: string) => whole.value || props.section === name
const drawsForm = computed(() => whole.value || (props.section !== 'type' && props.section !== 'danger'))
const layout = computed(() => entryLayout(props.collection.itemSchema, props.order ? { order: props.order } : {}))
const deleteOpen = ref(false)

// The latest value of the whole entry, so an id typed in the field is written next to the fields the form has changed.
const latest = ref<Entry | null>(props.entry)
watch(() => props.entry, (entry) => {
  latest.value = entry
})
// The latest value read by the type section, the members and the form. They are drawn only while there is an entry.
const current = (): Entry => latest.value ?? {}

const idDraft = ref(idText(props.entry))
watch(() => idText(props.entry), (id) => {
  idDraft.value = id
})

const idError = computed(() => validateNewId(idDraft.value, props.otherIds, noun.value))

const onIdInput = (value: string) => {
  idDraft.value = value
  latest.value = { ...latest.value, id: value }
  emit('change', latest.value)
}

// The tags of the other entries reach the tags field through the form configuration, the one place
// a renderer can read context from that is not its own data.
const formConfiguration = computed(() => ({ showUnfocusedDescription: true, tagSuggestions: props.otherTags }))
const onChange = (event: { data: Entry }) => {
  latest.value = event.data
  emit('change', event.data)
}

/*
 * The type of a repository. A repository without one behaves as before, and the choice that says so
 * is the empty value. Switching asks first when the new type would take settings away, and names them.
 */
const TYPE_HELP: Record<string, string> = {
  '': 'No type set. The repository behaves as before. Choose a type to enforce its rules.',
  HOSTED: 'Files are uploaded here and kept on storage.',
  PROXY: 'Files are fetched from remote repositories and cached in the cache storage. Nothing can be uploaded.',
  GROUP: 'Lists other repositories, in order. Nothing can be uploaded.'
}
// The schema offers the type as a string.
const typeValue = computed(() => (latest.value?.type ?? '') as string)
const typeHelp = computed(() => TYPE_HELP[typeValue.value] ?? '')
const typeLabelOf = (type: string) => SETTING_TYPE_OPTIONS.find(option => option.value === type)?.label ?? ''
const keepLabel = computed(() => (typeValue.value === '' ? 'Keep it as before' : `Keep ${typeLabelOf(typeValue.value)}`))
// Shown under the type of a group that still has storage, which a group does not use.
const unusedStorageNote = computed(() => unusedStorageNoteOf(current()))
// A stored storage type the served schema does not offer, such as a plugin that is not installed. It is kept as it is.
const unavailableStorage = computed(() => unavailableStorageTypeOf(current(), props.collection.itemSchema))
const pendingType = ref<string | null>(null)
const typeNotice = ref('')
const pendingRemovals = computed(() => (pendingType.value === null
  ? []
  : removalsFor(current(), typeValue.value, pendingType.value)))
const pendingWarnings = computed(() => (pendingType.value === null
  ? []
  : warningsFor(current(), typeValue.value, pendingType.value)))
// Empty mirrored entries are dropped by a typed repository, so the switch names them as removals too.
const pendingBlanks = computed(() => (pendingType.value === null
  ? 0
  : blankEntriesRemovedBy(current(), pendingType.value)))
const pendingRemoves = computed(() => pendingRemovals.value.length > 0 || pendingBlanks.value > 0)
const confirmLabel = computed(() => (pendingRemoves.value ? 'Switch and remove' : 'Switch'))

const applyType = (type: string) => {
  const next = switchedEntry(current(), type)
  latest.value = next
  pendingType.value = null
  typeNotice.value = `Type set to ${typeLabelOf(type).toLowerCase() || 'not set'}.`
  emit('change', next)
}

// A switch that removes or leaves behind a setting is asked about first, and names it. Nothing is deleted silently.
const requestType = (type: string) => {
  if (type === typeValue.value) return
  if (removalsFor(current(), typeValue.value, type).length === 0
    && warningsFor(current(), typeValue.value, type).length === 0
    && blankEntriesRemovedBy(current(), type) === 0) {
    applyType(type)
    return
  }
  pendingType.value = type
  typeNotice.value = ''
}

const confirmType = () => {
  if (pendingType.value !== null) applyType(pendingType.value)
}
const cancelType = () => {
  pendingType.value = null
}

// The rules the type breaks, checked here so the problem is shown before the save. Only for the typed repositories.
const violations = computed(() => (props.typed ? violationsOf(current(), { otherIds: props.otherIds }) : []))

// A group's members are its mirrored entries, edited as a list. The picker hands back the entries that stay as they were,
// so the settings and credentials of each member are kept; only the list itself changes.
const members = computed(() => (Array.isArray(current().proxied) ? current().proxied as Mirror[] : []))
const onMembers = (entries: Mirror[]) => {
  const next = { ...latest.value, proxied: entries }
  latest.value = next
  emit('change', next)
}

// The fields the type does not take are left out of the form. The type itself is shown by the selector above it.
const withoutFields = (node: LayoutNode, hidden: Set<string>): LayoutNode => {
  const elements = (node.elements ?? [])
    .filter(element => !(element.type === 'Control' && hidden.has((element.scope ?? '').replace('#/properties/', ''))))
    .map(element => (element.elements ? withoutFields(element, hidden) : element))
    .filter(element => !(element.type === 'Group' && element.elements?.length === 0))
  return { ...node, elements }
}

// A proxy's storage is the storage its cache is kept in, so the form names it that way.
const labelled = (node: LayoutNode, type: string): LayoutNode => ({
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
  const hidden = new Set([...hiddenFormFields(typeValue.value), ...(props.typed ? ['type'] : []), ...props.omit])
  return labelled(withoutFields(layout.value, hidden), typeValue.value)
})
</script>

<template>
  <div
    v-if="entry === null"
    class="flex flex-col items-start gap-4"
    data-ingot-component="entry-detail"
  >
    <h3 class="md-headline-small text-on-surface">
      Repository not found
    </h3>
    <p class="md-body-large text-on-surface-variant">
      There is no {{ collection.noun }} with the id "{{ id }}" in this configuration.
    </p>
  </div>

  <div
    v-else
    class="flex flex-col gap-6"
    data-ingot-component="entry-detail"
  >
    <div
      v-if="shows('general')"
      data-ingot-part="header"
    >
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
    </div>

    <section
      v-if="typed && shows('type')"
      data-test="repository-type"
      aria-labelledby="entry-type-title"
      class="flex flex-col gap-4"
    >
      <h3
        id="entry-type-title"
        class="md-title-medium text-on-surface"
      >
        Type
      </h3>
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
        <h3
          id="entry-type-removals-title"
          class="md-title-small text-on-surface"
        >
          Switching to {{ typeLabelOf(pendingType).toLowerCase() }}{{ pendingRemoves ? ' removes' : '' }}
        </h3>
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
          v-if="pendingBlanks > 0"
          data-test="type-blank-entries"
          class="md-body-medium text-on-surface"
        >
          {{ blankEntriesNotice(pendingBlanks) }}
        </p>
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
        <h3 class="md-title-small text-on-surface">
          Members
        </h3>
        <RepositoryMembersPicker
          id-prefix="entry-members"
          :model-value="members"
          :candidates="otherIds"
          credentials
          @update:model-value="onMembers"
        />
      </div>
    </section>

    <p
      v-if="drawsForm && unavailableStorage && (whole || order?.includes('storageProvider'))"
      data-test="unavailable-storage"
      role="status"
      class="md-body-medium text-warning"
    >
      The storage provider "{{ unavailableStorage }}" is not installed. The settings keep it unchanged until it is installed again.
    </p>

    <div
      v-if="drawsForm"
      data-test="entry-form"
    >
      <!-- The form holds the latest value, so a change of the type is never answered with the value before it. -->
      <JsonForms
        :key="id"
        :data="latest"
        :schema="collection.itemSchema"
        :uischema="formLayout"
        :renderers="renderers"
        :uischemas="nestedUischemas"
        :ajv="validator"
        :config="formConfiguration"
        @change="onChange"
      />
    </div>

    <RepositoryAccessCard
      v-if="showAccess && idText(entry) && shows('access')"
      :repository="idText(entry)"
    />

    <section
      v-if="shows('danger')"
      data-test="danger-zone"
      aria-labelledby="entry-danger-title"
      class="flex flex-col gap-3 rounded-corner-medium border border-error p-4"
    >
      <h3
        id="entry-danger-title"
        class="md-title-medium text-error"
      >
        Danger zone
      </h3>
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
      v-if="shows('danger')"
      :id="idText(entry) || 'unnamed'"
      v-model="deleteOpen"
      :references="references"
      @confirm="emit('delete')"
    />
  </div>
</template>
