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
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'
import MdTextField from '../md/MdTextField.vue'
import { activeFilters, dateInputValue, instantFromDateInput } from './searchQuery'

/*
 * The filter panel of the search page: MD3 filter chips for the repository, the file extension, the
 * classifier, the origin and the release or snapshot state, and fields for the coordinates, the
 * modification date and the size. A chip row shows the alternatives to the current choice, with the
 * number of files behind each (spec section 4.4), so the counts are labelled "files".
 *
 * The panel owns no query. It reads the one it is given and reports every change as a partial query
 * through `change`; the page puts that into the address. The text fields keep a draft while they are
 * typed in and report it when the field is left or Enter is pressed (the native change event).
 */
const props = defineProps({
  query: { type: Object, required: true },
  facets: { type: Object, required: true }
})

const emit = defineEmits(['change', 'reset'])

const ORIGIN_OPTIONS = [
  { value: 'all', label: 'Any origin', facet: null },
  { value: 'hosted', label: 'Hosted', facet: 'HOSTED' },
  { value: 'mirror', label: 'Cached copies', facet: 'MIRROR' }
]

const SNAPSHOT_OPTIONS = [
  { value: 'all', label: 'Any version', facet: null },
  { value: 'false', label: 'Releases', facet: 'false' },
  { value: 'true', label: 'Snapshots', facet: 'true' }
]

const countOf = (facet, value) => props.facets[facet]?.find(bucket => bucket.value === value)?.count ?? null

// The buckets of a facet, and then any chosen value the facet no longer lists, so a chosen chip never vanishes.
const optionsOf = (facet, selected) => {
  const listed = (props.facets[facet] ?? []).map(bucket => ({ value: bucket.value, count: bucket.count }))
  const missing = selected.filter(value => !listed.some(option => option.value === value))
  return [...listed, ...missing.map(value => ({ value, count: null }))]
}

const GROUPS = computed(() => [
  { key: 'repository', title: 'Repository', facet: 'repository', selected: props.query.repository, label: value => value },
  { key: 'extension', title: 'File type', facet: 'extension', selected: props.query.extension, label: value => value },
  {
    key: 'classifier', title: 'Classifier', facet: 'classifier', selected: props.query.classifier,
    label: value => (value === '-' ? 'none' : value)
  }
].map(group => ({ ...group, options: optionsOf(group.facet, group.selected) })))

const toggleValue = (group, value) => {
  const next = group.selected.includes(value)
    ? group.selected.filter(entry => entry !== value)
    : [...group.selected, value]
  emit('change', { [group.key]: next })
}

const filterWords = computed(() => activeFilters({ ...props.query, q: '' }))
const filtersActive = computed(() => filterWords.value.length > 0)

// Drafts of the fields, so typing does not change the address on every key.
const draft = ref({})
const syncDraft = () => {
  draft.value = {
    groupId: props.query.groupId,
    artifactId: props.query.artifactId,
    version: props.query.version,
    modifiedAfter: dateInputValue(props.query.modifiedAfter),
    modifiedBefore: dateInputValue(props.query.modifiedBefore),
    sizeMin: props.query.sizeMin,
    sizeMax: props.query.sizeMax
  }
}
syncDraft()
watch(() => props.query, syncDraft)

const commitText = (key) => {
  const value = draft.value[key].trim()
  if (value !== props.query[key]) emit('change', { [key]: value })
}

const commitDate = (key) => {
  const instant = instantFromDateInput(draft.value[key])
  if (instant !== props.query[key]) emit('change', { [key]: instant })
}

// A byte count is a whole number; anything else is left out rather than sent to the server.
const commitBytes = (key) => {
  const value = /^\d+$/.test(draft.value[key].trim()) ? draft.value[key].trim() : ''
  if (value !== props.query[key]) emit('change', { [key]: value })
}
</script>

