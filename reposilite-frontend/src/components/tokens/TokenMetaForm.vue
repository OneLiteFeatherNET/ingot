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

const description = defineModel('description', { type: String, default: '' })
const manager = defineModel('manager', { type: Boolean, default: false })
const expiry = defineModel('expiry', { type: String, default: '' })

defineProps({
  /** Today, in the format the date input speaks. Nothing expires in the past. */
  minExpiry: {
    type: String,
    required: true
  }
})

defineEmits(['submit', 'cancel'])
</script>

<template>
  <TokenFormRow>
    <TokenTextField v-model="description" grow placeholder="Description" />
    <SegmentedControl label="Token permissions">
      <SegmentedOption :on="manager" @click="manager = !manager">
        manager
      </SegmentedOption>
    </SegmentedControl>
    <span class="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">expires</span>
    <TokenTextField v-model="expiry" type="date" :min="minExpiry" />
    <ActionButton v-if="expiry" small @click="expiry = ''">
      clear
    </ActionButton>
    <ActionButton primary small @click="$emit('submit')">
      Save
    </ActionButton>
    <ActionButton small @click="$emit('cancel')">
      Cancel
    </ActionButton>
  </TokenFormRow>
</template>
