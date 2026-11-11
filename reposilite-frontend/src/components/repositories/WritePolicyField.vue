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
import { computed, useId } from 'vue'
import MdRadio from '../md/MdRadio.vue'
import { WRITE_CHOICES } from './writePolicy'

/*
 * The write policy of a hosted repository as one radio group. Each choice has its help text under it, linked to its
 * radio, so the explanation is read with the choice. Read only is offered only when the schema has the readOnly field.
 * The value is the choice, and the page turns it into the stored keys (writePolicyChange).
 */
const props = defineProps({
  modelValue: {
    type: String,
    required: true
  },
  offerReadOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const name = `write-policy-${useId()}`

// The help line of each choice names the status code the choice answers.
const HELP = {
  allow: 'Deploys are accepted.',
  allowOnce: 'The first deploy of each file is accepted; a later deploy of the same file is refused with 409.',
  readOnly: 'Deploys and deletes are refused with 405 Method Not Allowed. Reads are not affected. If the instance is in read-only mode, every repository answers 503 first.'
}

const choices = computed(() => WRITE_CHOICES
  .filter(choice => choice.value !== 'readOnly' || props.offerReadOnly)
  .map(choice => ({ ...choice, help: HELP[choice.value], helpId: `${name}-${choice.value}-help` })))
</script>

<template>
  <fieldset
    class="flex flex-col gap-4 rounded-corner-medium bg-surface-container-low p-4"
    data-test="write-policy"
  >
    <legend class="md-title-medium text-on-surface">
      Write policy
    </legend>
    <div
      v-for="choice in choices"
      :key="choice.value"
      class="flex flex-col gap-1"
    >
      <MdRadio
        :model-value="modelValue"
        :value="choice.value"
        :name="name"
        :aria-describedby="choice.helpId"
        @update:model-value="emit('update:modelValue', $event)"
      >
        {{ choice.label }}
      </MdRadio>
      <p
        :id="choice.helpId"
        class="ml-12 md-body-medium text-on-surface-variant"
      >
        {{ choice.help }}
      </p>
    </div>
  </fieldset>
</template>
