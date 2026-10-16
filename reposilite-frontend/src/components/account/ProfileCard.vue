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
import { computed } from 'vue'
import TokenTag from '../tokens/TokenTag.vue'

/*
 * The read-only profile of the signed in principal. The server offers no self service profile
 * edit, so there is nothing to change here.
 *
 * Props:
 *   principal      { id, kind, name, displayName }
 *   teams          the names of the principal's teams
 *   instanceAdmin  whether the principal administers the instance (shows a badge)
 */
const props = defineProps({
  principal: {
    type: Object,
    required: true
  },
  teams: {
    type: Array,
    default: () => []
  },
  instanceAdmin: {
    type: Boolean,
    default: false
  }
})

const KINDS = { USER: 'User', MACHINE: 'Machine' }

const kind = computed(() => KINDS[props.principal.kind] ?? props.principal.kind)
const displayName = computed(() => props.principal.displayName || props.principal.name)
</script>

<template>
  <section
    class="flex flex-col gap-4 rounded-corner-large bg-surface-container-low px-4 py-4"
    aria-labelledby="profile-title"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h3
        id="profile-title"
        class="md-title-large text-on-surface"
      >
        Profile
      </h3>
      <TokenTag
        v-if="instanceAdmin"
        accent
        data-test="instance-admin"
      >
        Instance admin
      </TokenTag>
    </div>
    <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 md-body-medium">
      <dt class="text-on-surface-variant">
        Display name
      </dt>
      <dd
        class="m-0 min-w-0 break-words text-on-surface"
        data-test="display-name"
      >
        {{ displayName }}
      </dd>
      <dt class="text-on-surface-variant">
        Username
      </dt>
      <dd class="m-0 min-w-0 break-words text-on-surface">
        {{ principal.name }}
      </dd>
      <dt class="text-on-surface-variant">
        Kind
      </dt>
      <dd class="m-0 text-on-surface">
        {{ kind }}
      </dd>
      <dt class="text-on-surface-variant">
        Teams
      </dt>
      <dd class="m-0 flex min-w-0 flex-wrap items-center gap-2 text-on-surface">
        <template v-if="teams.length > 0">
          <TokenTag
            v-for="team in teams"
            :key="team"
            data-test="team"
          >
            {{ team }}
          </TokenTag>
        </template>
        <span
          v-else
          class="text-on-surface-variant"
        >
          No teams
        </span>
      </dd>
    </dl>
  </section>
</template>
