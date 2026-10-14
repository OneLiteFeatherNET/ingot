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
import { ref } from 'vue'
import CopiedIcon from '../icons/CopiedIcon.vue'
import CopyIcon from '../icons/CopyIcon.vue'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'

/*
 * Shows a freshly generated or regenerated secret until the user dismisses it. The view keeps
 * the secret in the token store, so it survives switching tabs. It is a status region, so a
 * screen reader announces it when it appears and when the copy result changes.
 */
const props = defineProps({
  name: {
    type: String,
    required: true
  },
  value: {
    type: String,
    required: true
  }
})

defineEmits(['dismiss'])

const copyResult = ref('')

const copy = async () => {
  try {
    await navigator.clipboard.writeText(props.value)
    copyResult.value = 'copied'
  } catch {
    copyResult.value = 'failed'
  }
}
</script>

<template>
  <div
    role="status"
    class="flex flex-col gap-3 border-b border-outline-variant bg-primary-container px-4 py-4 text-on-primary-container"
  >
    <p class="md-title-small">
      New secret for <span class="font-semibold">{{ name }}</span>
    </p>
    <div class="flex flex-wrap items-center gap-2">
      <code class="md-body-large min-w-0 flex-1 break-all rounded-corner-small bg-surface px-3 py-2 font-mono text-on-surface">{{ value }}</code>
      <MdIconButton
        :aria-label="`Copy the secret of token ${name}`"
        :title="`Copy the secret of token ${name}`"
        @click="copy"
      >
        <CopiedIcon v-if="copyResult === 'copied'" />
        <CopyIcon v-else />
      </MdIconButton>
    </div>
    <p class="md-body-medium">
      You won't see this secret again. Copy it now and keep it somewhere safe.
    </p>
    <p
      v-if="copyResult === 'copied'"
      class="md-body-small"
    >
      Copied to the clipboard.
    </p>
    <p
      v-else-if="copyResult === 'failed'"
      class="md-body-small"
    >
      Copying failed. Select the secret and copy it by hand.
    </p>
    <div>
      <MdButton
        variant="text"
        @click="$emit('dismiss')"
      >
        Dismiss
      </MdButton>
    </div>
  </div>
</template>