<template>
  <div
    class="flex flex-col gap-6"
    data-test="filters"
  >
    <fieldset
      v-for="group in GROUPS"
      :key="group.key"
      class="m-0 min-w-0 border-0 p-0"
      :data-test="`group-${group.key}`"
    >
      <legend class="mb-2 p-0 md-title-small text-on-surface">
        {{ group.title }}
      </legend>
      <div
        v-if="group.options.length > 0"
        class="flex flex-wrap gap-2"
      >
        <MdChip
          v-for="option in group.options"
          :key="option.value"
          variant="filter"
          toggle="button"
          :data-test="`chip-${group.key}-${option.value}`"
          :selected="group.selected.includes(option.value)"
          :title="option.count === null ? undefined : `${option.count} files`"
          @click="toggleValue(group, option.value)"
        >
          {{ group.label(option.value) }}
          <span
            v-if="option.count !== null"
            class="opacity-70"
          >{{ option.count }}</span>
        </MdChip>
      </div>
      <p
        v-else
        class="m-0 md-body-medium text-on-surface-variant"
      >
        None in this result.
      </p>
    </fieldset>

    <fieldset
      class="m-0 min-w-0 border-0 p-0"
      data-test="group-origin"
    >
      <legend class="mb-2 p-0 md-title-small text-on-surface">
        Origin
      </legend>
      <div class="flex flex-wrap gap-2">
        <MdChip
          v-for="option in ORIGIN_OPTIONS"
          :key="option.value"
          variant="filter"
          toggle="button"
          :data-test="`chip-origin-${option.value}`"
          :selected="query.origin === option.value"
          :title="option.facet && countOf('origin', option.facet) !== null ? `${countOf('origin', option.facet)} files` : undefined"
          @click="emit('change', { origin: option.value })"
        >
          {{ option.label }}
          <span
            v-if="option.facet && countOf('origin', option.facet) !== null"
            class="opacity-70"
          >{{ countOf('origin', option.facet) }}</span>
        </MdChip>
      </div>
    </fieldset>

    <fieldset
      class="m-0 min-w-0 border-0 p-0"
      data-test="group-snapshot"
    >
      <legend class="mb-2 p-0 md-title-small text-on-surface">
        Release or snapshot
      </legend>
      <div class="flex flex-wrap gap-2">
        <MdChip
          v-for="option in SNAPSHOT_OPTIONS"
          :key="option.value"
          variant="filter"
          toggle="button"
          :data-test="`chip-snapshot-${option.value}`"
          :selected="query.snapshot === option.value"
          :title="option.facet && countOf('snapshot', option.facet) !== null ? `${countOf('snapshot', option.facet)} files` : undefined"
          @click="emit('change', { snapshot: option.value })"
        >
          {{ option.label }}
          <span
            v-if="option.facet && countOf('snapshot', option.facet) !== null"
            class="opacity-70"
          >{{ countOf('snapshot', option.facet) }}</span>
        </MdChip>
      </div>
    </fieldset>

    <p class="m-0 md-body-small text-on-surface-variant">
      The numbers on the chips count files, not versions.
    </p>

    <fieldset class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
      <legend class="mb-2 p-0 md-title-small text-on-surface">
        Coordinates
      </legend>
      <MdTextField
        v-model="draft.groupId"
        variant="outlined"
        label="Group id starts with"
        data-test="field-groupId"
        @change="commitText('groupId')"
      />
      <MdTextField
        v-model="draft.artifactId"
        variant="outlined"
        label="Artifact id starts with"
        data-test="field-artifactId"
        @change="commitText('artifactId')"
      />
      <MdTextField
        v-model="draft.version"
        variant="outlined"
        label="Version"
        data-test="field-version"
        @change="commitText('version')"
      />
    </fieldset>

    <fieldset class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
      <legend class="mb-2 p-0 md-title-small text-on-surface">
        Last modified
      </legend>
      <MdTextField
        v-model="draft.modifiedAfter"
        variant="outlined"
        type="date"
        label="On or after"
        data-test="field-modifiedAfter"
        @change="commitDate('modifiedAfter')"
      />
      <MdTextField
        v-model="draft.modifiedBefore"
        variant="outlined"
        type="date"
        label="Before"
        data-test="field-modifiedBefore"
        @change="commitDate('modifiedBefore')"
      />
    </fieldset>

    <fieldset class="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
      <legend class="mb-2 p-0 md-title-small text-on-surface">
        File size
      </legend>
      <MdTextField
        v-model="draft.sizeMin"
        variant="outlined"
        type="number"
        min="0"
        inputmode="numeric"
        label="At least (bytes)"
        data-test="field-sizeMin"
        @change="commitBytes('sizeMin')"
      />
      <MdTextField
        v-model="draft.sizeMax"
        variant="outlined"
        type="number"
        min="0"
        inputmode="numeric"
        label="At most (bytes)"
        data-test="field-sizeMax"
        @change="commitBytes('sizeMax')"
      />
    </fieldset>

    <MdButton
      v-if="filtersActive"
      variant="text"
      class="self-start"
      data-test="reset-filters"
      @click="emit('reset')"
    >
      Reset filters
    </MdButton>
  </div>
</template>
