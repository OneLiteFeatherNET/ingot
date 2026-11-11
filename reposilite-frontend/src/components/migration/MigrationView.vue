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

// The page of the nexus import wizard: one heading, the step list and the step on screen.
import { computed, onBeforeUnmount } from 'vue'
import { STEPS, STEP_LABELS } from '../../store/migration/gating'
import { useWizard } from '../../store/migration/wizardStore'

const props = defineProps({
  step: {
    type: String,
    default: undefined
  }
})

const wizard = useWizard()
const current = computed(() => (STEPS.includes(props.step) ? props.step : STEPS[0]))

// Leaving the wizard drops the literal password (design D4). The rest of the page state stays in memory.
onBeforeUnmount(() => wizard.passwords.forget())
</script>

<template>
  <div class="container mx-auto flex flex-col gap-6 py-8">
    <h1 class="md-headline-medium text-on-surface">
      Migration
    </h1>
    <nav aria-label="Migration steps">
      <ol class="flex flex-wrap gap-2 list-none">
        <li
          v-for="(id, index) in STEPS"
          :key="id"
        >
          <RouterLink
            :to="{ name: 'settings-migration', params: { step: id } }"
            :aria-current="id === current ? 'step' : undefined"
            class="md-focus-ring md-label-large inline-flex min-h-6 items-center rounded-corner-small px-3 py-2 no-underline"
            :class="id === current ? 'bg-secondary-container text-on-secondary-container' : 'text-primary'"
          >
            {{ index + 1 }}. {{ STEP_LABELS[id] }}
          </RouterLink>
        </li>
      </ol>
    </nav>
    <section aria-labelledby="migration-step-title">
      <h2
        id="migration-step-title"
        tabindex="-1"
        class="md-title-large text-on-surface"
      >
        {{ STEP_LABELS[current] }}
      </h2>
    </section>
  </div>
</template>
