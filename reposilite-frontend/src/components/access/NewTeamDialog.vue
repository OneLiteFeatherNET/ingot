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
import { computed, ref } from 'vue'
import { errorMessage } from '../../helpers/toast'
import MdButton from '../md/MdButton.vue'
import MdTextField from '../md/MdTextField.vue'
import DialogShell from '../identity/DialogShell.vue'
import { buildTeamCreateBody, validateTeamName } from './accessTeams'

/*
 * Creates a team: a name, and optionally a description and an external reference (the group of
 * an identity provider the team stands for).
 *
 * Props:
 *   modelValue  whether the dialog is open (v-model)
 *   api         { create(body) }
 * Emits update:modelValue and created (the name of the new team).
 */
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  api: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'created'])

const name = ref('')
const description = ref('')
const externalRef = ref('')
const submitted = ref(false)
const busy = ref(false)
const failure = ref('')

const nameProblem = computed(() => validateTeamName(name.value))

const reset = () => {
  name.value = ''
  description.value = ''
  externalRef.value = ''
  submitted.value = false
  busy.value = false
  failure.value = ''
}

const submit = async () => {
  submitted.value = true
  failure.value = ''
  if (nameProblem.value) return
  busy.value = true
  const body = buildTeamCreateBody({
    name: name.value,
    description: description.value,
    externalRef: externalRef.value
  })
  try {
    await props.api.create(body)
  } catch (error) {
    failure.value = errorMessage(error)
    busy.value = false
    return
  }
  busy.value = false
  emit('created', body.name)
  emit('update:modelValue', false)
}
</script>

<template>
  <DialogShell
    :model-value="modelValue"
    labelledby="new-team-title"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <h2
      id="new-team-title"
      class="md-headline-small"
    >
      New team
    </h2>
    <p class="mt-2 md-body-large text-on-surface-variant">
      A team groups users and machine accounts. Give it permission nodes afterwards.
    </p>

    <form
      id="new-team-form"
      class="mt-6 flex flex-col gap-4"
      novalidate
      @submit.prevent="submit()"
    >
      <MdTextField
        id="new-team-name"
        v-model="name"
        label="Name"
        type="text"
        autocomplete="off"
        required
        :error="submitted && nameProblem !== ''"
        :error-text="nameProblem"
      />
      <MdTextField
        id="new-team-description"
        v-model="description"
        label="Description (optional)"
        type="text"
        autocomplete="off"
      />
      <MdTextField
        id="new-team-external-ref"
        v-model="externalRef"
        label="External reference (optional)"
        type="text"
        autocomplete="off"
        supporting-text="The group of your identity provider this team stands for."
      />
      <p
        v-if="failure"
        role="alert"
        data-test="create-failure"
        class="md-body-medium text-error"
      >
        {{ failure }}
      </p>
    </form>

    <div class="mt-6 flex justify-end gap-2">
      <MdButton
        variant="text"
        @click="emit('update:modelValue', false)"
      >
        Cancel
      </MdButton>
      <MdButton
        type="submit"
        form="new-team-form"
        data-test="create-team"
        :disabled="busy"
      >
        Create team
      </MdButton>
    </div>
  </DialogShell>
</template>
