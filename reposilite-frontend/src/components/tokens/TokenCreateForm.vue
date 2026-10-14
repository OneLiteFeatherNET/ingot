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
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import SegmentedControl from './SegmentedControl.vue'
import SegmentedOption from './SegmentedOption.vue'
import TokenFormRow from './TokenFormRow.vue'

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
    <div class="min-w-48 flex-1">
      <MdTextField
        v-model="name"
        name="token-name"
        label="Token name"
        :error="!!error"
        :error-text="error"
        :supporting-text="error ? '' : 'For example ci-bot. Names cannot contain : or /.'"
        @keyup.enter="$emit('submit')"
      />
    </div>
    <SegmentedControl label="Token type">
      <SegmentedOption
        :on="type === 'PERSISTENT'"
        @click="type = 'PERSISTENT'"
      >
        persistent
      </SegmentedOption>
      <SegmentedOption
        :on="type === 'TEMPORARY'"
        @click="type = 'TEMPORARY'"
      >
        temporary
      </SegmentedOption>
    </SegmentedControl>
    <MdButton
      variant="tonal"
      @click="$emit('submit')"
    >
      Generate
    </MdButton>
    <MdButton
      variant="text"
      @click="$emit('cancel')"
    >
      Cancel
    </MdButton>
    <p class="md-body-small basis-full text-on-surface-variant">
      Persistent tokens are stored and survive a restart. Temporary tokens live in memory only and are lost on restart.
    </p>
  </TokenFormRow>
</template>
