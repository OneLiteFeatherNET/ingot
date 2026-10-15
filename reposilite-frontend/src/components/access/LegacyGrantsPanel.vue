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
import TokenTag from '../tokens/TokenTag.vue'

/*
 * The routes a migrated access token had, shown for reading only. They still apply to that
 * token, but they are not edited here: the way forward is a team role on the repository.
 *
 * Props:
 *   grants  [{ path, read, write }], one entry per route path
 */
defineProps({
  grants: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-large bg-surface-container-low px-4 py-4"
    aria-labelledby="legacy-grants-title"
    data-test="legacy-grants"
  >
    <h3
      id="legacy-grants-title"
      class="md-title-large text-on-surface"
    >
      Legacy grants (migrated from an access token)
    </h3>
    <p class="md-body-medium text-on-surface-variant">
      Convert these to team roles: add the machine to a team with a role on the repository.
      <router-link
        :to="{ name: 'access-teams' }"
        class="md-focus-ring rounded-corner-extra-small text-primary"
        data-test="teams-link"
      >
        Open teams
      </router-link>
    </p>
    <ul
      v-if="grants.length > 0"
      class="m-0 flex list-none flex-col divide-y divide-outline-variant p-0"
    >
      <li
        v-for="grant in grants"
        :key="grant.path"
        data-test="legacy-grant"
        class="flex flex-wrap items-center justify-between gap-2 py-2"
      >
        <span class="min-w-0 break-all md-body-medium text-on-surface">{{ grant.path }}</span>
        <span class="flex gap-2">
          <TokenTag v-if="grant.read">Read</TokenTag>
          <TokenTag v-if="grant.write">Write</TokenTag>
        </span>
      </li>
    </ul>
    <p
      v-else
      class="md-body-medium text-on-surface-variant"
    >
      The token had no routes.
    </p>
  </section>
</template>
