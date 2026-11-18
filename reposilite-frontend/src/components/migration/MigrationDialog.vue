<script setup lang="ts">
/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// A confirmation of the import wizard. The frame, the scrim, the focus trap, Escape and the return of focus come from
// MdDialog; this file only names the dialog and lays out its title, text and actions.
import MdDialog from '../md/MdDialog.vue'
import MdDialogActions from '../md/MdDialogActions.vue'

defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  titleId: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  }
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<template>
  <MdDialog
    :model-value="modelValue"
    :labelledby="titleId"
    size="md"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div data-ingot-component="migration-dialog">
      <h2
        :id="titleId"
        class="md-headline-small text-on-surface"
      >
        {{ title }}
      </h2>
      <div class="mt-3 md-body-medium text-on-surface-variant">
        <slot />
      </div>
      <MdDialogActions class="mt-6">
        <slot name="actions" />
      </MdDialogActions>
    </div>
  </MdDialog>
</template>
