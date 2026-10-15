<script setup>
import { computed, ref, watch } from 'vue'
import download from 'downloadjs'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdChip from '../md/MdChip.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import AuditEventRow from './AuditEventRow.vue'
import {
  ACTION_GROUPS,
  ACTION_OPTIONS,
  RANGE_OPTIONS,
  auditFiltersToQuery,
  auditParams,
  isoToLocalInput,
  localInputToIso,
  readAuditFilters,
} from './auditQuery'

/*
 * The audit log, newest first. The filters live in the route query: the view reads them from `query`
 * and reports every change through update:query, so the address always says what is on screen. A
 * request that answers after a newer one has started is dropped, so a slow page cannot overwrite the
 * list the reader is looking at. Text fields are drafts until they are applied, so typing does not
 * send a request for each key.
 */
const props = defineProps({
  query: {
    type: Object,
    default: () => ({})
  },
  /** The reference time, in epoch milliseconds, for the presets. Tests pass a fixed value. */
  now: {
    type: Number,
    default: undefined
  }
})

const emit = defineEmits(['update:query'])

const session = useSession()
const referenceTime = () => props.now ?? Date.now()

const filters = computed(() => readAuditFilters(props.query))

// The drafts follow the applied text filters only, so a chip or the action picker does not discard
// text that has been typed but not applied yet.
const draft = ref({ text: '', actor: '', repository: '' })
watch(
  () => [filters.value.text, filters.value.actor, filters.value.repository],
  ([text, actor, repository]) => {
    draft.value = { text, actor, repository }
  },
  { immediate: true }
)

const events = ref([])
const nextCursor = ref(null)
const loaded = ref(false)
const loading = ref(false)
const loadError = ref(null)
const exporting = ref(false)
const openRows = ref(new Set())

let latestRequest = 0

