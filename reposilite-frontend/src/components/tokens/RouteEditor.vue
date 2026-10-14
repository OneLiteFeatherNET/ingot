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
import { computed } from 'vue'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import SegmentedControl from './SegmentedControl.vue'
import SegmentedOption from './SegmentedOption.vue'
import TokenFormRow from './TokenFormRow.vue'

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
  }
})

defineEmits(['submit', 'cancel', 'edit-existing'])

const pathError = computed(() => !!props.error && props.error.field !== 'access')
</script>

<template>
  <TokenFormRow indent>
    <div class="min-w-48 flex-1">
      <MdTextField
        v-model="path"
        name="route-path"
        label="Route path"
        variant="outlined"
        :supporting-text="pathError ? '' : 'Starts with a slash, for example /releases/com/example/artifact'"
        :error="pathError"
        :error-text="pathError ? error.message : ''"
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
      v-if="error && !pathError"
      role="alert"
      class="md-body-small basis-full px-4 text-error"
    >
      {{ error.message }}
    </p>
  </TokenFormRow>
</template>
