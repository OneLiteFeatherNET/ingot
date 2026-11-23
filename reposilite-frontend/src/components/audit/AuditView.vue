<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AxiosResponse } from 'axios'
import download from 'downloadjs'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdChip from '../md/MdChip.vue'
import MdFieldGrid from '../md/MdFieldGrid.vue'
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
import type { ActionGroup, AuditEvent, AuditFilters, AuditPage, AuditQuery, AuditRouteQuery } from './auditQuery'

/*
 * The audit log, newest first. The filters live in the route query: the view reads them from `query`
 * and reports every change through update:query, so the address always says what is on screen. A
 * request that answers after a newer one has started is dropped, so a slow page cannot overwrite the
 * list the reader is looking at. Text fields are drafts until they are applied, so typing does not
 * send a request for each key.
 */
const props = withDefaults(defineProps<{
  query?: AuditQuery
  /** The reference time, in epoch milliseconds, for the presets. Tests pass a fixed value. */
  now?: number
}>(), {
  query: () => ({}),
  now: undefined
})

const emit = defineEmits<{
  'update:query': [query: AuditRouteQuery]
}>()

const session = useSession()
const referenceTime = (): number => props.now ?? Date.now()

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

const events = ref<AuditEvent[]>([])
const nextCursor = ref<string | null>(null)
const loaded = ref(false)
const loading = ref(false)
const loadError = ref<string | null>(null)
const exporting = ref(false)
const openRows = ref(new Set<AuditEvent['id']>())

let latestRequest = 0

const fetchPage = async (cursor: string | null = null): Promise<void> => {
  const request = ++latestRequest
  loading.value = true
  loadError.value = null
  try {
    const response: AxiosResponse<AuditPage> = await session.client.value.audit.list(auditParams(filters.value, referenceTime(), cursor))
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

const loadMore = (): void => {
  if (nextCursor.value && !loading.value) {
    fetchPage(nextCursor.value)
  }
}

const update = (changes: Partial<AuditFilters>): void => emit('update:query', auditFiltersToQuery({ ...filters.value, ...changes }))

const chooseAction = (action: string): void => update({ action })
const groupValue = (group: ActionGroup): string => group.actions.join(',')
const toggleGroup = (group: ActionGroup): void => update({ action: filters.value.action === groupValue(group) ? '' : groupValue(group) })
// The picker names one action; a chip stands for several, so the picker is blank while a chip is on.
const pickedAction = computed(() => (filters.value.action.includes(',') ? '' : filters.value.action))
const chooseRange = (range: string): void => update({ range })
const applyText = (): void => update({
  text: draft.value.text.trim(),
  actor: draft.value.actor.trim(),
  repository: draft.value.repository.trim(),
})
const setCustomBound = (key: 'from' | 'to', value: string): void => update({ range: 'custom', [key]: localInputToIso(value) })

const toggleRow = (id: AuditEvent['id']): void => {
  const next = new Set(openRows.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  openRows.value = next
}

const exportCsv = async (): Promise<void> => {
  exporting.value = true
  try {
    const response: AxiosResponse<Blob> = await session.client.value.audit.exportCsv(auditParams(filters.value, referenceTime()))
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
    class="flex flex-col"
    aria-label="Audit log"
    data-ingot-component="audit-view"
  >
    <MdCard
      padding="none"
      class="overflow-hidden"
    >
      <form
        role="search"
        aria-label="Filter the audit log"
        class="flex flex-col gap-3 border-b border-outline-variant px-4 py-4"
        @submit.prevent="applyText"
      >
        <div class="grid gap-3 medium:grid-cols-3">
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

      <MdFieldGrid
        v-if="filters.range === 'custom'"
        class="gap-3 border-b border-outline-variant px-4 py-4"
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
      </MdFieldGrid>

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
        data-ingot-state="loading"
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
    </MdCard>
  </section>
</template>
