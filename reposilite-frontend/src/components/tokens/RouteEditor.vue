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
import ActionButton from './ActionButton.vue'
import SegmentedControl from './SegmentedControl.vue'
import SegmentedOption from './SegmentedOption.vue'
import TokenFormRow from './TokenFormRow.vue'
import TokenTextField from './TokenTextField.vue'

const path = defineModel('path', { type: String, default: '' })
const read = defineModel('read', { type: Boolean, default: false })
const write = defineModel('write', { type: Boolean, default: false })

defineProps({
  /** Adding a route and editing one differ in this word and in the placeholder alone. */
  submitLabel: {
    type: String,
    required: true
  },
  placeholder: {
    type: String,
    default: null
  }
})

defineEmits(['submit', 'cancel'])
</script>

<template>
  <TokenFormRow indent>
    <TokenTextField
      v-model="path"
      grow
      mono
      :placeholder="placeholder"
    />
    <SegmentedControl label="Route access">
      <SegmentedOption :on="read" @click="read = !read">
        read
      </SegmentedOption>
      <SegmentedOption :on="write" @click="write = !write">
        write
      </SegmentedOption>
    </SegmentedControl>
    <ActionButton primary small @click="$emit('submit')">
      {{ submitLabel }}
    </ActionButton>
    <ActionButton small @click="$emit('cancel')">
      Cancel
    </ActionButton>
  </TokenFormRow>
</template>
