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
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'
import MdFieldset from '../md/MdFieldset.vue'
import MdTextField from '../md/MdTextField.vue'
import { activeFilters, dateInputValue, instantFromDateInput, splitAttribute } from './searchQuery'
import type { SearchQuery } from './searchQuery'
import type { AttributeFacets, FacetableAttribute, Facets } from './searchResult'

interface FacetOption {
  value: string
  count: number | null
}

interface FilterGroup {
  key: 'repository' | 'extension' | 'classifier'
  title: string
  facet: keyof Facets
  selected: string[]
  options: FacetOption[]
}

type TextKey = 'groupId' | 'artifactId' | 'version'
type DateKey = 'modifiedAfter' | 'modifiedBefore'
type BytesKey = 'sizeMin' | 'sizeMax'
type Draft = Record<TextKey | DateKey | BytesKey, string>

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
const props = withDefaults(defineProps<{
  query: SearchQuery
  facets: Facets
  // The buckets of each declared attribute, and the labels of the keys the instance can count.
  attributes?: AttributeFacets
  attributeLabels?: FacetableAttribute[]
}>(), {
  attributes: () => ({}),
  attributeLabels: () => []
})

const emit = defineEmits<{
  change: [partial: Partial<SearchQuery>]
  reset: []
}>()

const ORIGIN_OPTIONS = [
  { value: 'all', label: 'Any origin', facet: null },
  { value: 'hosted', label: 'Hosted', facet: 'HOSTED' },
  { value: 'mirror', label: 'Cached copies', facet: 'MIRROR' }
] as const

const SNAPSHOT_OPTIONS = [
  { value: 'all', label: 'Any version', facet: null },
  { value: 'false', label: 'Releases', facet: 'false' },
  { value: 'true', label: 'Snapshots', facet: 'true' }
] as const

const countOf = (facet: keyof Facets, value: string): number | null => props.facets[facet]?.find(bucket => bucket.value === value)?.count ?? null

// The buckets of a facet, and then any chosen value the facet no longer lists, so a chosen chip never vanishes.
const optionsOf = (facet: keyof Facets, selected: string[]): FacetOption[] => {
  const listed = (props.facets[facet] ?? []).map(bucket => ({ value: bucket.value, count: bucket.count }))
  const missing = selected.filter(value => !listed.some(option => option.value === value))
  return [...listed, ...missing.map(value => ({ value, count: null }))]
}

const GROUPS = computed(() => ([
  { key: 'repository', title: 'Repository', facet: 'repository', selected: props.query.repository, label: (value: string) => value },
  { key: 'extension', title: 'File type', facet: 'extension', selected: props.query.extension, label: (value: string) => value },
  {
    key: 'classifier', title: 'Classifier', facet: 'classifier', selected: props.query.classifier,
    label: (value: string) => (value === '-' ? 'none' : value)
  }
] as const).map(group => ({ ...group, options: optionsOf(group.facet, group.selected) })))

// One group for each key of the response, in its order, then any chosen key it no longer lists. A chosen
// value that the buckets omit stays, with no count, like the fixed groups.
const ATTRIBUTE_GROUPS = computed(() => {
  const chosen = props.query.attribute.map(splitAttribute)
  const keys = [...new Set([...Object.keys(props.attributes), ...chosen.map(pair => pair.key)])]
  return keys.map((key) => {
    const selected = chosen.filter(pair => pair.key === key).map(pair => pair.value)
    const listed = (props.attributes[key] ?? []).map(bucket => ({ value: bucket.value, count: bucket.count as number | null }))
    const missing = selected.filter(value => !listed.some(option => option.value === value))
    return {
      key,
      title: props.attributeLabels.find(entry => entry.key === key)?.label ?? key,
      selected,
      options: [...listed, ...missing.map(value => ({ value, count: null as number | null }))]
    }
  })
})

const toggleAttribute = (key: string, value: string) => {
  const pair = `${key}:${value}`
  emit('change', {
    attribute: props.query.attribute.includes(pair)
      ? props.query.attribute.filter(entry => entry !== pair)
      : [...props.query.attribute, pair]
  })
}

const toggleValue = (group: Pick<FilterGroup, 'key' | 'selected'>, value: string) => {
  const next = group.selected.includes(value)
    ? group.selected.filter(entry => entry !== value)
    : [...group.selected, value]
  emit('change', { [group.key]: next })
}

const filterWords = computed(() => activeFilters({ ...props.query, q: '' }))
const filtersActive = computed(() => filterWords.value.length > 0)

// Drafts of the fields, so typing does not change the address on every key.
const draft = ref({} as Draft)
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

const commitText = (key: TextKey) => {
  const value = draft.value[key].trim()
  if (value !== props.query[key]) emit('change', { [key]: value })
}

const commitDate = (key: DateKey) => {
  const instant = instantFromDateInput(draft.value[key])
  if (instant !== props.query[key]) emit('change', { [key]: instant })
}

// A byte count is a whole number; anything else is left out rather than sent to the server.
const commitBytes = (key: BytesKey) => {
  const value = /^\d+$/.test(draft.value[key].trim()) ? draft.value[key].trim() : ''
  if (value !== props.query[key]) emit('change', { [key]: value })
}
</script>

<template>
  <div
    class="flex flex-col gap-6"
    data-test="filters"
    data-ingot-component="search-filters"
  >
    <MdFieldset
      v-for="group in GROUPS"
      :key="group.key"
      :legend="group.title"
      :data-test="`group-${group.key}`"
    >
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
            class="md-label-medium"
          >{{ option.count }}</span>
        </MdChip>
      </div>
      <p
        v-else
        class="m-0 md-body-medium text-on-surface-variant"
      >
        None in this result.
      </p>
    </MdFieldset>

    <MdFieldset
      v-for="group in ATTRIBUTE_GROUPS"
      :key="group.key"
      :legend="group.title"
      :data-test="`group-attribute-${group.key}`"
    >
      <div class="flex flex-wrap gap-2">
        <MdChip
          v-for="option in group.options"
          :key="option.value"
          variant="filter"
          toggle="button"
          :data-test="`chip-attribute-${group.key}-${option.value}`"
          :selected="group.selected.includes(option.value)"
          :title="`${option.count ?? 0} files`"
          @click="toggleAttribute(group.key, option.value)"
        >
          {{ option.value }}
          <span class="md-label-medium">{{ option.count ?? 0 }}</span>
        </MdChip>
      </div>
    </MdFieldset>

    <MdFieldset
      legend="Origin"
      data-test="group-origin"
    >
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
            class="md-label-medium"
          >{{ countOf('origin', option.facet) }}</span>
        </MdChip>
      </div>
    </MdFieldset>

    <MdFieldset
      legend="Release or snapshot"
      data-test="group-snapshot"
    >
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
            class="md-label-medium"
          >{{ countOf('snapshot', option.facet) }}</span>
        </MdChip>
      </div>
    </MdFieldset>

    <p class="m-0 md-body-small text-on-surface-variant">
      The numbers on the chips count files, not versions.
    </p>

    <MdFieldset
      legend="Coordinates"
      class="gap-4"
    >
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
    </MdFieldset>

    <MdFieldset
      legend="Last modified"
      class="gap-4"
    >
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
    </MdFieldset>

    <MdFieldset
      legend="File size"
      class="gap-4"
    >
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
    </MdFieldset>

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