const fetchPage = async (cursor = null) => {
  const request = ++latestRequest
  loading.value = true
  loadError.value = null
  try {
    const response = await session.client.value.audit.list(auditParams(filters.value, referenceTime(), cursor))
    if (request !== latestRequest) return
    events.value = cursor === null ? response.data.events : [...events.value, ...response.data.events]
    nextCursor.value = response.data.nextCursor ?? null
    loaded.value = true
  } catch (error) {
    if (request === latestRequest) loadError.value = errorMessage(error)
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

// A new address means a new question, so the list starts over and the open rows are forgotten.
watch(() => props.query, () => {
  openRows.value = new Set()
  fetchPage(null)
}, { deep: true, immediate: true })

const loadMore = () => {
  if (nextCursor.value && !loading.value) {
    fetchPage(nextCursor.value)
  }
}

const update = (changes) => emit('update:query', auditFiltersToQuery({ ...filters.value, ...changes }))

const chooseAction = (action) => update({ action })
const groupValue = (group) => group.actions.join(',')
const toggleGroup = (group) => update({ action: filters.value.action === groupValue(group) ? '' : groupValue(group) })
// The picker names one action; a chip stands for several, so the picker is blank while a chip is on.
const pickedAction = computed(() => (filters.value.action.includes(',') ? '' : filters.value.action))
const chooseRange = (range) => update({ range })
const applyText = () => update({
  text: draft.value.text.trim(),
  actor: draft.value.actor.trim(),
  repository: draft.value.repository.trim(),
})
const setCustomBound = (key, value) => update({ range: 'custom', [key]: localInputToIso(value) })

const toggleRow = (id) => {
  const next = new Set(openRows.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  openRows.value = next
}

const exportCsv = async () => {
  exporting.value = true
  try {
    const response = await session.client.value.audit.exportCsv(auditParams(filters.value, referenceTime()))
    download(response.data, 'ingot-audit.csv', 'text/csv')
  } catch (error) {
    createErrorToast(`Could not export the audit log: ${errorMessage(error)}`)
  } finally {
    exporting.value = false
  }
}

const countText = computed(() => {
  const count = events.value.length
  if (count === 0) return ''
  return `Showing ${count} ${count === 1 ? 'event' : 'events'}${nextCursor.value ? ', more are available' : ''}`
})
</script>

<template>
  <section
    class="container mx-auto pb-12 pt-7"
    aria-labelledby="audit-title"
  >
    <div class="flex flex-col gap-1 pb-7">
      <h2
        id="audit-title"
        class="md-headline-small text-on-surface"
      >
        Audit log
      </h2>
      <p class="md-body-large text-on-surface-variant">
        Deployments, deletions, settings changes, token changes and failed sign ins on this instance, newest first.
      </p>
    </div>

    <div class="overflow-hidden rounded-corner-large bg-surface-container-low">
      <form
        role="search"
        aria-label="Filter the audit log"
        class="flex flex-col gap-3 border-b border-outline-variant px-4 py-4"
        @submit.prevent="applyText"
      >
        <div class="grid gap-3 min-[720px]:grid-cols-3">
          <MdTextField
            v-model="draft.text"
            label="Search"
            variant="outlined"
          />
          <MdTextField
            v-model="draft.actor"
            label="Actor"
            variant="outlined"
          />
          <MdTextField
            v-model="draft.repository"
            label="Repository"
            variant="outlined"
          />
        </div>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="min-w-60">
            <MdSelect
              :model-value="pickedAction"
              label="Action"
              :options="ACTION_OPTIONS"
              @update:model-value="chooseAction"
            />
          </div>
          <MdButton
            type="submit"
            variant="tonal"
          >
            Apply filters
          </MdButton>
        </div>
      </form>

      <div
        role="group"
        aria-label="Identity actions"
        class="flex flex-wrap items-center gap-2 border-b border-outline-variant px-4 py-3"
      >
        <MdChip
          v-for="group in ACTION_GROUPS"
          :key="group.key"
          variant="filter"
          :selected="filters.action === groupValue(group)"
          @click="toggleGroup(group)"
        >
          {{ group.label }}
        </MdChip>
      </div>

      <div
        role="group"
        aria-label="Time range"
        class="flex flex-wrap items-center gap-2 border-b border-outline-variant px-4 py-3"
      >
        <MdChip
          v-for="option in RANGE_OPTIONS"
          :key="option.key"
          variant="filter"
          :selected="filters.range === option.key"
          @click="chooseRange(option.key)"
        >
          {{ option.label }}
        </MdChip>
      </div>

      <div
        v-if="filters.range === 'custom'"
        class="grid gap-3 border-b border-outline-variant px-4 py-4 min-[720px]:grid-cols-2"
      >
        <MdTextField
          :model-value="isoToLocalInput(filters.from)"
          label="From"
          variant="outlined"
          type="datetime-local"
          @update:model-value="(value) => setCustomBound('from', value)"
        />
        <MdTextField
          :model-value="isoToLocalInput(filters.to)"
          label="To"
          variant="outlined"
          type="datetime-local"
          @update:model-value="(value) => setCustomBound('to', value)"
        />
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
        <p
          class="md-body-medium text-on-surface-variant"
          aria-live="polite"
        >
          {{ countText }}
        </p>
        <MdButton
          variant="tonal"
          :disabled="exporting"
          @click="exportCsv"
        >
          Export CSV
        </MdButton>
      </div>

      <MdLinearProgress
        v-if="loading"
        indeterminate
        label="Loading audit events"
      />

      <div
        v-if="loadError"
        role="alert"
        class="flex flex-wrap items-center gap-3 border-t border-outline-variant px-4 py-4"
      >
        <span class="md-body-medium text-error">Could not load the audit log: {{ loadError }}</span>
        <MdButton
          variant="tonal"
          @click="fetchPage(null)"
        >
          Retry
        </MdButton>
      </div>

      <ul
        v-else-if="events.length > 0"
        role="list"
        class="list-none border-t border-outline-variant"
      >
        <AuditEventRow
          v-for="event in events"
          :key="event.id"
          :event="event"
          :expanded="openRows.has(event.id)"
          @toggle="toggleRow"
        />
      </ul>

      <p
        v-else-if="loaded && !loading"
        class="md-body-medium border-t border-outline-variant px-4 py-12 text-center text-on-surface-variant"
      >
        No events match these filters.
      </p>

      <div
        v-if="nextCursor"
        class="flex justify-center border-t border-outline-variant px-4 py-4"
      >
        <MdButton
          variant="tonal"
          :disabled="loading"
          @click="loadMore"
        >
          Load more
        </MdButton>
      </div>
    </div>
  </section>
</template>
