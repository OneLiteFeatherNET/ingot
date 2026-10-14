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

const name = defineModel('name', { type: String, default: '' })
const type = defineModel('type', { type: String, default: 'PERSISTENT' })

defineProps({
  /** Message for the name field, set by the view after a failed check. */
  error: {
    type: String,
    default: ''
  }
})

defineEmits(['submit', 'cancel'])
</script>

<template>
  <TokenFormRow>
    <TokenTextField
      v-model="name"
      name="token-name"
      grow
      placeholder="Token name (e.g. ci-bot)"
      @keyup.enter="$emit('submit')"
    />
    <SegmentedControl label="Token type">
      <SegmentedOption :on="type === 'PERSISTENT'" @click="type = 'PERSISTENT'">
        persistent
      </SegmentedOption>
      <SegmentedOption :on="type === 'TEMPORARY'" @click="type = 'TEMPORARY'">
        temporary
      </SegmentedOption>
    </SegmentedControl>
    <ActionButton primary small @click="$emit('submit')">
      Generate
    </ActionButton>
    <ActionButton small @click="$emit('cancel')">
      Cancel
    </ActionButton>
    <p class="basis-full text-xs text-gray-600 dark:text-gray-300">
      Persistent tokens are stored and survive a restart. Temporary tokens live in memory only and are lost on restart.
    </p>
    <p v-if="error" role="alert" class="basis-full text-xs text-error">
      {{ error }}
    </p>
  </TokenFormRow>
</template>
