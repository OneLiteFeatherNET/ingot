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
import BrowserIcon from './BrowserIcon.vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdSwitch from '../md/MdSwitch.vue'

const { reversedFileOrder, displayHashFiles } = useAdjustments()
const showAdjustments = ref(false)
const titleId = useId()
</script>

<template>
  <div>
    <MdIconButton
      aria-label="File browser adjustments"
      title="File browser adjustments"
      @click="showAdjustments = true"
    >
      <BrowserIcon name="tune" />
    </MdIconButton>
    <VueFinalModal
      v-model="showAdjustments"
      teleport-to="body"
      :overlay-class="'vfm__overlay bg-scrim/32!'"
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
          <MdSwitch
            v-model="reversedFileOrder"
            class="min-h-16 justify-between py-3 flex-row-reverse"
          >
            <span>Sort files from newest to oldest</span>
          </MdSwitch>

          <MdSwitch
            v-model="displayHashFiles"
            class="min-h-16 justify-between py-3 flex-row-reverse"
          >
            <span>
              Display utility files such as
              <span class="font-mono text-on-surface-variant">.asc/.md5/.sha1/.sha256/.sha512</span>
            </span>
          </MdSwitch>
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
