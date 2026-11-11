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

// Step 4: a dry run reads Nexus and writes nothing. Its loss report is shown before any write, and the operator
// accepts it for this mapping. The acceptance is kept in the page; Sync stays closed until it is current.
import { computed, inject, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import PasswordField from './PasswordField.vue'
import ProblemAlert from './ProblemAlert.vue'
import JobProgress from './JobProgress.vue'
import { formatBytes } from '../maintenance/format'
import { LOSS_STATUSES, lossStatusText, statusText } from './jobText'
import { useJobWatch } from './useJobWatch'
import { migrationApi, reportHref } from '../../store/migration/migrationApi'
import { jobBodyOf } from '../../store/migration/requests'
import { acceptanceIsCurrent, mappingWarnings, planFingerprint, syncBlockers, isFinished } from '../../store/migration/gating'
import { browserRuntime, MIGRATION_API, MIGRATION_RUNTIME, MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'

const api = inject(MIGRATION_API, migrationApi)
const wizard = inject(MIGRATION_WIZARD, useWizard())
const runtime = inject(MIGRATION_RUNTIME, browserRuntime())

const mapping = computed(() => wizard.state.mapping)
const included = computed(() => (mapping.value?.repositories ?? []).filter(repository => repository.included))
const duplicateWarnings = computed(() => mappingWarnings(mapping.value?.repositories ?? []).filter(warning => warning.kind === 'duplicate' || warning.kind === 'empty'))
const passwordNeeded = computed(() => wizard.state.connection?.passwordMode === 'literal' && wizard.passwords.recall() === null)

const typedPassword = ref('')
const passwordError = ref('')
const problem = ref(null)
const starting = ref(false)
const reloaded = ref(null)
const report = ref({ rows: [], summary: null })
const reportLoaded = ref(false)
const readReport = ref(false)
const statusFilter = ref('')
const textFilter = ref('')

const watched = useJobWatch({
  api,
  runtime,
  onFinished: (finished) => {
    if (finished.kind === 'DRY_RUN') loadReport(finished.id)
  },
})

// The dry run on screen: the one this visit started, or the newest of the jobs after a reload.
const dryRun = computed(() => watched.job.value ?? reloaded.value)
const done = computed(() => dryRun.value !== null && isFinished(dryRun.value))
// The acceptance counts only while it names this dry run and the mapping as it is now.
const acceptanceCurrent = computed(() => dryRun.value !== null
  && mapping.value !== null
  && acceptanceIsCurrent(wizard.state.acceptance, mapping.value, dryRun.value.id))
const summary = computed(() => dryRun.value?.summary ?? report.value.summary ?? null)
const rows = computed(() => report.value.rows ?? [])

const statusCounts = computed(() => LOSS_STATUSES.map(status => ({
  status,
  count: rows.value.filter(row => row.status === status).length,
})))

const visibleRows = computed(() => rows.value.filter(row => {
  if (statusFilter.value && row.status !== statusFilter.value) return false
  const text = textFilter.value.trim().toLowerCase()
  if (!text) return true
  return [row.objectType, row.nexusId, row.target, row.reason].some(value => String(value ?? '').toLowerCase().includes(text))
}))

// A name that is taken comes with the name Ingot proposes instead. Taking the proposal changes the mapping.
const proposals = computed(() => rows.value.filter(row => /is taken/i.test(row.reason ?? '') && row.target && row.target !== row.nexusId))

const blockers = computed(() => summary.value?.blockers ?? [])
const rowFor = (blocker) => rows.value.find(row => blocker.includes(row.nexusId))

const gate = computed(() => syncBlockers({
  dryRun: dryRun.value,
  acceptance: wizard.state.acceptance,
  rows: mapping.value?.repositories ?? [],
  conflictPolicy: mapping.value?.conflictPolicy ?? 'refuse',
}))

const loadReport = async (id) => {
  try {
    report.value = await api.report(id, 'json')
    reportLoaded.value = true
  } catch (failure) {
    problem.value = failure
  }
}

const start = async () => {
  passwordError.value = ''
  if (passwordNeeded.value) {
    if (!typedPassword.value) {
      passwordError.value = 'Enter the password again to start the dry run.'
      return
    }
    wizard.passwords.remember(typedPassword.value)
  }
  const body = jobBodyOf({ kind: 'DRY_RUN', connection: wizard.state.connection, passwords: wizard.passwords, mapping: mapping.value })
  if (!body) return
  starting.value = true
  problem.value = null
  readReport.value = false
  reloaded.value = null
  report.value = { rows: [], summary: null }
  try {
    const created = await api.createJob(body)
    typedPassword.value = ''
    watched.follow(created.id)
  } catch (failure) {
    problem.value = failure
  } finally {
    starting.value = false
  }
}

const acceptReport = () => {
  wizard.accept({ dryRunJobId: dryRun.value.id, fingerprint: planFingerprint(mapping.value) })
}

const takeProposal = (row) => {
  const next = (mapping.value?.repositories ?? []).map(repository => (repository.nexusName === row.nexusId
    ? { ...repository, targetName: row.target }
    : repository))
  wizard.setMapping(next, mapping.value?.conflictPolicy ?? 'refuse')
}

const focusRow = (nexusId) => {
  document.getElementById(`migration-report-row-${nexusId}`)?.focus()
}

// After a reload the newest dry run of the jobs is shown with its report, and an unfinished one is followed.
onMounted(() => {
  const newest = wizard.state.jobs
    .filter(job => job.kind === 'DRY_RUN')
    .sort((a, b) => b.id - a.id)[0]
  if (!newest) return
  if (isFinished(newest)) {
    reloaded.value = newest
    loadReport(newest.id)
  } else {
    watched.follow(newest.id)
  }
})
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-dry-run-title"
  >
    <h2
      id="migration-dry-run-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Dry run
    </h2>
    <p class="md-body-large text-on-surface-variant">
      A dry run reads Nexus and writes nothing. It shows what each object becomes in Ingot and what is lost.
    </p>

    <p
      v-if="included.length === 0"
      class="md-body-large text-on-surface-variant"
    >
      Map at least one repository before the dry run.
      <RouterLink
        class="md-focus-ring text-primary"
        :to="{ name: 'settings-migration', params: { step: 'mapping' } }"
      >
        Go to Mapping
      </RouterLink>
    </p>

    <template v-else>
      <form
        class="flex max-w-xl flex-col gap-4"
        novalidate
        @submit.prevent="start"
      >
        <PasswordField
          v-if="passwordNeeded"
          id="migration-dry-run-password"
          label="Password"
          :model-value="typedPassword"
          :error="passwordError !== ''"
          :error-text="passwordError"
          supporting-text="The password was not kept when the page reloaded."
          @update:model-value="typedPassword = $event"
        />
        <div>
          <MdButton
            id="migration-dry-run-start"
            type="button"
            variant="filled"
            :disabled="starting || watched.watching.value || duplicateWarnings.length > 0"
            @click="start"
          >
            Start dry run
          </MdButton>
        </div>
        <ProblemAlert
          id="migration-dry-run-problem"
          :problem="problem"
        />
      </form>

      <JobProgress
        v-if="watched.watching.value && watched.job.value"
        :job="watched.job.value"
        label="Dry run progress"
      />

      <p
        v-if="done"
        class="md-body-large text-on-surface"
      >
        Status: {{ statusText(dryRun.status) }}
      </p>

      <template v-if="done && summary">
        <p class="md-body-large text-on-surface">
          Estimated transfer size: {{ formatBytes(summary.transferBytes) }}.
        </p>
        <ul
          class="flex flex-wrap gap-4 md-body-medium"
          aria-label="Loss report counts"
        >
          <li
            v-for="entry in statusCounts"
            :key="entry.status"
          >
            {{ entry.count }} {{ lossStatusText(entry.status).toLowerCase() }}
          </li>
        </ul>

        <ul
          v-if="blockers.length"
          class="flex flex-col gap-2 md-body-medium"
          aria-label="Blockers"
        >
          <li
            v-for="(blocker, index) in blockers"
            :key="blocker"
            class="flex flex-wrap items-center gap-2"
          >
            <span>Blocker: {{ blocker }}</span>
            <MdButton
              v-if="rowFor(blocker)"
              :id="`migration-blocker-show-${index}`"
              variant="text"
              @click="focusRow(rowFor(blocker).nexusId)"
            >
              Show in report
            </MdButton>
          </li>
        </ul>

        <div
          v-if="proposals.length"
          class="flex flex-col gap-2"
        >
          <p
            v-for="row in proposals"
            :key="row.nexusId"
            class="md-body-medium text-on-surface"
          >
            {{ row.reason }}
            <MdButton
              :id="`migration-take-proposal-${row.nexusId}`"
              variant="tonal"
              @click="takeProposal(row)"
            >
              Use {{ row.target }}
            </MdButton>
          </p>
        </div>

        <div class="flex flex-wrap items-end gap-4">
          <div class="flex flex-col gap-1">
            <label
              for="migration-report-status"
              class="md-label-large"
            >Status</label>
            <select
              id="migration-report-status"
              v-model="statusFilter"
              class="rounded-corner-extra-small border border-outline bg-surface px-2 py-2"
            >
              <option value="">
                All statuses
              </option>
              <option
                v-for="status in LOSS_STATUSES"
                :key="status"
                :value="status"
              >
                {{ lossStatusText(status) }}
              </option>
            </select>
          </div>
          <div class="flex flex-col gap-1">
            <label
              for="migration-report-search"
              class="md-label-large"
            >Search the report</label>
            <input
              id="migration-report-search"
              v-model="textFilter"
              type="search"
              class="rounded-corner-extra-small border border-outline bg-surface px-2 py-2"
            >
          </div>
          <div class="flex gap-4">
            <a
              class="md-focus-ring text-primary"
              :href="reportHref(dryRun.id, 'json')"
              download
            >Download JSON</a>
            <a
              class="md-focus-ring text-primary"
              :href="reportHref(dryRun.id, 'csv')"
              download
            >Download CSV</a>
          </div>
        </div>

        <table
          v-if="reportLoaded"
          data-test="report"
          class="w-full text-left md-body-medium"
        >
          <caption class="md-title-small pb-2 text-left text-on-surface">
            Loss report: what each object becomes in Ingot
          </caption>
          <thead>
            <tr>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Object
              </th>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Nexus id
              </th>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Ingot target
              </th>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Status
              </th>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Reason
              </th>
              <th
                scope="col"
                class="py-2 pr-4"
              >
                Consequence
              </th>
              <th
                scope="col"
                class="py-2"
              >
                Action
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in visibleRows"
              :id="`migration-report-row-${row.nexusId}`"
              :key="`${row.objectType}-${row.nexusId}`"
              tabindex="-1"
            >
              <td class="py-2 pr-4">
                {{ row.objectType }}
              </td>
              <th
                scope="row"
                class="py-2 pr-4 font-normal"
              >
                {{ row.nexusId }}
              </th>
              <td class="py-2 pr-4">
                {{ row.target ?? 'None' }}
              </td>
              <td class="py-2 pr-4">
                {{ lossStatusText(row.status) }}
              </td>
              <td class="py-2 pr-4">
                {{ row.reason }}
              </td>
              <td class="py-2 pr-4">
                {{ row.consequence }}
              </td>
              <td class="py-2">
                {{ row.action }}
              </td>
            </tr>
          </tbody>
        </table>

        <div class="flex flex-col gap-3">
          <label class="flex items-center gap-2 md-body-large">
            <input
              id="migration-accept-check"
              v-model="readReport"
              type="checkbox"
            >
            I have read the loss report for this mapping
          </label>
          <div>
            <button
              id="migration-accept-report"
              type="button"
              class="md-focus-ring rounded-corner-full bg-primary px-6 py-2.5 md-label-large text-on-primary disabled:bg-on-surface/12 disabled:text-on-surface/38"
              :disabled="!readReport || acceptanceCurrent"
              @click="acceptReport"
            >
              Accept the loss report
            </button>
          </div>
          <p
            v-if="acceptanceCurrent"
            class="md-body-medium text-on-surface"
          >
            The report is accepted for this mapping.
          </p>
        </div>

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
        <p v-else>
          <RouterLink
            class="md-focus-ring md-label-large text-primary"
            :to="{ name: 'settings-migration', params: { step: 'sync' } }"
          >
            Continue to Sync
          </RouterLink>
        </p>
      </template>
    </template>
  </section>
</template>
