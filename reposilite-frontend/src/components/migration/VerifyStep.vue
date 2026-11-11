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

// Step 6: the verify job reads the repositories of the import back from Nexus and Ingot and compares them. Each
// repository ends VERIFIED or MISMATCH; the paths that differ are listed.
import { computed, inject, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import PasswordField from './PasswordField.vue'
import ProblemAlert from './ProblemAlert.vue'
import JobProgress from './JobProgress.vue'
import { repositoryResultText, statusText } from './jobText'
import { useJobWatch } from './useJobWatch'
import { migrationApi } from '../../store/migration/migrationApi'
import { connectionBodyOf } from '../../store/migration/requests'
import { isFinished } from '../../store/migration/gating'
import { browserRuntime, MIGRATION_API, MIGRATION_RUNTIME, MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'

const api = inject(MIGRATION_API, migrationApi)
const wizard = inject(MIGRATION_WIZARD, useWizard())
const runtime = inject(MIGRATION_RUNTIME, browserRuntime())

const typedPassword = ref('')
const passwordError = ref('')
const problem = ref(null)
const starting = ref(false)
const reloaded = ref(null)
const mismatches = ref([])

const newest = (kind) => wizard.state.jobs.filter(job => job.kind === kind).sort((a, b) => b.id - a.id)[0] ?? null

const watched = useJobWatch({
  api,
  runtime,
  onUpdate: (value) => wizard.upsertJob(value),
  onFinished: (finished) => {
    if (finished.kind === 'VERIFY') loadMismatches(finished.id)
  },
})

const importJob = computed(() => newest('IMPORT'))
const shown = computed(() => watched.job.value ?? reloaded.value)
const running = computed(() => shown.value !== null && !isFinished(shown.value))
const passwordNeeded = computed(() => wizard.state.connection?.passwordMode === 'literal' && wizard.passwords.recall() === null)

const loadMismatches = async (id) => {
  try {
    mismatches.value = await api.conflicts(id)
  } catch (failure) {
    problem.value = failure
  }
}

const start = async () => {
  passwordError.value = ''
  if (!importJob.value) return
  if (passwordNeeded.value) {
    if (!typedPassword.value) {
      passwordError.value = 'Enter the password again to start the verification.'
      return
    }
    wizard.passwords.remember(typedPassword.value)
  }
  const base = connectionBodyOf(wizard.state.connection, wizard.passwords)
  if (!base) return
  starting.value = true
  problem.value = null
  reloaded.value = null
  mismatches.value = []
  try {
    const created = await api.createJob({ ...base, kind: 'VERIFY', verifyImportJob: importJob.value.id })
    typedPassword.value = ''
    wizard.upsertJob({ id: created.id, kind: created.kind, status: created.status, pauseReason: null, repositories: [], results: {}, summary: null })
    watched.follow(created.id)
  } catch (failure) {
    problem.value = failure
  } finally {
    starting.value = false
  }
}

onMounted(() => {
  const latest = newest('VERIFY')
  if (!latest) return
  if (isFinished(latest)) {
    reloaded.value = latest
    loadMismatches(latest.id)
  } else {
    watched.follow(latest.id)
  }
})
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-verify-title"
  >
    <h2
      id="migration-verify-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Verify
    </h2>

    <p
      v-if="!importJob"
      class="md-body-large text-on-surface-variant"
    >
      Start the import first.
      <RouterLink
        class="md-focus-ring text-primary"
        :to="{ name: 'settings-migration', params: { step: 'sync' } }"
      >
        Go to Sync
      </RouterLink>
    </p>

    <template v-else>
      <form
        v-if="!running"
        class="flex max-w-xl flex-col gap-4"
        novalidate
        @submit.prevent="start"
      >
        <PasswordField
          v-if="passwordNeeded"
          id="migration-verify-password"
          label="Password"
          :model-value="typedPassword"
          :error="passwordError !== ''"
          :error-text="passwordError"
          supporting-text="The password was not kept when the page reloaded."
          @update:model-value="typedPassword = $event"
        />
        <div>
          <MdButton
            id="migration-verify-start"
            type="button"
            variant="filled"
            :disabled="starting"
            @click="start"
          >
            Start verification
          </MdButton>
        </div>
      </form>

      <JobProgress
        v-if="running"
        :job="shown"
        label="Verify progress"
        :unreadable="watched.watching.value && watched.error.value !== null"
      />

      <p
        v-if="shown && !running"
        class="md-body-large text-on-surface"
      >
        Verify job {{ shown.id }}: {{ statusText(shown.status) }}
      </p>

      <table
        v-if="shown && shown.repositories?.length"
        data-test="verify"
        class="w-full text-left md-body-medium"
      >
        <caption class="md-title-small pb-2 text-left text-on-surface">
          Result per repository
        </caption>
        <thead>
          <tr>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Nexus name
            </th>
            <th
              scope="col"
              class="py-2 pr-4"
            >
              Ingot name
            </th>
            <th
              scope="col"
              class="py-2"
            >
              Result
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="repository in shown.repositories"
            :key="repository.nexusName"
          >
            <th
              scope="row"
              class="py-2 pr-4 font-normal"
            >
              {{ repository.nexusName }}
            </th>
            <td class="py-2 pr-4">
              {{ repository.targetName }}
            </td>
            <td class="py-2">
              {{ repositoryResultText(repository.status) }}
            </td>
          </tr>
        </tbody>
      </table>

      <table
        v-if="mismatches.length"
        data-test="mismatches"
        class="w-full text-left md-body-medium"
      >
        <caption class="md-title-small pb-2 text-left text-on-surface">
          Paths that do not match
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
              class="py-2 pr-4"
            >
              Path
            </th>
            <th
              scope="col"
              class="py-2"
            >
              Detail
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in mismatches"
            :key="`${entry.repository}-${entry.path}`"
          >
            <td class="py-2 pr-4">
              {{ entry.repository }}
            </td>
            <th
              scope="row"
              class="py-2 pr-4 font-normal"
            >
              {{ entry.path }}
            </th>
            <td class="py-2">
              {{ entry.detail }}
            </td>
          </tr>
        </tbody>
      </table>

      <p v-if="shown && !running">
        <RouterLink
          class="md-focus-ring md-label-large text-primary"
          :to="{ name: 'settings-migration', params: { step: 'cut-over' } }"
        >
          Continue to Cut-over
        </RouterLink>
      </p>
    </template>

    <ProblemAlert
      id="migration-verify-problem"
      :problem="problem"
    />
  </section>
</template>
