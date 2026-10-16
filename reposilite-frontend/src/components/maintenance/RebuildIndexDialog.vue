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
import { computed, ref, watch } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import MdSelect from '../md/MdSelect.vue'
import { SCRIM_STYLE } from '../settings/dialog'

/*
 * Confirms a rebuild of the search index. The scope is all repositories or one of them. The dialog
 * answers with the request body of POST /api/maven/search/index/rebuild: `{}` for all, otherwise
 * `{ repositories: [name] }`. A rebuild only reads storage, so the confirmation is a plain one.
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  /** The configured repository names the scope offers. */
  repositories: {
    type: Array,
    default: () => []
  },
  running: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const scope = ref('')

const options = computed(() => [
  { value: '', label: 'All repositories' },
  ...props.repositories.map(name => ({ value: name, label: name }))
])

watch(() => props.modelValue, (open) => {
  if (open) scope.value = ''
})

const close = () => emit('update:modelValue', false)

const confirm = () => {
  if (props.running) return
  emit('confirm', scope.value ? { repositories: [scope.value] } : {})
}
</script>

<template>
  <VueFinalModal
    :model-value="modelValue"
    teleport-to="body"
    :overlay-style="SCRIM_STYLE"
    class="flex min-h-full items-center justify-center p-4"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div
      class="w-[calc(100vw-2rem)] max-w-lg max-h-[90dvh] overflow-y-auto rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      role="dialog"
      aria-modal="true"
      aria-labelledby="search-index-rebuild-title"
      aria-describedby="search-index-rebuild-body"
    >
      <h2
        id="search-index-rebuild-title"
        class="md-headline-small text-on-surface"
      >
        Rebuild the search index?
      </h2>
      <p
        id="search-index-rebuild-body"
        class="mt-3 md-body-medium text-on-surface-variant"
      >
        A rebuild reads the files of the chosen repositories from storage again and corrects the index.
        It deletes nothing from storage. Search keeps working meanwhile, and results may be partial until
        the rebuild ends.
      </p>
      <div class="mt-4">
        <MdSelect
          id="search-index-scope"
          label="Repositories"
          :model-value="scope"
          :options="options"
          supporting-text="Choose one repository, or all of them."
          @update:model-value="scope = $event"
        />
      </div>
      <div class="mt-6 flex flex-wrap justify-end gap-2">
        <MdButton
          id="search-index-rebuild-cancel"
          variant="text"
          type="button"
          :disabled="running"
          @click="close"
        >
          Cancel
        </MdButton>
        <MdButton
          id="search-index-rebuild-confirm"
          variant="filled"
          type="button"
          :disabled="running"
          @click="confirm"
        >
          {{ running ? 'Starting…' : 'Rebuild' }}
        </MdButton>
      </div>
    </div>
  </VueFinalModal>
</template>
