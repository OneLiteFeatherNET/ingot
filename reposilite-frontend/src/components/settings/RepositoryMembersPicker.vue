/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
-->

<script setup>
import { computed, ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import { addMember, choosableMembers, moveMember, referenceOf, removeMemberAt, withCredentialField } from './groupMembers'

/*
 * The ordered list of repositories a group lists. A repository is added from a select of the ones
 * that are not members yet, and the order is changed with the up and down buttons. The buttons
 * are the keyboard way to reorder; a pointer user may use them too, so no drag is needed.
 *
 * The list is the value: each change emits the whole new list, and the list it was given is never
 * changed. A member is a mirrored entry, and the entries that stay are emitted as the same objects,
 * so a credential or any other setting of an entry is never lost by a change of the list. Used by
 * the wizard and by the settings of a group.
 *
 * Props:
 *   modelValue   the mirrored entries of the members, in order; each names its repository in `reference`
 *   candidates   the ids of every repository that can be a member
 *   error        the problem to show under the picker, or an empty string
 *   idPrefix     the prefix of the ids of the select, the add button and the credential fields
 *   credentials  whether each member shows its credential, and lets it be edited (the loopback login and password)
 */
const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  candidates: {
    type: Array,
    default: () => []
  },
  error: {
    type: String,
    default: ''
  },
  idPrefix: {
    type: String,
    default: 'members'
  },
  credentials: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const chosen = ref('')
const choices = computed(() => choosableMembers(props.candidates, props.modelValue))
const selectOptions = computed(() => [
  { value: '', label: 'Choose a repository' },
  ...choices.value.map(id => ({ value: id, label: id }))
])
const noCandidates = computed(() => props.candidates.length === 0)
const allChosen = computed(() => !noCandidates.value && choices.value.length === 0)

const add = () => {
  if (chosen.value === '') return
  emit('update:modelValue', addMember(props.modelValue, chosen.value))
  chosen.value = ''
}

const move = (index, delta) => emit('update:modelValue', moveMember(props.modelValue, index, delta))
const remove = (index) => emit('update:modelValue', removeMemberAt(props.modelValue, index))

// The member whose credential is open for editing, or null. One at a time keeps the list short.
const openCredential = ref(null)
const toggleCredential = (index) => {
  openCredential.value = openCredential.value === index ? null : index
}
const credentialOf = (entry) => entry.authorization ?? {}
const credentialSummary = (entry) => (credentialOf(entry).login
  ? `Credential as ${credentialOf(entry).login}`
  : 'No credential')
const setCredential = (index, field, value) => {
  emit('update:modelValue', props.modelValue.map((entry, position) => (position === index
    ? withCredentialField(entry, field, value)
    : entry)))
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <ol
      v-if="modelValue.length > 0"
      aria-label="Members in order"
      class="m-0 flex list-none flex-col gap-1 rounded-corner-medium bg-surface-container p-2"
    >
      <li
        v-for="(item, index) in modelValue"
        :key="index"
        data-test="member"
        class="flex flex-col gap-2 rounded-corner-small px-2 py-1"
      >
        <div class="flex items-center gap-2">
          <span
            data-test="member-place"
            class="flex size-7 shrink-0 items-center justify-center rounded-corner-full bg-secondary-container md-label-large text-on-secondary-container"
          >{{ index + 1 }}</span>
          <span
            data-test="member-id"
            class="min-w-0 flex-1 truncate md-body-large text-on-surface"
          >{{ referenceOf(item) }}</span>
          <MdIconButton
            :aria-label="`Move ${referenceOf(item)} up`"
            :title="`Move ${referenceOf(item)} up`"
            :disabled="index === 0"
            @click="move(index, -1)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M7.41 15.41 12 10.83l4.59 4.58L18 14l-6-6-6 6z" />
            </svg>
          </MdIconButton>
          <MdIconButton
            :aria-label="`Move ${referenceOf(item)} down`"
            :title="`Move ${referenceOf(item)} down`"
            :disabled="index === modelValue.length - 1"
            @click="move(index, 1)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z" />
            </svg>
          </MdIconButton>
          <MdIconButton
            :aria-label="`Remove ${referenceOf(item)} from the group`"
            :title="`Remove ${referenceOf(item)} from the group`"
            @click="remove(index)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
            </svg>
          </MdIconButton>
        </div>

        <div
          v-if="credentials"
          class="flex flex-col gap-3 pl-9"
        >
          <div class="flex items-center gap-2">
            <span
              data-test="member-credential"
              class="min-w-0 flex-1 truncate md-body-medium text-on-surface-variant"
            >{{ credentialSummary(item) }}</span>
            <MdButton
              variant="text"
              type="button"
              :aria-label="`Edit the credential of ${referenceOf(item)}`"
              :aria-expanded="openCredential === index ? 'true' : 'false'"
              @click="toggleCredential(index)"
            >
              {{ openCredential === index ? 'Done' : 'Edit credential' }}
            </MdButton>
          </div>
          <div
            v-if="openCredential === index"
            data-test="member-credential-form"
            class="grid gap-4 sm:grid-cols-2"
          >
            <MdTextField
              :id="`${idPrefix}-login-${index}`"
              :model-value="credentialOf(item).login ?? ''"
              label="Login"
              type="text"
              autocomplete="off"
              @update:model-value="setCredential(index, 'login', $event)"
            />
            <MdTextField
              :id="`${idPrefix}-password-${index}`"
              :model-value="credentialOf(item).password ?? ''"
              label="Password"
              type="password"
              autocomplete="off"
              supporting-text="A stored password shows as ********. Type a new value to replace it."
              @update:model-value="setCredential(index, 'password', $event)"
            />
          </div>
        </div>
      </li>
    </ol>

    <p
      v-else
      class="md-body-medium text-on-surface-variant"
    >
      The group lists no repository yet.
    </p>

    <p
      v-if="noCandidates"
      class="md-body-medium text-on-surface-variant"
    >
      There are no repositories yet. Create the repositories the group should list first.
    </p>
    <p
      v-else-if="allChosen"
      class="md-body-medium text-on-surface-variant"
    >
      Every repository is already a member of the group.
    </p>

    <div
      v-else
      class="flex flex-wrap items-start gap-2"
    >
      <MdSelect
        :id="`${idPrefix}-add`"
        v-model="chosen"
        class="min-w-0 flex-1"
        label="Repository to add"
        :options="selectOptions"
      />
      <MdButton
        :id="`${idPrefix}-add-button`"
        variant="tonal"
        type="button"
        :disabled="chosen === ''"
        @click="add"
      >
        Add
      </MdButton>
    </div>

    <p
      v-if="error"
      role="alert"
      class="md-body-medium text-error"
    >
      {{ error }}
    </p>
  </div>
</template>
