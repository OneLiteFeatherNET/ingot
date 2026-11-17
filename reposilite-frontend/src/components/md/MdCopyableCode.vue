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
import { ref } from 'vue'
import { copyToClipboard, selectText } from '../card/copySnippet'
import MdCodeBlock from './MdCodeBlock.vue'
import MdIcon from './MdIcon.vue'
import MdIconButton from './MdIconButton.vue'

/*
 * A block of code with a copy action in its top right corner. The copy goes through the copy helper. When the clipboard
 * is refused, the code is selected instead, so the keyboard shortcut finishes the copy. The result is written to a polite
 * live region under the code. That region is in the page before any copy, so the first announcement is heard.
 *
 * Props:
 *   text       the text that is shown and copied
 *   label      the accessible name of the code area, for example "Gradle snippet"
 *   copyLabel  the accessible name of the copy button (default "Copy code")
 */
const props = withDefaults(defineProps<{
  text: string
  label: string
  copyLabel?: string
}>(), {
  copyLabel: 'Copy code'
})

// The copy glyph, the outline of components/icons/CopyIcon.vue.
const COPY_GLYPH = 'M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z'

const block = ref<{ codeElement?: HTMLElement } | null>(null)
const status = ref('')

// The text of the status line after the last copy. Empty until the first copy.
const copy = async () => {
  if (await copyToClipboard(props.text, navigator.clipboard)) {
    status.value = 'Copied'
    return
  }
  selectText(block.value?.codeElement)
  status.value = 'Copying is not available here. The code is selected: press Ctrl+C (Cmd+C on macOS) to copy it.'
}
</script>

<template>
  <div
    class="flex min-w-0 flex-col gap-2"
    data-ingot-component="md-copyable-code"
  >
    <MdCodeBlock
      ref="block"
      :label="label"
    >
      <template #actions>
        <MdIconButton
          :aria-label="copyLabel"
          @click="copy"
        >
          <MdIcon
            :path="COPY_GLYPH"
            size="md"
          />
        </MdIconButton>
      </template>
      <template #default>
        <span v-text="text" />
      </template>
    </MdCodeBlock>
    <p
      data-test="copy-status"
      role="status"
      aria-live="polite"
      class="md-label-medium text-on-surface-variant"
    >
      {{ status }}
    </p>
  </div>
</template>
