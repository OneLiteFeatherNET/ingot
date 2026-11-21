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

<script setup lang="ts">
import { computed } from 'vue'
import MdOverflowMenu from '../md/MdOverflowMenu.vue'
import type { MenuItem } from '../md/types'

/*
 * The download of the settings. The default is the saved state, the last one the server confirmed.
 * The draft, edits included, is offered only when there are unsaved changes, and it is labelled as
 * such, because it is not a backup of what the server holds.
 */
const props = withDefaults(defineProps<{
  disabled?: boolean
  hasUnsaved?: boolean
  canIncludeUnsaved?: boolean
  secretsHidden?: boolean
}>(), {
  disabled: false,
  hasUnsaved: false,
  canIncludeUnsaved: true,
  secretsHidden: false
})

const emit = defineEmits<{
  saved: []
  draft: []
}>()

const items = computed<MenuItem[]>(() => [
  { value: 'saved', label: 'Download saved settings', supporting: 'The last state the server confirmed.' },
  ...(props.hasUnsaved
    ? [{
        value: 'draft',
        label: 'Download including unsaved changes',
        supporting: 'Includes edits that are not saved yet, so it is not a copy of the server state.',
        disabled: !props.canIncludeUnsaved
      }]
    : [])
])

const choose = (value: string) => {
  if (value === 'saved') emit('saved')
  else if (value === 'draft') emit('draft')
}
</script>

<template>
  <div
    class="shrink-0"
    data-ingot-component="settings-download-menu"
  >
    <!-- The shared menu: it is teleported to the body on the menu layer, and owns Escape, the arrow keys and the focus. -->
    <MdOverflowMenu
      label="Download options"
      :items="items"
      trigger-variant="outlined"
      :disabled="disabled"
      @select="choose"
    >
      <template #trigger>
        Download as JSON
      </template>
      <template
        v-if="secretsHidden"
        #header
      >
        <p class="md-body-small text-on-surface-variant">
          Secrets are not included. The server hides them, and the file holds ******** in their place.
        </p>
      </template>
    </MdOverflowMenu>
  </div>
</template>
