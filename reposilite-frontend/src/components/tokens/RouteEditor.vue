<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup>
import { computed, ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'
import SegmentedControl from './SegmentedControl.vue'
import SegmentedOption from './SegmentedOption.vue'
import TokenFormRow from './TokenFormRow.vue'
import { normalizeRoutePath, repositoryOfRoutePath } from '../../store/tokens'

const path = defineModel('path', { type: String, default: '' })
const read = defineModel('read', { type: Boolean, default: false })
const write = defineModel('write', { type: Boolean, default: false })

const props = defineProps({
  /** Adding a route and editing one differ in this word alone. */
  submitLabel: {
    type: String,
    required: true
  },
  /**
   * `{ message, field, existing }` after a failed check. A `field` of `access` marks a problem
   * with read and write rather than with the path. `existing` is the route that already has
   * this path, and makes the editor offer to open it.
   */
  error: {
    type: Object,
    default: null
  },
  /**
   * The ids of the repositories the server has saved, or null while they are not known. The picker
   * offers exactly these, so a route cannot name a repository that does not exist.
   */
  repositories: {
    type: Array,
    default: null
  }
})

defineEmits(['submit', 'cancel', 'edit-existing'])

// A value no repository id can take (ids allow no tilde), so it cannot clash with one.
const CUSTOM = '~custom'

/*
 * The path is a repository only when it is exactly "/<id>" for a saved id. Anything below a repository,
 * or a name that is not saved, is a custom path, typed in full. The choice the user makes wins over
 * the path, so the field does not change its mode while the user is typing into it.
 */
const chosenCustom = ref(null)

const isRepositoryPath = (value) => {
  const repository = repositoryOfRoutePath(value)
  return repository !== '' &&
    normalizeRoutePath(value) === `/${repository}` &&
    (props.repositories ?? []).includes(repository)
}

const custom = computed(() => chosenCustom.value ?? (path.value !== '' && !isRepositoryPath(path.value)))

const choice = computed(() => (custom.value ? CUSTOM : repositoryOfRoutePath(path.value)))

const choiceOptions = computed(() => [
  ...(choice.value === '' ? [{ value: '', label: 'Choose a repository' }] : []),
  ...(props.repositories ?? []).map(id => ({ value: id, label: id })),
  { value: CUSTOM, label: 'Custom path' }
])

const choose = (value) => {
  if (value === '') return
  if (value === CUSTOM) {
    chosenCustom.value = true
    return
  }
  chosenCustom.value = false
  path.value = `/${value}`
}

const typePath = (value) => {
  chosenCustom.value = true
  path.value = value
}

// The path field is shown for a custom path, and always when the repositories are not known.
const showPathField = computed(() => custom.value || props.repositories === null)

// A check failed on the path itself, which shows on the field. Otherwise the message is shown below.
const pathError = computed(() => !!props.error && props.error.field !== 'access' && showPathField.value)

/*
 * A custom path whose first segment is no saved repository matches nothing, so the editor says so. It
 * stays a warning, not an error: the list may be out of date, and the route can still be saved.
 */
const warning = computed(() => {
  if (!custom.value || props.repositories === null) return ''
  const repository = repositoryOfRoutePath(path.value)
  if (repository === '' || props.repositories.includes(repository)) return ''
  return `There is no repository "${repository}". A route to a missing repository matches nothing.`
})
</script>

<template>
  <TokenFormRow indent>
    <div
      v-if="repositories !== null"
      class="min-w-48 flex-1"
    >
      <MdSelect
        name="route-repository"
        label="Repository"
        :model-value="choice"
        :options="choiceOptions"
        supporting-text="Pick a saved repository, or a custom path below one."
        @update:model-value="choose"
      />
    </div>
    <div
      v-if="showPathField"
      class="min-w-48 flex-1"
    >
      <MdTextField
        name="route-path"
        label="Route path"
        variant="outlined"
        :model-value="path"
        :supporting-text="pathError ? '' : 'Starts with a slash, for example /releases/com/example/artifact'"
        :error="pathError"
        :error-text="pathError ? error.message : ''"
        @update:model-value="typePath"
        @keyup.enter="$emit('submit')"
      />
    </div>
    <SegmentedControl label="Route access">
      <SegmentedOption
        :on="read"
        @click="read = !read"
      >
        read
      </SegmentedOption>
      <SegmentedOption
        :on="write"
        @click="write = !write"
      >
        write
      </SegmentedOption>
    </SegmentedControl>
    <MdButton
      variant="tonal"
      @click="$emit('submit')"
    >
      {{ submitLabel }}
    </MdButton>
    <MdButton
      variant="text"
      @click="$emit('cancel')"
    >
      Cancel
    </MdButton>
    <MdButton
      v-if="error?.existing"
      variant="text"
      @click="$emit('edit-existing')"
    >
      Edit existing route
    </MdButton>
    <p
      v-if="warning"
      role="status"
      class="md-body-small basis-full px-4 text-warning"
    >
      {{ warning }}
    </p>
    <p
      v-if="error && !pathError"
      role="alert"
      class="md-body-small basis-full px-4 text-error"
    >
      {{ error.message }}
    </p>
  </TokenFormRow>
</template>
