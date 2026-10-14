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
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
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
    <span class="md-body-small max-w-xs text-on-surface-variant">
      {{ confirmAction === 'revoke' ? 'Revoking cannot be undone.' : 'The old secret stops working immediately.' }}
    </span>
    <MdButton
      v-if="confirmAction === 'revoke'"
      variant="text"
      class="text-error!"
      @click="$emit('confirm')"
    >
      Revoke
    </MdButton>
    <MdButton
      v-else
      variant="tonal"
      @click="$emit('confirm')"
    >
      Regenerate
    </MdButton>
    <MdButton
      :variant="confirmAction === 'revoke' ? 'tonal' : 'text'"
      @click="$emit('cancel')"
    >
      Cancel
    </MdButton>
  </template>
  <template v-else>
    <MdIconButton
      :aria-label="`Edit token ${name}`"
      :title="`Edit token ${name}`"
      @click="$emit('edit')"
    >
      <PencilIcon />
    </MdIconButton>
    <MdIconButton
      :aria-label="`Regenerate the secret of token ${name}`"
      :title="`Regenerate the secret of token ${name}`"
      @click="$emit('regenerate')"
    >
      <RefreshIcon />
    </MdIconButton>
    <MdIconButton
      variant="error"
      :aria-label="`Revoke token ${name}`"
      :title="`Revoke token ${name}`"
      @click="$emit('revoke')"
    >
      <TrashIcon />
    </MdIconButton>
  </template>
</template>
