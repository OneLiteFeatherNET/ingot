<script setup>
/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Step 7: the checklist of the day the clients are switched. Only the verify item is derived from the server; the
// freeze of Nexus is done in Nexus and is text here, and the two manual ticks are not stored anywhere.
import { computed, inject, onMounted, ref } from 'vue'
import MdButton from '../md/MdButton.vue'
import MigrationDialog from './MigrationDialog.vue'
import ProblemAlert from './ProblemAlert.vue'
import { repositoryResultText } from './jobText'
import { isFinished } from '../../store/migration/gating'
import usePlaceholders from '../../store/placeholders'
import { migrationApi } from '../../store/migration/migrationApi'
import { MIGRATION_API, MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'

const api = inject(MIGRATION_API, migrationApi)
const wizard = inject(MIGRATION_WIZARD, useWizard())

// The two manual items. They live in this page only, so a reload clears them.
const freezeDone = ref(false)
const switchDone = ref(false)
const problem = ref(null)
const deleteOpen = ref(false)
const importConflicts = ref([])

const newest = (kind) => wizard.state.jobs.filter(job => job.kind === kind).sort((a, b) => b.id - a.id)[0] ?? null

const importJob = computed(() => newest('IMPORT'))
const verifyJob = computed(() => newest('VERIFY'))
const verifyFinished = computed(() => verifyJob.value !== null && isFinished(verifyJob.value))
const verifyRows = computed(() => verifyJob.value?.repositories ?? [])
const verified = computed(() => verifyFinished.value && verifyRows.value.length > 0 && verifyRows.value.every(row => row.status === 'VERIFIED'))
const blocking = computed(() => (verifyFinished.value ? verifyRows.value.filter(row => row.status !== 'VERIFIED') : []))

// The repositories the clients use: the included rows of the mapping, under the names Ingot gives them.
const targets = computed(() => {
  const included = (wizard.state.mapping?.repositories ?? []).filter(repository => repository.included)
  const rows = included.length > 0 ? included : verifyRows.value
  return rows.map(repository => repository.targetName ?? repository.nexusName)
})

const baseUrl = computed(() => (usePlaceholders().productionUrl ?? '').replace(/\/$/, ''))

// The snippets carry placeholders for the token, never a secret. Ingot does not put one into a snippet.
const mavenSnippet = (name) => [
  '<settings>',
  '  <mirrors>',
  '    <mirror>',
  `      <id>ingot-${name}</id>`,
  `      <name>Ingot ${name}</name>`,
  `      <url>${baseUrl.value}/${name}</url>`,
  '      <mirrorOf>external:*</mirrorOf>',
  '    </mirror>',
  '  </mirrors>',
  '  <servers>',
  '    <server>',
  `      <id>ingot-${name}</id>`,
  '      <username>YOUR_TOKEN_NAME</username>',
  '      <password>YOUR_TOKEN_SECRET</password>',
  '    </server>',
  '  </servers>',
  '</settings>',
].join('\n')

const gradleSnippet = (name) => [
  'repositories {',
  '    maven {',
  `        url "${baseUrl.value}/${name}"`,
  '        credentials {',
  '            username = "YOUR_TOKEN_NAME"',
  '            password = "YOUR_TOKEN_SECRET"',
  '        }',
  '    }',
  '}',
].join('\n')

const copy = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    problem.value = { title: 'Copy failed', detail: 'The browser did not allow the copy. Select the text and copy it by hand.' }
  }
}

const loadConflicts = async (id) => {
  try {
    importConflicts.value = await api.conflicts(id)
  } catch (failure) {
    problem.value = failure
  }
}

const removeVerifyJob = async () => {
  deleteOpen.value = false
  const id = verifyJob.value.id
  try {
    await api.deleteJob(id)
    wizard.setJobs(wizard.state.jobs.filter(job => job.id !== id))
  } catch (failure) {
    problem.value = failure
  }
}

