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
import MdIconButton from '../../md/MdIconButton.vue'
import SegmentedControl from '../../tokens/SegmentedControl.vue'
import SegmentedOption from '../../tokens/SegmentedOption.vue'

/*
 * One permission node: the node in monospace, an Allow/Deny choice and a remove button. `label`
 * replaces the visible text (the tree shows the last segment) while the full node still names the
 * controls. A read only row shows the state as text and has no controls. `error` is a message the
 * server gave for this row.
 */
defineProps({
  entry: {
    type: Object,
    required: true
  },
  label: {
    type: String,
    default: ''
  },
  readonly: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['toggle', 'remove'])
</script>

<template>
  <div
    class="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2"
    :data-node="entry.node"
    :data-allowed="entry.allowed ? 'true' : 'false'"
  >
    <div class="min-w-0 flex-1 basis-56">
      <code
        class="break-all font-mono md-body-medium text-on-surface"
        :title="entry.node"
      >{{ label || entry.node }}</code>
      <p
        v-if="error"
        class="md-body-small text-error"
        data-test="row-error"
      >
        {{ error }}
      </p>
    </div>
    <span
      v-if="readonly"
      class="md-label-large"
      :class="entry.allowed ? 'text-on-surface' : 'text-error'"
      data-test="row-state"
    >{{ entry.allowed ? 'Allowed' : 'Denied' }}</span>
    <template v-else>
      <div class="w-44 shrink-0">
        <SegmentedControl :label="`Access for ${entry.node}`">
          <SegmentedOption
            :on="entry.allowed"
            @click="emit('toggle', true)"
          >
            Allow
          </SegmentedOption>
          <SegmentedOption
            :on="!entry.allowed"
            @click="emit('toggle', false)"
          >
            Deny
          </SegmentedOption>
        </SegmentedControl>
      </div>
      <MdIconButton
        variant="error"
        :aria-label="`Remove ${entry.node}`"
        data-test="remove-node"
        @click="emit('remove')"
      >
        <svg
          viewBox="0 -960 960 960"
          aria-hidden="true"
          focusable="false"
        >
          <path
            fill="currentColor"
            d="M280-120q-33 0-56.5-23.5T200-200v-520h-40v-80h200v-40h240v40h200v80h-40v520q0 33-23.5 56.5T680-120H280Zm400-600H280v520h400v-520ZM360-280h80v-360h-80v360Zm160 0h80v-360h-80v360ZM280-720v520-520Z"
          />
        </svg>
      </MdIconButton>
    </template>
  </div>
</template>
