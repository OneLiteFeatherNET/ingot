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

// Step 5: the import. The job runs on the server, so a reload attaches to it again. Pause, resume and cancel are the
// operator's controls; a credentials pause asks for the password and sends it only to its own route.
import { computed, inject, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import MigrationDialog from './MigrationDialog.vue'
import PasswordField from './PasswordField.vue'
import ProblemAlert from './ProblemAlert.vue'
import JobProgress from './JobProgress.vue'
import { pauseText, repositoryResultText, statusText } from './jobText'
import { useJobWatch } from './useJobWatch'
import { migrationApi } from '../../store/migration/migrationApi'
import { jobBodyOf } from '../../store/migration/requests'
import { isFinished, syncBlockers } from '../../store/migration/gating'
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
const conflicts = ref([])
const cancelOpen = ref(false)

const newest = (kind) => wizard.state.jobs.filter(job => job.kind === kind).sort((a, b) => b.id - a.id)[0] ?? null

const watched = useJobWatch({
  api,
  runtime,
  onUpdate: (value) => wizard.upsertJob(value),
  onFinished: (finished) => {
    if (finished.kind === 'IMPORT') loadConflicts(finished.id)
  },
  onUnauthorized: () => wizard.passwords.forget(),
})

const shown = computed(() => watched.job.value ?? reloaded.value)
const mapping = computed(() => wizard.state.mapping)
const passwordNeeded = computed(() => wizard.state.connection?.passwordMode === 'literal' && wizard.passwords.recall() === null)

const gate = computed(() => syncBlockers({
  dryRun: newest('DRY_RUN'),
  acceptance: wizard.state.acceptance,
  rows: mapping.value?.repositories ?? [],
  conflictPolicy: mapping.value?.conflictPolicy ?? 'refuse',
}))

const credentialsPause = computed(() => shown.value?.status === 'PAUSED' && shown.value?.pauseReason === 'CREDENTIALS_NEEDED')
const canPause = computed(() => shown.value !== null && ['QUEUED', 'RUNNING'].includes(shown.value.status))
const canResume = computed(() => shown.value?.status === 'PAUSED' && shown.value?.pauseReason === 'OPERATOR')
const canCancel = computed(() => shown.value !== null && !isFinished(shown.value))
const running = computed(() => shown.value !== null && !isFinished(shown.value))

const loadConflicts = async (id) => {
  try {
    conflicts.value = await api.conflicts(id)
  } catch (failure) {
    problem.value = failure
  }
}

const act = async (call) => {
  problem.value = null
  try {
    await call()
  } catch (failure) {
    problem.value = failure
  }
}

const start = async () => {
  passwordError.value = ''
  if (passwordNeeded.value) {
    if (!typedPassword.value) {
      passwordError.value = 'Enter the password again to start the import.'
      return
    }
    wizard.passwords.remember(typedPassword.value)
  }
  const body = jobBodyOf({ kind: 'IMPORT', connection: wizard.state.connection, passwords: wizard.passwords, mapping: mapping.value })
  if (!body) return
  starting.value = true
  problem.value = null
  reloaded.value = null
  conflicts.value = []
  try {
    const created = await api.createJob(body)
    typedPassword.value = ''
    wizard.upsertJob({ id: created.id, kind: created.kind, status: created.status, pauseReason: null, repositories: [], results: {}, summary: null })
    watched.follow(created.id)
  } catch (failure) {
    problem.value = failure
  } finally {
    starting.value = false
  }
}

const pause = () => act(async () => {
  await api.pauseJob(shown.value.id)
  watched.follow(shown.value.id)
})

const resume = () => act(async () => {
  await api.resumeJob(shown.value.id)
  watched.follow(shown.value.id)
})

const confirmCancel = () => {
  cancelOpen.value = false
  return act(async () => {
    await api.cancelJob(shown.value.id)
    watched.follow(shown.value.id)
  })
}

// The password of a credentials pause goes to its own route and nowhere else. The page keeps it for later calls.
const sendPassword = () => act(async () => {
  if (!typedPassword.value) {
    passwordError.value = 'Enter the password.'
    return
  }
  const password = typedPassword.value
  await api.supplyCredentials(shown.value.id, password)
  wizard.passwords.remember(password)
  typedPassword.value = ''
  passwordError.value = ''
  watched.follow(shown.value.id)
})

// After a reload the newest import is attached to when it still runs, and shown as it ended otherwise.
onMounted(() => {
  const latest = newest('IMPORT')
  if (!latest) return
  if (isFinished(latest)) {
    reloaded.value = latest
    loadConflicts(latest.id)
  } else {
    watched.follow(latest.id)
  }
})
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-sync-title"
  >
    <h2
      id="migration-sync-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Sync
    </h2>
    <p class="md-body-large text-on-surface-variant">
      The import creates the repositories in Ingot and copies their content. Nexus is only read.
    </p>

    <template v-if="!shown">
      <form
        class="flex max-w-xl flex-col gap-4"
        novalidate
        @submit.prevent="start"
      >
        <PasswordField
          v-if="passwordNeeded"
          id="migration-sync-password"
          label="Password"
          :model-value="typedPassword"
          :error="passwordError !== ''"
          :error-text="passwordError"
          supporting-text="The password was not kept when the page reloaded."
          @update:model-value="typedPassword = $event"
        />
        <div>
          <MdButton
            id="migration-sync-start"
            type="button"
            variant="filled"
            :disabled="!gate.ok || starting"
            @click="start"
          >
            Start import
          </MdButton>
        </div>
      </form>
      <div
        v-if="!gate.ok"
        role="status"
        class="flex flex-col gap-1 md-body-medium text-on-surface-variant"
      >
        <p>Sync cannot start yet:</p>
        <ul class="list-disc pl-6">
          <li
            v-for="reason in gate.reasons"
            :key="reason"
          >
            {{ reason }}
          </li>
        </ul>
      </div>
    </template>

    <template v-else>
      <p
        v-if="shown"
        class="md-body-large text-on-surface"
      >
        Import {{ shown.id }}: {{ statusText(shown.status) }}
      </p>

      <JobProgress
        v-if="running"
        :job="shown"
        label="Import progress"
      />

      <p
        v-if="shown.status === 'PAUSED' && shown.pauseReason === 'READ_ONLY'"
        role="status"
        class="md-body-large text-on-surface"
      >
        {{ pauseText('READ_ONLY') }}
      </p>

      <form
        v-if="credentialsPause"
        class="flex max-w-xl flex-col gap-4"
        novalidate
        @submit.prevent="sendPassword"
      >
        <p class="md-body-large text-on-surface">
          {{ pauseText('CREDENTIALS_NEEDED') }}
        </p>
        <PasswordField
          id="migration-sync-password"
          label="Password"
          :model-value="typedPassword"
          :error="passwordError !== ''"
          :error-text="passwordError"
          @update:model-value="typedPassword = $event"
        />
        <div>
          <MdButton
            id="migration-sync-send-password"
            type="button"
            variant="filled"
            @click="sendPassword"
          >
            Send password
          </MdButton>
        </div>
      </form>

      <div class="flex flex-wrap gap-2">
        <MdButton
          v-if="canPause"
          id="migration-sync-pause"
          type="button"
          variant="outlined"
          @click="pause"
        >
          Pause
        </MdButton>
        <MdButton
          v-if="canResume"
          id="migration-sync-resume"
          type="button"
          variant="filled"
          @click="resume"
        >
          Resume
        </MdButton>
        <MdButton
          v-if="canCancel"
          id="migration-sync-cancel"
          type="button"
          variant="outlined"
          @click="cancelOpen = true"
        >
          Cancel
        </MdButton>
      </div>

      <table
        data-test="repositories"
        class="w-full text-left md-body-medium"
      >
        <caption class="md-title-small pb-2 text-left text-on-surface">
          Repositories
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
        v-if="conflicts.length"
        data-test="conflicts"
        class="w-full text-left md-body-medium"
      >
        <caption class="md-title-small pb-2 text-left text-on-surface">
          Conflicts: the same path holds different files
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
              Checksums
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="conflict in conflicts"
            :key="`${conflict.repository}-${conflict.path}`"
          >
            <td class="py-2 pr-4">
              {{ conflict.repository }}
            </td>
            <th
              scope="row"
              class="py-2 pr-4 font-normal"
            >
              {{ conflict.path }}
            </th>
            <td class="py-2">
              {{ conflict.detail }}
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <ProblemAlert
      id="migration-sync-problem"
      :problem="problem"
    />

    <p v-if="shown && !running">
      <RouterLink
        class="md-focus-ring md-label-large text-primary"
        :to="{ name: 'settings-migration', params: { step: 'verify' } }"
      >
        Continue to Verify
      </RouterLink>
    </p>

    <MigrationDialog
      v-model="cancelOpen"
      title-id="migration-cancel-title"
      title="Cancel the import?"
    >
      <p>
        Repositories already copied stay in Ingot. The rest is not copied.
      </p>
      <template #actions>
        <MdButton
          id="migration-sync-cancel-keep"
          variant="text"
          @click="cancelOpen = false"
        >
          Keep running
        </MdButton>
        <MdButton
          id="migration-sync-cancel-confirm"
          variant="filled"
          @click="confirmCancel"
        >
          Cancel import
        </MdButton>
      </template>
    </MigrationDialog>
  </section>
</template>
