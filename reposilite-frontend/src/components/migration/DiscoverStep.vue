<script setup lang="ts">
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

// Step 2: what Nexus holds. One call lists the repositories and the format histogram. Formats the plugin cannot
// import are listed as excluded, so the format decision has its numbers before the mapping.
import { computed, inject, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MdButton from '../md/MdButton.vue'
import MdDataTable from '../md/MdDataTable.vue'
import PasswordField from './PasswordField.vue'
import ProblemAlert from './ProblemAlert.vue'
import { formatBytes, countLabel } from '../maintenance/format'
import { migrationApi } from '../../store/migration/migrationApi'
import { connectionBodyOf } from '../../store/migration/requests'
import { MIGRATION_API, MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'
import type { DiscoveredNexus, DiscoveredRepository, Problem } from './types'

const api = inject(MIGRATION_API, migrationApi)
const wizard = inject(MIGRATION_WIZARD, useWizard())

const typedPassword = ref('')
const problem = ref<Problem | null>(null)
const passwordError = ref('')
const pending = ref(false)

const connection = computed(() => wizard.state.connection)
// A literal password is held only while the wizard is open. After a reload the operator types it again here.
const passwordNeeded = computed(() => connection.value !== null
  && connection.value.passwordMode === 'literal'
  && wizard.passwords.recall() === null)

const discovered = computed(() => wizard.state.discovery as DiscoveredNexus | null)
const repositories = computed<DiscoveredRepository[]>(() => discovered.value?.repositories ?? [])
const formats = computed(() => Object.entries(discovered.value?.formats ?? {})
  .map(([format, totals]) => ({ format, ...totals })))

const REPOSITORY_COLUMNS = [
  { key: 'name', label: 'Name' },
  { key: 'format', label: 'Format' },
  { key: 'type', label: 'Type' },
  { key: 'components', label: 'Components' },
  { key: 'size', label: 'Size' },
  { key: 'status', label: 'Status' },
]
const FORMAT_COLUMNS = [
  { key: 'format', label: 'Format' },
  { key: 'repositories', label: 'Repositories' },
  { key: 'components', label: 'Components' },
  { key: 'size', label: 'Size' },
]
const repositoryRows = computed(() => repositories.value.map(repository => ({
  name: repository.name,
  format: repository.format,
  type: repository.type,
  components: repository.components,
  size: formatBytes(repository.bytes),
  status: repository.supported ? 'Can be imported' : 'Excluded: the format is not supported',
})))
const formatRows = computed(() => formats.value.map(entry => ({
  format: entry.format,
  repositories: countLabel(entry.repositories, 'repository'),
  components: entry.components,
  size: formatBytes(entry.bytes),
})))

const submit = async () => {
  if (!connection.value) return
  passwordError.value = ''
  if (passwordNeeded.value) {
    if (!typedPassword.value) {
      passwordError.value = 'Enter the password again to discover the repositories.'
      return
    }
    wizard.passwords.remember(typedPassword.value)
  }
  const body = connectionBodyOf(connection.value, wizard.passwords)
  if (!body) return
  pending.value = true
  problem.value = null
  try {
    const report = await api.discover(body) as DiscoveredNexus
    wizard.setDiscovery(report)
    typedPassword.value = ''
  } catch (error) {
    problem.value = error as Problem
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section
    class="flex flex-col gap-6"
    aria-labelledby="migration-discover-title"
    data-ingot-component="discover-step"
  >
    <h2
      id="migration-discover-title"
      tabindex="-1"
      class="md-title-large text-on-surface"
    >
      Discover
    </h2>

    <p
      v-if="!connection"
      class="md-body-large text-on-surface-variant"
    >
      Test the connection to Nexus first.
      <RouterLink
        class="md-focus-ring text-primary"
        :to="{ name: 'settings-migration', params: { step: 'connect' } }"
      >
        Go to Connect
      </RouterLink>
    </p>

    <form
      v-else
      id="migration-discover-form"
      class="flex max-w-xl flex-col gap-4"
      novalidate
      @submit.prevent="submit"
    >
      <PasswordField
        v-if="passwordNeeded"
        id="migration-discover-password"
        label="Password"
        :model-value="typedPassword"
        :error="passwordError !== ''"
        :error-text="passwordError"
        supporting-text="The password was not kept when the page reloaded."
        @update:model-value="typedPassword = $event"
      />
      <div>
        <MdButton
          type="submit"
          variant="filled"
          :disabled="pending"
        >
          Discover repositories
        </MdButton>
      </div>
      <ProblemAlert
        id="migration-discover-problem"
        :problem="problem"
      />
    </form>

    <template v-if="wizard.state.discovery">
      <MdDataTable
        data-test="repositories"
        caption="Repositories in Nexus"
        :columns="REPOSITORY_COLUMNS"
        :rows="repositoryRows"
      />

      <MdDataTable
        data-test="formats"
        caption="Formats"
        :columns="FORMAT_COLUMNS"
        :rows="formatRows"
      />
    </template>
  </section>
</template>
