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
import { computed, inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { tokenStoreKey, useTokens } from '../../store/tokens'
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import { checklistItems, checklistProgress, dismissChecklist, isChecklistDismissed } from './gettingStarted'

/*
 * The setup checklist for administrators, at the top of the dashboard. It reads the saved settings
 * and the token list, so every step reflects the server. It is shown to managers until they hide it,
 * and the hiding is remembered in this browser.
 */

const { isManager } = useSession()
const configuration = inject(configurationStoreKey, null) ?? useConfiguration().store
const tokenStore = inject(tokenStoreKey, null) ?? useTokens()
const router = useRouter()

const dismissed = ref(isChecklistDismissed())

const dismiss = () => {
  dismissChecklist()
  dismissed.value = true
}

// Null until the data it stands for has arrived. The checklist waits for all three before it draws.
const repositories = computed(() => {
  const saved = configuration.savedConfiguration('maven')
  return saved ? (saved.repositories ?? []) : null
})
const frontend = computed(() => configuration.savedConfiguration('frontend') ?? null)
const tokens = computed(() => (tokenStore.loaded.value ? tokenStore.tokens.value : null))

const items = computed(() => checklistItems({
  repositories: repositories.value,
  tokens: tokens.value,
  frontend: frontend.value
}))
const loaded = computed(() => repositories.value !== null && frontend.value !== null && tokens.value !== null)
const progress = computed(() => checklistProgress(items.value))
const failure = computed(() => configuration.loadError.value || tokenStore.loadError.value || '')
const statusText = computed(() => (progress.value.done === progress.value.total
  ? 'All steps are done.'
  : `${progress.value.done} of ${progress.value.total} steps done.`))

const hrefOf = (item) => router.resolve(item.action.to).href
const go = (item) => router.push(item.action.to)

onMounted(() => {
  if (!isManager.value || dismissed.value) return
  // The shared settings are fetched only when nothing is loaded yet, so an edit in progress is never replaced.
  if (configuration.domains.value.length === 0) configuration.fetchConfiguration()
  tokenStore.fetchTokens()
})
</script>

<template>
  <MdCard
    v-if="isManager && !dismissed"
    variant="elevated"
    class="flex flex-col gap-4"
  >
    <section
      aria-labelledby="getting-started-title"
      class="flex flex-col gap-4"
    >
      <header class="flex flex-wrap items-start justify-between gap-3">
        <div class="flex min-w-0 flex-col gap-1">
          <h2
            id="getting-started-title"
            class="md-title-large text-on-surface"
          >
            Getting started
          </h2>
          <p
            v-if="loaded"
            class="md-body-medium text-on-surface-variant"
            role="status"
          >
            {{ statusText }}
          </p>
        </div>
        <MdButton
          variant="text"
          type="button"
          @click="dismiss"
        >
          Hide checklist
        </MdButton>
      </header>

      <p
        v-if="failure"
        role="alert"
        class="md-body-medium text-error"
      >
        Some steps could not be checked: {{ failure }}
      </p>

      <ol
        v-if="loaded"
        aria-label="Setup steps"
        class="flex flex-col gap-2"
      >
        <li
          v-for="item in items"
          :key="item.key"
          class="flex flex-wrap items-center gap-3 rounded-corner-medium bg-surface-container p-3"
          :data-state="item.done ? 'done' : 'open'"
        >
          <span
            class="flex size-8 shrink-0 items-center justify-center rounded-corner-full"
            :class="item.done ? 'bg-secondary-container text-on-secondary-container' : 'border border-outline text-on-surface-variant'"
            aria-hidden="true"
          >
            <svg
              v-if="item.done"
              viewBox="0 0 24 24"
              fill="currentColor"
              class="size-5"
            >
              <path d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
            </svg>
          </span>
          <div class="flex min-w-0 flex-1 basis-56 flex-col">
            <span class="md-title-small text-on-surface">{{ item.title }}</span>
            <span class="md-body-medium text-on-surface-variant">{{ item.description }}</span>
            <span class="md-label-medium text-on-surface-variant">{{ item.done ? 'Done' : 'To do' }}</span>
          </div>
          <MdButton
            :data-step-action="item.key"
            :variant="item.done ? 'text' : 'tonal'"
            :href="hrefOf(item)"
            @click.prevent="go(item)"
          >
            {{ item.action.label }}
          </MdButton>
        </li>
      </ol>
      <p
        v-else-if="!failure"
        class="md-body-medium text-on-surface-variant"
      >
        Checking the setup
      </p>
    </section>
  </MdCard>
</template>
