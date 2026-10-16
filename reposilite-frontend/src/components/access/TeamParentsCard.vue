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
import { computed, onMounted, ref, useId, watch } from 'vue'
import { createSuccessToast, errorMessage } from '../../helpers/toast'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import { teamParentsApi } from './nodes/teamParentsApi'

/*
 * The "Inherits from" card of a team: the parent teams as removable chips, a field that adds a team
 * and a Save button that sends the whole list. The card reads the parents itself (they come with
 * their own entity tag) and the team names for the suggestions.
 *
 * The field is a combobox. As the text changes, the teams that contain it, and are not parents yet,
 * appear in a listbox. ArrowDown and ArrowUp move through the options, Enter on an option adds it
 * and Escape closes the list. Without an option in focus, Enter adds the typed name if it is a team.
 * The team itself is refused with a message, because a team cannot inherit from itself.
 *
 * A rejected save (a cycle, too deep a chain, an unknown team, a stale tag) shows the server's own
 * message on the card, not as a toast.
 *
 * Props:
 *   name  the team whose parents are edited
 * Emits saved after the parents were written, so the page can read the team again.
 */
const props = defineProps({
  name: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['saved'])

const SUGGESTION_LIMIT = 8

const { client } = useSession()
const api = () => teamParentsApi(client.value)

const titleId = useId()
const inputId = `parent-add-${useId()}`
const listboxId = `${inputId}-listbox`
const optionId = (index) => `${inputId}-option-${index}`

const status = ref('loading')
const failure = ref('')
const saved = ref([])
const draft = ref([])
const etag = ref(null)
const teamNames = ref([])
const saving = ref(false)
const serverError = ref('')
const stale = ref(false)

const text = ref('')
const open = ref(false)
const active = ref(-1)
const entryError = ref('')

let latest = 0

const same = (left, right) => String(left).toLowerCase() === String(right).toLowerCase()
const has = (list, team) => list.some(entry => same(entry, team))

const load = async () => {
  const request = ++latest
  status.value = 'loading'
  failure.value = ''
  serverError.value = ''
  stale.value = false
  try {
    const result = await api().parents(props.name)
    if (request !== latest) return
    saved.value = result.parents
    draft.value = [...result.parents]
    etag.value = result.etag
    status.value = 'ready'
  } catch (error) {
    if (request !== latest) return
    status.value = 'error'
    failure.value = errorMessage(error)
    return
  }
  try {
    const names = await api().teamNames()
    if (request === latest) teamNames.value = names
  } catch {
    // Without the list there are no suggestions, and the server still checks every name.
    if (request === latest) teamNames.value = []
  }
}

onMounted(load)

watch(() => props.name, () => {
  text.value = ''
  entryError.value = ''
  load()
})

const dirty = computed(() => {
  if (draft.value.length !== saved.value.length) return true
  return draft.value.some(team => !has(saved.value, team))
})

const trimmed = computed(() => text.value.trim())

const suggestions = computed(() => {
  const needle = trimmed.value.toLowerCase()
  return teamNames.value
    .filter(team => !same(team, props.name) && !has(draft.value, team) && team.toLowerCase().includes(needle))
    .map((team, index) => ({ team, index, prefix: team.toLowerCase().startsWith(needle) ? 0 : 1 }))
    .sort((left, right) => (left.prefix - right.prefix) || (left.index - right.index))
    .slice(0, SUGGESTION_LIMIT)
    .map(entry => entry.team)
})

const showList = computed(() => open.value && suggestions.value.length > 0)
const activeDescendant = computed(() => (showList.value && active.value >= 0 ? optionId(active.value) : undefined))

// The message for a name that cannot become a parent, or an empty string. `team` is the name as
// the list knows it when it is known.
const problemWith = (value) => {
  if (value === '') return ''
  if (same(value, props.name)) return 'A team cannot inherit from itself'
  if (has(draft.value, value)) return `${value} is already a parent`
  if (teamNames.value.length > 0 && !has(teamNames.value, value)) return `There is no team named ${value}`
  return ''
}

const resetField = () => {
  text.value = ''
  active.value = -1
  open.value = false
  entryError.value = ''
}

const add = (value) => {
  const wanted = value.trim()
  const message = problemWith(wanted)
  if (wanted === '') return
  if (message) {
    entryError.value = message
    return
  }
  const known = teamNames.value.find(team => same(team, wanted)) ?? wanted
  draft.value = [...draft.value, known]
  serverError.value = ''
  resetField()
}

const remove = (team) => {
  draft.value = draft.value.filter(entry => entry !== team)
  serverError.value = ''
}

const onInput = (value) => {
  text.value = value
  open.value = true
  active.value = -1
  entryError.value = ''
}

const move = (step) => {
  const count = suggestions.value.length
  if (count === 0) return
  if (!open.value || active.value < 0) {
    open.value = true
    active.value = step > 0 ? 0 : count - 1
    return
  }
  active.value = (active.value + step + count) % count
}

const onKeydown = (event) => {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    move(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    move(-1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    if (showList.value && active.value >= 0) add(suggestions.value[active.value])
    else add(text.value)
  } else if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    open.value = false
    active.value = -1
  }
}

const onBlur = () => {
  open.value = false
  active.value = -1
}

const save = async () => {
  saving.value = true
  serverError.value = ''
  stale.value = false
  try {
    const result = await api().setParents(props.name, draft.value, etag.value)
    saved.value = result.parents
    draft.value = [...result.parents]
    etag.value = result.etag
    createSuccessToast('Parent teams saved')
    emit('saved')
  } catch (error) {
    serverError.value = errorMessage(error)
    stale.value = error?.response?.status === 412
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-large bg-surface-container-low px-4 py-4"
    :aria-labelledby="titleId"
    data-test="team-parents"
  >
    <div class="flex flex-col gap-1">
      <h4
        :id="titleId"
        class="md-title-medium text-on-surface"
      >
        Inherits from
      </h4>
      <p class="md-body-small text-on-surface-variant">
        A team gets the permission nodes of its parent teams, up to 5 levels up. Its own nodes decide first.
      </p>
    </div>

    <p
      v-if="status === 'loading'"
      role="status"
      class="md-body-medium text-on-surface-variant"
    >
      Loading...
    </p>
    <div
      v-else-if="status === 'error'"
      role="alert"
      class="flex flex-wrap items-center gap-3"
      data-test="parents-load-error"
    >
      <span class="md-body-medium text-error">Could not load the parent teams: {{ failure }}</span>
      <MdButton
        variant="tonal"
        data-test="parents-retry"
        @click="load()"
      >
        Retry
      </MdButton>
    </div>

    <template v-else>
      <ul
        v-if="draft.length > 0"
        class="m-0 flex list-none flex-wrap items-center gap-2 p-0"
        aria-label="Parent teams"
        data-test="parent-list"
      >
        <li
          v-for="team in draft"
          :key="team"
          data-test="parent"
          class="inline-flex h-8 items-center gap-1 rounded-corner-small border border-outline bg-surface-container-low pl-3 pr-1 md-label-large text-on-surface"
        >
          <span class="[overflow-wrap:anywhere]">{{ team }}</span>
          <button
            type="button"
            class="md-state-layer md-focus-ring md-touch-target relative inline-flex size-6 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant"
            :aria-label="`Remove ${team}`"
            @click="remove(team)"
          >
            <svg
              viewBox="0 0 24 24"
              class="size-[18px]"
              fill="currentColor"
              aria-hidden="true"
              focusable="false"
            >
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </button>
        </li>
      </ul>
      <p
        v-else
        class="md-body-medium text-on-surface-variant"
        data-test="parents-empty"
      >
        This team does not inherit from another team.
      </p>

      <div class="flex flex-col gap-2 sm:flex-row sm:items-start">
        <div class="relative min-w-0 flex-1">
          <MdTextField
            :id="inputId"
            :model-value="text"
            label="Add a parent team"
            variant="outlined"
            autocomplete="off"
            spellcheck="false"
            :error="entryError !== ''"
            :error-text="entryError"
            supporting-text="Type a team name or choose one from the list"
            role="combobox"
            aria-autocomplete="list"
            :aria-expanded="showList ? 'true' : 'false'"
            :aria-controls="listboxId"
            :aria-activedescendant="activeDescendant"
            @update:model-value="onInput"
            @keydown="onKeydown"
            @focus="open = true"
            @blur="onBlur"
          />
          <ul
            v-show="showList"
            :id="listboxId"
            role="listbox"
            aria-label="Team suggestions"
            class="absolute inset-x-0 top-14 z-20 max-h-72 overflow-y-auto rounded-corner-extra-small bg-surface-container shadow-elevation-2"
            @mousedown.prevent
          >
            <li
              v-for="(team, index) in suggestions"
              :id="optionId(index)"
              :key="team"
              role="option"
              :aria-selected="index === active ? 'true' : 'false'"
              class="md-state-layer relative cursor-pointer px-4 py-2 md-body-medium"
              :class="index === active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface'"
              data-test="suggestion"
              @click="add(team)"
            >
              {{ team }}
            </li>
          </ul>
        </div>
        <MdButton
          variant="outlined"
          class="shrink-0 self-start sm:mt-2"
          :disabled="trimmed === ''"
          data-test="add-parent"
          @click="add(text)"
        >
          Add
        </MdButton>
      </div>

      <div
        v-if="serverError"
        role="alert"
        class="flex flex-wrap items-center gap-3"
        data-test="parents-error"
      >
        <span class="md-body-medium text-error">{{ serverError }}</span>
        <MdButton
          v-if="stale"
          variant="text"
          data-test="parents-reload"
          @click="load()"
        >
          Reload
        </MdButton>
      </div>

      <div class="flex justify-end">
        <MdButton
          variant="tonal"
          :disabled="!dirty || saving"
          data-test="save-parents"
          @click="save()"
        >
          Save
        </MdButton>
      </div>
    </template>
  </section>
</template>
