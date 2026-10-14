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

const description = defineModel('description', { type: String, default: '' })
const manager = defineModel('manager', { type: Boolean, default: false })
const expiry = defineModel('expiry', { type: String, default: '' })

defineProps({
  /** Set by the view when the chosen date is in the past. The view checks it again on save. */
  expiryError: {
    type: String,
    default: ''
  },
  /** The earliest date the picker offers, as yyyy-mm-dd. Empty means no lower bound. */
  expiryMin: {
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
        v-model="description"
        name="description"
        label="Description"
        variant="outlined"
      />
    </div>
    <SegmentedControl label="Token permissions">
      <SegmentedOption
        :on="manager"
        @click="manager = !manager"
      >
        manager
      </SegmentedOption>
    </SegmentedControl>
    <div class="w-48">
      <MdTextField
        v-model="expiry"
        name="expires"
        label="Expires on"
        type="date"
        variant="outlined"
        :min="expiryMin || undefined"
        :error="!!expiryError"
        :error-text="expiryError"
      />
    </div>
    <MdButton
      v-if="expiry"
      variant="text"
      @click="expiry = ''"
    >
      Clear expiry
    </MdButton>
    <MdButton
      variant="tonal"
      @click="$emit('submit')"
    >
      Save
    </MdButton>
    <MdButton
      variant="text"
      @click="$emit('cancel')"
    >
      Cancel
    </MdButton>
  </TokenFormRow>
</template>
