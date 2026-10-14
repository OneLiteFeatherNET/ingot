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
import { ref, useId } from 'vue'
import { VueFinalModal } from 'vue-final-modal'
import { useAdjustments } from '../../store/adjustments'
import AdjustmentsIcon from '../icons/AdjustmentsIcon.vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'

const { reversedFileOrder, displayHashFiles } = useAdjustments()
const showAdjustments = ref(false)
const titleId = useId()
</script>

<template>
  <div>
    <MdIconButton
      aria-label="File browser adjustments"
      @click="showAdjustments = true"
    >
      <AdjustmentsIcon />
    </MdIconButton>
    <VueFinalModal
      v-model="showAdjustments"
      :overlay-class="'vfm__overlay bg-scrim/40!'"
      class="flex items-center justify-center p-4"
    >
      <div
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        class="w-full max-w-lg rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
      >
        <h2
          :id="titleId"
          class="md-headline-small"
        >
          File browser adjustments
        </h2>

        <div class="mt-4 divide-y divide-outline-variant">
          <label class="flex min-h-16 cursor-pointer items-center justify-between gap-6 py-3">
            <span class="md-body-large">Sort files from newest to oldest</span>
            <span class="relative inline-flex h-8 w-13 shrink-0 items-center">
              <input
                v-model="reversedFileOrder"
                type="checkbox"
                role="switch"
                class="peer sr-only"
              >
              <span class="absolute inset-0 rounded-corner-full border-2 border-outline bg-surface-container-highest transition-colors peer-checked:border-primary peer-checked:bg-primary peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary" />
              <span class="absolute left-1.5 size-4 rounded-corner-full bg-outline transition-all peer-checked:translate-x-5 peer-checked:size-6 peer-checked:bg-on-primary" />
            </span>
          </label>

          <label class="flex min-h-16 cursor-pointer items-center justify-between gap-6 py-3">
            <span class="md-body-large">
              Display utility files such as
              <span class="font-mono text-on-surface-variant">.asc/.md5/.sha1/.sha256/.sha512</span>
            </span>
            <span class="relative inline-flex h-8 w-13 shrink-0 items-center">
              <input
                v-model="displayHashFiles"
                type="checkbox"
                role="switch"
                class="peer sr-only"
              >
              <span class="absolute inset-0 rounded-corner-full border-2 border-outline bg-surface-container-highest transition-colors peer-checked:border-primary peer-checked:bg-primary peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary" />
              <span class="absolute left-1.5 size-4 rounded-corner-full bg-outline transition-all peer-checked:translate-x-5 peer-checked:size-6 peer-checked:bg-on-primary" />
            </span>
          </label>
        </div>

        <div class="mt-6 flex justify-end">
          <MdButton
            variant="text"
            @click="showAdjustments = false"
          >
            Done
          </MdButton>
        </div>
      </div>
    </VueFinalModal>
  </div>
</template>
