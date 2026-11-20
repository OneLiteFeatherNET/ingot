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
import { computed } from 'vue'
import type { Principal } from '../../store/session'
import MdCard from '../md/MdCard.vue'
import MdSection from '../md/MdSection.vue'

/*
 * The read-only profile of the signed in principal. The server offers no self service profile
 * edit, so there is nothing to change here. Each value is drawn once: the username only when it
 * differs from the display name, and the teams and the instance admin tag are in Your access.
 *
 * Props:
 *   principal  { id, kind, name, displayName }
 *   sources    the sign-in sources, such as Local or LDAP (see accessView.ts)
 */
const props = withDefaults(defineProps<{
  principal: Principal
  sources?: string[]
}>(), {
  sources: () => []
})

const KINDS: Readonly<Record<string, string | undefined>> = { USER: 'User', MACHINE: 'Machine' }

const kind = computed(() => KINDS[props.principal.kind] ?? props.principal.kind)
const displayName = computed(() => props.principal.displayName || props.principal.name)
const username = computed(() => (props.principal.name === displayName.value ? '' : props.principal.name))
</script>

<template>
  <section
    class="flex flex-col"
    aria-labelledby="profile-title"
    data-ingot-component="profile-card"
  >
    <MdCard
      variant="low"
      class="border border-outline"
    >
      <MdSection
        title="Profile"
        :level="3"
        heading-id="profile-title"
      >
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
          <template v-if="username">
            <dt class="text-on-surface-variant">
              Username
            </dt>
            <dd
              class="m-0 min-w-0 break-words text-on-surface"
              data-test="username"
            >
              {{ username }}
            </dd>
          </template>
          <dt class="text-on-surface-variant">
            Kind
          </dt>
          <dd class="m-0 text-on-surface">
            {{ kind }}
          </dd>
          <template v-if="sources.length > 0">
            <dt class="text-on-surface-variant">
              Sign-in
            </dt>
            <dd
              class="m-0 min-w-0 break-words text-on-surface"
              data-test="sign-in-sources"
            >
              {{ sources.join(', ') }}
            </dd>
          </template>
        </dl>
      </MdSection>
    </MdCard>
  </section>
</template>
