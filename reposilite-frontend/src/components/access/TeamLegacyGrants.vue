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
import { computed, useId } from 'vue'
import TokenTag from '../tokens/TokenTag.vue'
import { ROLES, grantScopeLabel, pathPrefixLabel, roleExplanation } from './accessTeams'

/*
 * The role grants a team still has from before permission nodes, shown for reading only in a
 * section that starts collapsed. They have no controls: nothing here edits or removes a grant.
 * Without grants nothing is shown.
 *
 * Props:
 *   grants  [{ repository, pathPrefix, role }] as the server has them
 */
const props = defineProps({
  grants: {
    type: Array,
    required: true
  }
})

const titleId = useId()

const rows = computed(() => props.grants.map((grant, index) => ({
  index,
  scope: grantScopeLabel(grant.repository),
  prefix: pathPrefixLabel(grant.pathPrefix),
  role: ROLES.find(entry => entry.value === grant.role)?.label ?? grant.role,
  explanation: roleExplanation(grant.role)
})))
</script>

<template>
  <details
    v-if="rows.length > 0"
    class="rounded-corner-large bg-surface-container-low px-4 py-4"
    data-test="legacy-team-grants"
  >
    <summary
      :id="titleId"
      class="md-focus-ring cursor-pointer rounded-corner-extra-small md-title-medium text-on-surface"
      data-test="legacy-team-grants-summary"
    >
      Legacy grants ({{ rows.length }})
    </summary>
    <div class="mt-3 flex flex-col gap-3">
      <p class="md-body-medium text-on-surface-variant">
        These role grants came from before permission nodes and still apply. They are read only here.
      </p>
      <ul
        class="m-0 flex list-none flex-col divide-y divide-outline-variant p-0"
        :aria-labelledby="titleId"
      >
        <li
          v-for="row in rows"
          :key="row.index"
          data-test="legacy-team-grant"
          class="flex flex-col gap-1 py-2"
        >
          <span
            class="md-title-small text-on-surface [overflow-wrap:anywhere]"
            data-test="legacy-team-grant-repository"
          >{{ row.scope }}</span>
          <span
            class="md-body-medium text-on-surface-variant [overflow-wrap:anywhere]"
            data-test="legacy-team-grant-prefix"
          >{{ row.prefix }}</span>
          <span class="flex flex-wrap items-center gap-2">
            <TokenTag data-test="legacy-team-grant-role">{{ row.role }}</TokenTag>
            <span class="md-body-small text-on-surface-variant">{{ row.explanation }}</span>
          </span>
        </li>
      </ul>
    </div>
  </details>
</template>
