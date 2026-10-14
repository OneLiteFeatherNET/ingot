<!--
  - Copyright (c) 2023 dzikoysk
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
import { VueFinalModal } from 'vue-final-modal'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import { computed, useId } from 'vue'
import useQualifier from '../../store/qualifier'
import { property } from '../../helpers/vue-extensions'
import MdButton from '../md/MdButton.vue'

const props = defineProps({
  value: property(Object, false),
  close: property(Function, true)
})

const { client } = useSession()
const { refreshQualifier } = useQualifier()

const titleId = useId()
const bodyId = useId()

const deleteEntry = ({ path, file }) => {
  client.value.maven.delete(path + '/' + file)
    .then(() => refreshQualifier())
    .catch(error => createErrorToast(`Cannot delete file: ${errorMessage(error)}`))
}

const deleteAndClose = () => {
  deleteEntry(props.value)
  props.close()
}

const isOpen = computed(() => props.value != undefined)
</script>

<template>
  <div id="delete-modal">
    <VueFinalModal
      v-if="isOpen"
      :model-value="isOpen"
      :overlay-class="'vfm__overlay bg-scrim/40!'"
      class="flex items-center justify-center p-4"
      @update:model-value="(open) => { if (!open) close() }"
    >
      <div
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="bodyId"
        class="w-full max-w-md rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      >
        <h2
          :id="titleId"
          class="md-headline-small"
        >
          Delete this entry?
        </h2>
        <p
          :id="bodyId"
          class="mt-4 md-body-medium text-on-surface-variant"
        >
          <span class="break-all font-mono text-on-surface">{{ '/' + value.path + '/' + value.file }}</span>
        </p>
        <div class="mt-6 flex justify-end gap-2">
          <MdButton
            variant="text"
            @click="close()"
          >
            Cancel
          </MdButton>
          <button
            type="button"
            class="md-state-layer md-focus-ring inline-flex min-h-12 items-center justify-center rounded-corner-full bg-error px-6 md-label-large text-on-error"
            @click="deleteAndClose()"
          >
            Confirm
          </button>
        </div>
      </div>
    </VueFinalModal>
  </div>
</template>
