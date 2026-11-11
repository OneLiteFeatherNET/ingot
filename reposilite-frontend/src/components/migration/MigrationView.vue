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

// The page of the nexus import wizard: one heading, the step list and the step on screen. It reads the jobs of the
// server once when it opens, and a step address that is not open goes to the step the visitor has to finish next.
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ConnectStep from './ConnectStep.vue'
import DiscoverStep from './DiscoverStep.vue'
import MappingStep from './MappingStep.vue'
import DryRunStep from './DryRunStep.vue'
import SyncStep from './SyncStep.vue'
import VerifyStep from './VerifyStep.vue'
import CutOverStep from './CutOverStep.vue'
import ProblemAlert from './ProblemAlert.vue'
import { migrationApi, onMigrationUnauthorized } from '../../store/migration/migrationApi'
import { STEPS, STEP_LABELS, stepStates, stepTarget, nextClosedReason } from '../../store/migration/gating'
import { MIGRATION_API, MIGRATION_WIZARD } from '../../store/migration/runtime'
import { useWizard } from '../../store/migration/wizardStore'
import { openLogin } from '../../store/login'
import { useSession } from '../../store/session'

const props = defineProps({
  step: {
    type: String,
    default: undefined
  }
})

const api = inject(MIGRATION_API, migrationApi)
const wizard = inject(MIGRATION_WIZARD, useWizard())
const router = useRouter()

const { isLogged } = useSession()
const loaded = ref(false)
const loadProblem = ref(null)
const notice = ref('')
// A visitor without the permission sees this state and no step, so no data is shown to them.
const denied = computed(() => loadProblem.value?.status === 403)

const STEP_VIEWS = {
  connect: ConnectStep,
  discover: DiscoverStep,
  mapping: MappingStep,
  'dry-run': DryRunStep,
  sync: SyncStep,
  verify: VerifyStep,
  'cut-over': CutOverStep,
}

const root = ref(null)
const current = computed(() => (STEPS.includes(props.step) ? props.step : STEPS[0]))
const view = computed(() => STEP_VIEWS[current.value] ?? null)
const states = computed(() => stepStates(wizard.gate(), wizard.state.jobs))

// A step that is not open yet is still listed, with the reason in its text, so the list says what is missing.
const reasonOf = (id) => (states.value[id].open ? '' : states.value[id].reason ?? '')

const goTo = (step) => router.replace({ name: 'settings-migration', params: { step } })

// The address decides once the jobs are known. An open step stays. A closed or unknown one goes to the step the
// jobs open, or to the last open step before the first closed one, and says what that step still needs.
const decide = () => {
  if (!loaded.value) return
  const requested = STEPS.includes(props.step) ? props.step : undefined
  const target = stepTarget(requested, wizard.gate(), wizard.state.jobs)
  if (!target.redirected) {
    notice.value = ''
    return
  }
  notice.value = target.fromJobs
    ? ''
    : `${requested ? `${STEP_LABELS[requested]} is not open yet.` : 'That step is not part of the migration.'} ${nextClosedReason(wizard.gate(), wizard.state.jobs) ?? ''}`.trim()
  if (target.step !== requested) goTo(target.step)
}

onMounted(async () => {
  // A 401 drops the literal password and opens the sign in dialog; the address stays on this step.
  onMigrationUnauthorized(() => {
    wizard.passwords.forget()
    openLogin()
  })
  try {
    wizard.setJobs(await api.listJobs())
  } catch (error) {
    loadProblem.value = error
    wizard.setJobs([])
  }
  loaded.value = true
  decide()
})

// A session that ends takes the literal password with it.
watch(isLogged, (logged) => {
  if (!logged) wizard.passwords.forget()
})

// A new step takes the focus on its heading, so a screen reader announces the step. The router leaves the focus
// of a move between migration steps alone (see router/create.js).
watch(() => props.step, async () => {
  decide()
  await nextTick()
  root.value?.querySelector('h2')?.focus()
})

// Leaving the wizard drops the literal password (design D4). The rest of the page state stays in memory.
onBeforeUnmount(() => {
  onMigrationUnauthorized(() => {})
  wizard.passwords.forget()
})
</script>

<template>
  <div
    ref="root"
    class="flex flex-col gap-6"
  >
    <nav aria-label="Migration steps">
      <ol class="flex flex-wrap gap-2 list-none">
        <li
          v-for="(id, index) in STEPS"
          :key="id"
        >
          <RouterLink
            :to="{ name: 'settings-migration', params: { step: id } }"
            :aria-current="id === current ? 'step' : undefined"
            :aria-disabled="states[id].open ? undefined : 'true'"
            class="md-focus-ring md-label-large inline-flex min-h-6 items-center rounded-corner-small px-3 py-2 no-underline"
            :class="[
              id === current ? 'bg-secondary-container text-on-secondary-container' : 'text-primary',
              states[id].open ? '' : 'opacity-60'
            ]"
          >
            {{ index + 1 }}. {{ STEP_LABELS[id] }}
            <span
              v-if="reasonOf(id)"
              class="sr-only"
            >(not open yet: {{ reasonOf(id) }})</span>
          </RouterLink>
        </li>
      </ol>
    </nav>
    <p
      v-if="notice"
      role="status"
      class="md-body-large text-on-surface-variant"
    >
      {{ notice }}
    </p>
    <ProblemAlert
      v-if="loadProblem"
      :problem="loadProblem"
    />
    <section
      v-if="denied"
      role="alert"
      class="flex flex-col gap-1"
    >
      <p class="md-title-small text-on-surface">
        You do not have access to the import.
      </p>
      <p class="md-body-medium text-on-surface-variant">
        Only instance administrators can run a Nexus import. Sign in with an account that can, to see this page.
      </p>
    </section>
    <p
      v-else-if="!loaded"
      class="md-body-large text-on-surface-variant"
    >
      Reading the import jobs of this instance.
    </p>
    <component
      :is="view"
      v-else-if="view"
    />
  </div>
</template>