onMounted(() => {
  if (importJob.value && blocking.value.length > 0) loadConflicts(importJob.value.id)
})
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-cutover-title"
    data-ingot-component="cut-over-step"
  >
    <h2
      id="migration-cutover-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Cut-over
    </h2>
    <p class="md-body-large text-on-surface-variant">
      Switching the clients is the last step. Work through the checklist in order.
    </p>

    <fieldset class="flex flex-col gap-5">
      <legend class="md-title-small text-on-surface">
        Checklist
      </legend>

      <div class="flex flex-col gap-1">
        <label class="flex items-center gap-2 md-body-large">
          <input
            id="migration-cutover-freeze"
            v-model="freezeDone"
            type="checkbox"
          >
          Freeze Nexus writes
        </label>
        <p class="md-body-medium text-on-surface-variant">
          In Nexus, set each repository you migrate to read-only, so no client writes to it during the switch. Ingot never
          writes to Nexus, so this is done in Nexus.
        </p>
      </div>

      <div class="flex flex-col gap-1">
        <label class="flex items-center gap-2 md-body-large">
          <input
            id="migration-cutover-verify"
            type="checkbox"
            :checked="verified"
            disabled
          >
          Verify all repositories
        </label>
        <p class="md-body-medium text-on-surface-variant">
          Set by the newest verify job. It is ticked when every repository is VERIFIED, and it cannot be ticked by hand.
        </p>
        <ul
          data-test="blocking"
          class="list-disc pl-6 md-body-medium text-on-surface"
        >
          <li v-if="!verifyFinished">
            Run the verify job first.
          </li>
          <li
            v-for="row in blocking"
            :key="row.nexusName"
          >
            {{ row.nexusName }} is not verified: {{ repositoryResultText(row.status) }}.
          </li>
          <li v-if="verifyFinished && blocking.length === 0">
            Every repository is verified.
          </li>
        </ul>
        <table
          v-if="importConflicts.length"
          class="w-full text-left md-body-medium"
        >
          <caption class="md-title-small pb-2 text-left text-on-surface">
            Conflicts that keep a repository from being verified
          </caption>
          <thead>
            <tr>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Repository
              </th>
              <th
                scope="col"
                class="py-2"
              >
                Path
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="conflict in importConflicts"
              :key="`${conflict.repository}-${conflict.path}`"
            >
              <td class="py-2 pr-4">
                {{ conflict.repository }}
              </td>
              <th
                scope="row"
                class="py-2 font-normal"
              >
                {{ conflict.path }}
              </th>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex flex-col gap-1">
        <label class="flex items-center gap-2 md-body-large">
          <input
            id="migration-cutover-switch"
            v-model="switchDone"
            type="checkbox"
          >
          Switch clients
        </label>
        <p class="md-body-medium text-on-surface-variant">
          Point each client at Ingot with the settings below, then tick this item.
        </p>
      </div>
    </fieldset>

    <section
      v-if="targets.length"
      aria-labelledby="migration-snippets-title"
      class="flex flex-col gap-4"
    >
      <h3
        id="migration-snippets-title"
        class="md-title-small text-on-surface"
      >
        Client settings
      </h3>
      <div
        v-for="name in targets"
        :key="name"
        class="flex flex-col gap-2"
      >
        <h4 class="md-label-large text-on-surface">
          {{ name }}
        </h4>
        <p class="md-body-medium text-on-surface-variant">
          Maven settings.xml
        </p>
        <pre class="overflow-x-auto rounded-corner-small bg-surface-container p-3 md-body-small"><code>{{ mavenSnippet(name) }}</code></pre>
        <div>
          <MdButton
            variant="text"
            type="button"
            :aria-label="`Copy the Maven settings for ${name}`"
            @click="copy(mavenSnippet(name))"
          >
            Copy Maven settings
          </MdButton>
        </div>
        <p class="md-body-medium text-on-surface-variant">
          Gradle
        </p>
        <pre class="overflow-x-auto rounded-corner-small bg-surface-container p-3 md-body-small"><code>{{ gradleSnippet(name) }}</code></pre>
        <div>
          <MdButton
            variant="text"
            type="button"
            :aria-label="`Copy the Gradle repository for ${name}`"
            @click="copy(gradleSnippet(name))"
          >
            Copy Gradle repository
          </MdButton>
        </div>
      </div>
    </section>

    <div v-if="verifyFinished">
      <MdButton
        id="migration-cutover-delete"
        type="button"
        variant="outlined"
        @click="deleteOpen = true"
      >
        Remove the verify job
      </MdButton>
    </div>

    <ProblemAlert
      id="migration-cutover-problem"
      :problem="problem"
    />

    <MigrationDialog
      v-model="deleteOpen"
      title-id="migration-delete-title"
      title="Remove the verify job?"
    >
      <p>
        The job and its results are removed from Ingot. The repositories stay.
      </p>
      <template #actions>
        <MdButton
          id="migration-cutover-delete-keep"
          variant="text"
          @click="deleteOpen = false"
        >
          Keep it
        </MdButton>
        <MdButton
          id="migration-cutover-delete-confirm"
          variant="filled"
          @click="removeVerifyJob"
        >
          Remove
        </MdButton>
      </template>
    </MigrationDialog>
  </section>
</template>
