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
import { computed, ref, useId } from 'vue'
import MdButton from '../../md/MdButton.vue'
import MdTextField from '../../md/MdTextField.vue'
import { artifactIdProblem, groupIdProblem } from './artifactNodes'
import { isRepositorySegment, parseNode } from './nodeGrammar'
import { repositoryNames } from './nodeList'
import { applyTemplate, fillTemplate, needsArtifact, needsRepository } from './nodeTemplates'

/*
 * Adds the nodes of a template. The button opens a panel with the templates. A template that has a
 * {repository} placeholder asks for the repository next (a text field with the repositories of the
 * catalog as suggestions) and fills the placeholder. A template with {groupId} or {artifactId} asks
 * for the groupId (required) and the artifactId (optional) in the same form, and an empty artifactId
 * leaves out its colon. The instance admin template has no placeholder and is added at once. Every
 * filled node is checked against the grammar before the form can add it, so the form never adds a
 * node the server would refuse. Only the nodes that are not listed yet are added, all as Allow, and
 * a status line says how many were added and how many were already there.
 */
const props = defineProps({
  /** [{ id, label, nodes }] */
  templates: {
    type: Array,
    default: () => []
  },
  catalog: {
    type: Array,
    default: () => []
  },
  /** The nodes already listed (the draft). */
  nodes: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['apply'])

const uid = useId()
const panelId = `template-panel-${uid}`
const datalistId = `template-repositories-${uid}`

const open = ref(false)
const chosen = ref(null)
const repository = ref('')
const groupId = ref('')
const artifactId = ref('')
const notice = ref('')

const trimmed = computed(() => repository.value.trim())
const group = computed(() => groupId.value.trim())
const artifact = computed(() => artifactId.value.trim())
const names = computed(() => repositoryNames(props.catalog))
const asksRepository = computed(() => chosen.value !== null && needsRepository(chosen.value))
const asksArtifact = computed(() => chosen.value !== null && needsArtifact(chosen.value))

const repositoryError = computed(() => {
  if (trimmed.value === '') return ''
  return isRepositorySegment(trimmed.value) ? '' : "Use letters, digits, '_' and '-', or '*'"
})
const groupError = computed(() => (asksArtifact.value && group.value !== '' ? groupIdProblem(group.value) : ''))
const artifactError = computed(() => (asksArtifact.value ? artifactIdProblem(artifact.value, group.value) : ''))

const coordinates = computed(() => ({ groupId: group.value, artifactId: artifact.value }))
const filled = computed(() => (chosen.value ? fillTemplate(chosen.value, trimmed.value, coordinates.value) : []))
// The first filled node the grammar refuses, once every field is filled in and valid.
const nodeError = computed(() => {
  if (!chosen.value || repositoryError.value !== '' || groupError.value !== '' || artifactError.value !== '') return ''
  for (const node of filled.value) {
    const parsed = parseNode(node)
    if (!parsed.valid) return parsed.problems[0].message
  }
  return ''
})
const fieldsReady = computed(() =>
  (!asksRepository.value || (trimmed.value !== '' && repositoryError.value === '')) &&
  (!asksArtifact.value || (group.value !== '' && groupError.value === '' && artifactError.value === '')))
const canApply = computed(() => chosen.value !== null && fieldsReady.value && nodeError.value === '')
const preview = computed(() => (canApply.value ? filled.value.join(', ') : ''))

const close = () => {
  open.value = false
  chosen.value = null
  repository.value = ''
  groupId.value = ''
  artifactId.value = ''
}

const toggle = () => {
  if (open.value) close()
  else open.value = true
}

const apply = (template, repositoryName, coordinatesOf) => {
  const { added, skipped } = applyTemplate(template, repositoryName, props.nodes, coordinatesOf)
  if (added.length > 0) emit('apply', added)
  const parts = [`Added ${added.length} ${added.length === 1 ? 'node' : 'nodes'}`]
  if (skipped.length > 0) parts.push(`${skipped.length} already listed`)
  notice.value = parts.join(', ')
  close()
}

const choose = (template) => {
  if (needsRepository(template) || needsArtifact(template)) {
    chosen.value = template
    repository.value = ''
    groupId.value = ''
    artifactId.value = ''
    notice.value = ''
  } else {
    apply(template, '', {})
  }
}

const submit = () => {
  if (chosen.value && canApply.value) apply(chosen.value, trimmed.value, coordinates.value)
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="flex flex-wrap items-center gap-3">
      <MdButton
        variant="tonal"
        :aria-expanded="open ? 'true' : 'false'"
        :aria-controls="panelId"
        data-test="template-toggle"
        @click="toggle"
      >
        Add from template
      </MdButton>
      <p
        role="status"
        class="md-body-medium text-on-surface-variant"
        data-test="template-notice"
      >
        {{ notice }}
      </p>
    </div>
    <div
      v-if="open"
      :id="panelId"
      class="flex flex-col gap-3 rounded-corner-medium bg-surface-container-low p-4"
      data-test="template-panel"
    >
      <ul
        v-if="!chosen"
        class="flex flex-col"
        aria-label="Templates"
      >
        <li
          v-for="template in templates"
          :key="template.id"
        >
          <button
            type="button"
            class="md-state-layer md-focus-ring relative flex min-h-12 w-full flex-col items-start justify-center rounded-corner-small px-3 py-2 text-left"
            data-test="template-option"
            @click="choose(template)"
          >
            <span class="md-label-large text-on-surface">{{ template.label }}</span>
            <span class="break-all font-mono md-body-small text-on-surface-variant">{{ template.nodes.join(', ') }}</span>
          </button>
        </li>
        <li
          v-if="templates.length === 0"
          class="px-3 py-2 md-body-medium text-on-surface-variant"
        >
          No templates are available.
        </li>
      </ul>
      <form
        v-else
        class="flex flex-col gap-3"
        @submit.prevent="submit"
      >
        <p class="md-title-small text-on-surface">
          {{ chosen.label }}
        </p>
        <template v-if="asksRepository">
          <MdTextField
            v-model="repository"
            label="Repository"
            variant="outlined"
            autocomplete="off"
            spellcheck="false"
            :list="datalistId"
            :error="repositoryError !== ''"
            :error-text="repositoryError"
            :supporting-text="asksArtifact ? 'Choose a repository or type its name' : (preview ? `Adds ${preview}` : 'Choose a repository or type its name')"
            data-test="template-repository"
          />
          <datalist :id="datalistId">
            <option
              v-for="name in names"
              :key="name"
              :value="name"
            />
          </datalist>
        </template>
        <template v-if="asksArtifact">
          <MdTextField
            v-model="groupId"
            label="groupId"
            variant="outlined"
            autocomplete="off"
            spellcheck="false"
            :error="groupError !== ''"
            :error-text="groupError"
            supporting-text="Required, for example net.onelitefeather. The groupId covers its subgroups"
            data-test="template-group"
          />
          <MdTextField
            v-model="artifactId"
            label="artifactId (optional)"
            variant="outlined"
            autocomplete="off"
            spellcheck="false"
            :error="artifactError !== ''"
            :error-text="artifactError"
            supporting-text="Leave empty for every artifact of the groupId"
            data-test="template-artifact"
          />
        </template>
        <p
          v-if="preview"
          class="break-all font-mono md-body-small text-on-surface-variant"
          data-test="template-preview"
        >
          Adds {{ preview }}
        </p>
        <p
          v-if="nodeError"
          role="alert"
          class="md-body-small text-error"
          data-test="template-problem"
        >
          {{ nodeError }}
        </p>
        <div class="flex justify-end gap-2">
          <MdButton
            variant="text"
            @click="close"
          >
            Cancel
          </MdButton>
          <MdButton
            variant="tonal"
            type="submit"
            :disabled="!canApply"
            data-test="template-apply"
          >
            Add nodes
          </MdButton>
        </div>
      </form>
    </div>
  </div>
</template>
