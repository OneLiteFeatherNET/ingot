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
import MdButton from '../../md/MdButton.vue'
import MdTextField from '../../md/MdTextField.vue'
import type { Remote } from '../repositoryWizard'

/*
 * The address and the optional login of one remote repository of a proxy. It reports each edit as
 * a field and a value and reports the removal, so the list itself stays in the wizard.
 */
defineProps<{
  remote: Remote
  index: number
  errors: Record<string, string | undefined>
  removable: boolean
}>()

defineEmits<{
  edit: [field: keyof Remote, value: string]
  remove: []
}>()

// Bound rather than written in the template: the type check reads the "//" of an address in a plain attribute as a comment.
const REMOTE_URL_HINT = 'An http or https address, such as https://repo.example.net/maven.'
</script>

<template>
  <div
    class="flex flex-col gap-4 rounded-corner-medium bg-surface-container p-4"
    data-ingot-component="wizard-remote-fields"
  >
    <MdTextField
      :id="`wizard-remote-url-${index}`"
      :model-value="remote.url"
      :label="`Address ${index + 1}`"
      variant="outlined"
      autocomplete="off"
      inputmode="url"
      :supporting-text="REMOTE_URL_HINT"
      :error="Boolean(errors[`remote${index}`])"
      :error-text="errors[`remote${index}`] ?? ''"
      @update:model-value="$emit('edit', 'url', $event)"
    />
    <MdTextField
      :id="`wizard-remote-login-${index}`"
      :model-value="remote.login"
      :label="`Login ${index + 1}`"
      variant="outlined"
      autocomplete="off"
      supporting-text="Optional. The remote's user name."
      :error="Boolean(errors[`login${index}`])"
      :error-text="errors[`login${index}`] ?? ''"
      @update:model-value="$emit('edit', 'login', $event)"
    />
    <MdTextField
      :id="`wizard-remote-password-${index}`"
      :model-value="remote.password"
      :label="`Password ${index + 1}`"
      type="password"
      variant="outlined"
      autocomplete="new-password"
      supporting-text="Optional. Masked here and in the settings."
      :error="Boolean(errors[`password${index}`])"
      :error-text="errors[`password${index}`] ?? ''"
      @update:model-value="$emit('edit', 'password', $event)"
    />
    <div v-if="removable">
      <MdButton
        variant="text"
        type="button"
        :aria-label="`Remove address ${index + 1}`"
        @click="$emit('remove')"
      >
        Remove
      </MdButton>
    </div>
  </div>
</template>
