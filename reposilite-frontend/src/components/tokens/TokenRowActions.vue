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
import IconButton from './IconButton.vue'
import PencilIcon from '../icons/PencilIcon.vue'
import RefreshIcon from '../icons/RefreshIcon.vue'
import TrashIcon from '../icons/TrashIcon.vue'

defineProps({
  /** Every label names the token, because a screen reader reads the row out of context. */
  name: {
    type: String,
    required: true
  },
  /** While this is set the row asks the question instead of offering the three actions. */
  confirmAction: {
    type: String,
    default: null,
    validator: (value) => value === null || value === 'revoke' || value === 'regenerate'
  }
})

defineEmits(['edit', 'regenerate', 'revoke', 'confirm', 'cancel'])
</script>

<template>
  <template v-if="confirmAction">
    <button
      type="button"
      class="text-xs font-medium text-red-600 dark:text-red-400"
      @click="$emit('confirm')"
    >
      {{ confirmAction === 'revoke' ? 'Revoke' : 'Regenerate' }}
    </button>
    <button
      type="button"
      class="text-xs text-gray-500 dark:text-gray-400"
      @click="$emit('cancel')"
    >
      Cancel
    </button>
  </template>
  <template v-else>
    <IconButton :label="`Edit token ${name}`" @click="$emit('edit')">
      <PencilIcon />
    </IconButton>
    <IconButton :label="`Regenerate the secret of token ${name}`" @click="$emit('regenerate')">
      <RefreshIcon />
    </IconButton>
    <IconButton
      destructive
      :label="`Revoke token ${name}`"
      @click="$emit('revoke')"
    >
      <TrashIcon />
    </IconButton>
  </template>
</template>
