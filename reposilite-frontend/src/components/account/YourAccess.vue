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

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import TokenTag from '../tokens/TokenTag.vue'
import MdCard from '../md/MdCard.vue'
import type { AccessView } from './accessView'

/*
 * The read only section "Your access" of My account. It draws the view model of the own access summary and has no
 * control that changes anything. The account view fetches the summary and passes it in.
 *
 * Props:
 *   access         the view model (see accessView.ts), or its loading and unavailable states
 *   principalName  the signed in principal, the one the Explain links check
 *   mayCheck       whether the principal may use the Access area; then a permission links to the check inside the
 *                  dashboard (a router link, so a sub-path deployment resolves it), otherwise to the how-to page
 *
 * The how-to page is outside the dashboard, so its link opens in a new tab and says so in its name.
 */
const props = defineProps<{
  access: AccessView
  principalName: string
  mayCheck: boolean
}>()

const HOW_TO = 'https://github.com/OneLiteFeatherNET/ingot/blob/main/docs/gitbook/manage/access/check-access.md'

const checkRoute = (node: string) => ({
  name: 'access-check',
  query: { principal: props.principalName, node }
})
</script>

<template>
  <section
    class="flex flex-col"
    aria-labelledby="access-title"
    data-ingot-component="your-access"
  >
    <MdCard
      variant="low"
      class="flex flex-col gap-4"
    >
      <div class="flex flex-col gap-1">
        <h3
          id="access-title"
          class="md-title-large text-on-surface"
        >
          Your access
        </h3>
        <p class="md-body-medium text-on-surface-variant">
          What your account may do on this instance. This page only shows it and changes nothing.
        </p>
      </div>

      <p
        v-if="access.status === 'loading'"
        role="status"
        class="md-body-medium text-on-surface-variant"
      >
        Loading your access
      </p>
      <p
        v-else-if="access.status === 'unavailable'"
        class="md-body-medium text-on-surface-variant"
      >
        The access summary is not available on this server.
      </p>
      <template v-else>
        <p
          v-if="access.tokenNarrowed"
          class="md-body-medium text-on-surface-variant"
        >
          This access token narrows the list below. It may use only the rights listed here, even where your account has more.
        </p>

        <TokenTag
          v-if="access.instanceAdmin"
          accent
          data-test="instance-admin"
        >
          Instance admin
        </TokenTag>

        <div class="flex flex-col gap-2">
          <h4 class="md-title-small text-on-surface">
            Teams
          </h4>
          <ul
            v-if="access.teams.length > 0"
            class="m-0 flex list-none flex-col gap-1 p-0 md-body-medium text-on-surface"
          >
            <li
              v-for="team in access.teams"
              :key="team.name"
              data-test="team"
            >
              {{ team.name }}
              <span
                v-if="team.parents.length > 0"
                class="text-on-surface-variant"
              >inherits from {{ team.parents.join(', ') }}</span>
            </li>
          </ul>
          <p
            v-else
            class="md-body-medium text-on-surface-variant"
          >
            No teams
          </p>
        </div>

        <div class="flex flex-col gap-2">
          <h4 class="md-title-small text-on-surface">
            Permissions
          </h4>
          <ul
            v-if="access.permissions.allowed.length > 0"
            class="m-0 flex list-none flex-col gap-2 p-0"
          >
            <li
              v-for="line in access.permissions.allowed"
              :key="line.node"
              data-test="allowed-permission"
              class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 md-body-medium text-on-surface"
            >
              <span class="min-w-0 break-words">{{ line.meaning }}</span>
              <span class="flex min-w-0 items-baseline gap-3">
                <code class="min-w-0 break-all md-label-small text-on-surface-variant">{{ line.node }}</code>
                <RouterLink
                  v-if="mayCheck"
                  :to="checkRoute(line.node)"
                  :aria-label="`Explain ${line.node}`"
                  class="md-label-large text-primary"
                >Explain</RouterLink>
                <a
                  v-else
                  :href="HOW_TO"
                  target="_blank"
                  rel="noopener"
                  :aria-label="`Explain ${line.node} (opens in a new tab)`"
                  class="md-label-large text-primary"
                >Explain</a>
              </span>
            </li>
          </ul>
          <p
            v-else
            class="md-body-medium text-on-surface-variant"
          >
            No permission is granted directly.
          </p>

          <div
            v-if="access.permissions.denied.length > 0"
            class="flex flex-col gap-2"
          >
            <h5 class="md-label-large text-on-surface-variant">
              Denied
            </h5>
            <ul class="m-0 flex list-none flex-col gap-2 p-0">
              <li
                v-for="line in access.permissions.denied"
                :key="line.node"
                data-test="denied-permission"
                class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 md-body-medium text-on-surface"
              >
                <span class="min-w-0 break-words">{{ line.meaning }}</span>
                <span class="flex min-w-0 items-baseline gap-3">
                  <code class="min-w-0 break-all md-label-small text-on-surface-variant">{{ line.node }}</code>
                  <RouterLink
                    v-if="mayCheck"
                    :to="checkRoute(line.node)"
                    :aria-label="`Explain ${line.node}`"
                    class="md-label-large text-primary"
                  >Explain</RouterLink>
                  <a
                    v-else
                    :href="HOW_TO"
                    target="_blank"
                    rel="noopener"
                    :aria-label="`Explain ${line.node} (opens in a new tab)`"
                    class="md-label-large text-primary"
                  >Explain</a>
                </span>
              </li>
            </ul>
          </div>
        </div>

        <div class="flex flex-col gap-2">
          <h4 class="md-title-small text-on-surface">
            Repository access
          </h4>
          <ul
            v-if="access.scopes.length > 0"
            class="m-0 flex list-none flex-col gap-2 p-0"
          >
            <li
              v-for="(scope, index) in access.scopes"
              :key="`${scope.repository}-${scope.via}-${index}`"
              data-test="scope"
              class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1 md-body-medium text-on-surface"
            >
              <span class="min-w-0 break-words">
                {{ scope.meaning }}, through {{ scope.via }}
              </span>
              <a
                :href="HOW_TO"
                target="_blank"
                rel="noopener"
                :aria-label="`Explain ${scope.meaning} (opens in a new tab)`"
                class="md-label-large text-primary"
              >Explain</a>
            </li>
          </ul>
          <p
            v-else
            class="md-body-medium text-on-surface-variant"
          >
            No repository is granted.
          </p>
          <ul
            v-if="access.unknown.length > 0"
            class="m-0 flex list-none flex-col gap-1 p-0 md-body-medium text-on-surface"
          >
            <li
              v-for="(line, index) in access.unknown"
              :key="index"
              data-test="unknown"
            >
              {{ line.text }}
            </li>
          </ul>
        </div>
      </template>
    </MdCard>
  </section>
</template>
