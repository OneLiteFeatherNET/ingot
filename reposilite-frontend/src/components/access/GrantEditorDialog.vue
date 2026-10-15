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
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'
import { ROLES, grantPayload, normalizePathPrefix, roleExplanation } from './accessTeams'

/*
 * Adds or edits one grant of a team: the repository (every repository or one of the list), an
 * optional path prefix and the role. Slashes around the path prefix are dropped.
 *
 * Props:
 *   modelValue    whether the dialog is open (v-model)
 *   grant         the grant being edited, or null for a new one
 *   repositories  the ids of the repositories to choose from
 * Emits update:modelValue and save (the grant: { repository, pathPrefix, role }).
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  grant: {
    type: Object,
    default: null
  },
  repositories: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'save'])

const EVERY = ''

const repository = ref(EVERY)
const pathPrefix = ref('')
const role = ref('READ')

const fill = () => {
  repository.value = props.grant?.repository ?? EVERY
  pathPrefix.value = normalizePathPrefix(props.grant?.pathPrefix) ?? ''
  role.value = props.grant?.role ?? 'READ'
}

watch(() => props.modelValue, (open) => { if (open) fill() }, { immediate: true })

const repositoryOptions = computed(() => {
  // A grant on a repository the list does not know (renamed, or the list failed) stays editable.
  const ids = [...props.repositories]
  if (repository.value && !ids.includes(repository.value)) ids.push(repository.value)
  return [{ value: EVERY, label: 'Every repository' }, ...ids.map(id => ({ value: id, label: id }))]
})

const roleOptions = ROLES.map(entry => ({ value: entry.value, label: entry.label }))

const submit = () => {
  emit('save', grantPayload({ repository: repository.value, pathPrefix: pathPrefix.value, role: role.value }))
  emit('update:modelValue', false)
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="grant-editor-title"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <h2
      id="grant-editor-title"
      class="md-headline-small"
    >
      {{ grant ? 'Edit grant' : 'Add grant' }}
    </h2>

    <form
      id="grant-editor-form"
      class="mt-6 flex flex-col gap-4"
      @submit.prevent="submit()"
    >
      <MdSelect
        id="grant-repository"
        v-model="repository"
        label="Repository"
        :options="repositoryOptions"
      />
      <MdTextField
        id="grant-path-prefix"
        v-model="pathPrefix"
        label="Path prefix (optional)"
        type="text"
        autocomplete="off"
        supporting-text="Leave empty for the whole repository. Slashes around the prefix are dropped."
      />
      <MdSelect
        id="grant-role"
        v-model="role"
        label="Role"
        :options="roleOptions"
        :supporting-text="roleExplanation(role)"
      />
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
        form="grant-editor-form"
        data-test="save-grant"
      >
        {{ grant ? 'Apply' : 'Add' }}
      </MdButton>
    </div>
  </DialogShell>
</template>
