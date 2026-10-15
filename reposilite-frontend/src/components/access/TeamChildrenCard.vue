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
import { useId } from 'vue'
import MdChip from '../md/MdChip.vue'

/*
 * The "Child teams" card of a team: the teams that list this team as a parent, each a link to the
 * team. The list is read only; a child team changes its parents on its own page.
 *
 * Props:
 *   names  the names of the child teams
 */
defineProps({
  names: {
    type: Array,
    default: () => []
  }
})

const titleId = useId()
</script>

<template>
  <section
    class="flex flex-col gap-3 rounded-corner-large bg-surface-container-low px-4 py-4"
    :aria-labelledby="titleId"
    data-test="team-children"
  >
    <div class="flex flex-col gap-1">
      <h4
        :id="titleId"
        class="md-title-medium text-on-surface"
      >
        Child teams
      </h4>
      <p class="md-body-small text-on-surface-variant">
        These teams inherit the permission nodes of this team.
      </p>
    </div>
    <ul
      v-if="names.length > 0"
      class="m-0 flex list-none flex-wrap items-center gap-2 p-0"
      aria-label="Child teams"
    >
      <li
        v-for="team in names"
        :key="team"
      >
        <router-link
          v-slot="{ href, navigate }"
          :to="{ name: 'access-team', params: { name: team } }"
          custom
        >
          <MdChip
            data-test="child-team"
            :href="href"
            @click="navigate"
          >
            {{ team }}
          </MdChip>
        </router-link>
      </li>
    </ul>
    <p
      v-else
      class="md-body-medium text-on-surface-variant"
      data-test="children-empty"
    >
      No team inherits from this team.
    </p>
  </section>
</template>
