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
import { computed, ref, useId } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'
import TokenTag from '../tokens/TokenTag.vue'
import { candidatePrincipals } from './accessTeams'

/*
 * The principal picker of a team: a search over the users and machine accounts that are not
 * members yet (a disabled principal is listed with a chip and can be added). The admin ticks
 * one or more and adds them.
 *
 * Props:
 *   modelValue   whether the dialog is open (v-model)
 *   teamName     the team, for the heading
 *   principals   [{ name, displayName, kind, disabled }] every principal there is
 *   members      [{ principal }] the current members, of either source
 *   loadError    why the principals could not be read, or an empty string
 *   busy         true while the caller's request runs
 *   failure      the message of a failed request, shown in the dialog
 * Emits update:modelValue and add (the picked names). The caller makes the request.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  teamName: {
    type: String,
    required: true
  },
  principals: {
    type: Array,
    default: () => []
  },
  members: {
    type: Array,
    default: () => []
  },
  loadError: {
    type: String,
    default: ''
  },
  busy: {
    type: Boolean,
    default: false
  },
  failure: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'add'])

const titleId = `add-member-title-${useId()}`
const query = ref('')
const picked = ref([])

const candidates = computed(() => candidatePrincipals(props.principals, props.members, query.value))

const toggle = (name) => {
  picked.value = picked.value.includes(name)
    ? picked.value.filter(entry => entry !== name)
    : [...picked.value, name]
}

const reset = () => {
  query.value = ''
  picked.value = []
}

const submit = () => {
  if (picked.value.length === 0 || props.busy) return
  emit('add', [...picked.value])
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    :labelledby="titleId"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      :id="titleId"
      class="md-headline-small"
    >
      Add members to {{ teamName }}
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      Users and machine accounts that are not in the team yet.
    </p>

    <form
      id="add-member-form"
      class="mt-6 flex flex-col gap-4"
      @submit.prevent="submit()"
    >
      <MdTextField
        v-model="query"
        data-test="picker-search"
        label="Search by name or display name"
        type="search"
        autocomplete="off"
      />

      <p
        v-if="loadError"
        role="alert"
        class="md-body-medium text-error"
      >
        Could not load the principals: {{ loadError }}
      </p>
      <ul
        v-else-if="candidates.length > 0"
        class="m-0 flex max-h-72 list-none flex-col gap-1 overflow-y-auto p-0"
        aria-label="Principals that can be added"
      >
        <li
          v-for="principal in candidates"
          :key="principal.name"
        >
          <label
            data-test="candidate"
            class="md-state-layer relative flex cursor-pointer items-start gap-3 rounded-corner-medium px-3 py-2"
          >
            <input
              type="checkbox"
              class="mt-1 size-5 shrink-0 accent-primary"
              :checked="picked.includes(principal.name)"
              :value="principal.name"
              @change="toggle(principal.name)"
            >
            <span class="flex min-w-0 flex-1 flex-col gap-1">
              <span class="md-title-small [overflow-wrap:anywhere]">{{ principal.displayName || principal.name }}</span>
              <span class="md-body-small text-on-surface-variant [overflow-wrap:anywhere]">{{ principal.name }}</span>
              <span class="flex flex-wrap gap-2">
                <TokenTag data-test="kind-chip">{{ principal.kind === 'MACHINE' ? 'Machine' : 'User' }}</TokenTag>
                <TokenTag v-if="principal.disabled">Disabled</TokenTag>
              </span>
            </span>
          </label>
        </li>
      </ul>
      <p
        v-else
        class="md-body-medium text-on-surface-variant"
        data-test="no-candidates"
      >
        {{ query.trim() === '' ? 'Everyone is a member already.' : `Nothing matches "${query.trim()}".` }}
      </p>

      <p
        v-if="failure"
        role="alert"
        data-test="add-failure"
        class="md-body-medium text-error"
      >
        {{ failure }}
      </p>
    </form>

    <div class="mt-6 flex justify-end gap-2">
      <MdButton
        variant="text"
        @click="emit('update:modelValue', false)"
      >
        Cancel
      </MdButton>
      <MdButton
        type="submit"
        form="add-member-form"
        data-test="add-members"
        :disabled="picked.length === 0 || busy"
      >
        Add {{ picked.length > 1 ? `${picked.length} members` : 'member' }}
      </MdButton>
    </div>
  </DialogShell>
</template>
