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
import { RouterLink } from 'vue-router'

/*
 * The page of /settings when no domain is open: the domains in category groups, each a card that links to
 * the domain. A card shows the saved value, and "Unsaved changes" when the draft differs. Opening a card
 * only changes the address. The last line sends sign in and security to Access.
 *
 * Props:
 *   groups  [{ heading, cards: [{ key, title, summary, unsaved, to }] }], as hubGroups builds them
 */
defineProps({
  groups: { type: Array, required: true }
})
</script>

<template>
  <div
    class="flex flex-col gap-8"
    data-test="settings-hub"
    data-ingot-component="settings-hub"
  >
    <section
      v-for="group in groups"
      :key="group.heading"
      role="group"
      :aria-label="group.heading"
      class="flex flex-col gap-3"
    >
      <h2 class="md-title-medium text-on-surface">
        {{ group.heading }}
      </h2>
      <ul class="grid list-none grid-cols-1 gap-3 p-0 md:grid-cols-2 xl:grid-cols-3">
        <li
          v-for="card in group.cards"
          :key="card.key"
        >
          <RouterLink
            :to="card.to"
            class="md-state-layer md-focus-ring relative flex h-full flex-col gap-1 rounded-corner-medium border border-outline-variant bg-surface-container-low p-4 no-underline"
            data-test="settings-card"
          >
            <h3 class="md-title-small text-on-surface">
              {{ card.title }}
            </h3>
            <p class="md-body-medium text-on-surface-variant">
              {{ card.summary }}
            </p>
            <p
              v-if="card.unsaved"
              class="md-label-medium text-primary"
            >
              Unsaved changes
            </p>
          </RouterLink>
        </li>
      </ul>
    </section>

    <p
      class="md-body-medium text-on-surface-variant"
      data-test="settings-hub-access"
    >
      Sign-in and security live in
      <RouterLink
        :to="{ name: 'access' }"
        class="text-primary hover:underline"
      >
        Access
      </RouterLink>
    </p>
  </div>
</template>
