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
import { ACCESS_DOMAINS } from './accessDomains'

/*
 * What the Settings page shows for a domain that is edited under Access: a short card with the links to
 * those pages, in place of the generic form. `domain` names the entry of ACCESS_DOMAINS, `title` its heading.
 */
const props = defineProps({
  domain: { type: String, required: true },
  title: { type: String, required: true }
})

const entry = () => ACCESS_DOMAINS[props.domain]
</script>

<template>
  <section
    aria-labelledby="access-domain-title"
    class="flex flex-col gap-3 rounded-corner-medium border border-outline-variant bg-surface-container-low p-4"
    data-test="access-domain"
  >
    <h3
      id="access-domain-title"
      class="md-title-medium text-on-surface"
    >
      {{ title }} is managed under Access
    </h3>
    <p class="md-body-medium text-on-surface-variant">
      {{ entry().text }}
    </p>
    <ul class="flex flex-wrap list-none gap-2 p-0">
      <li
        v-for="link in entry().links"
        :key="link.name"
      >
        <RouterLink
          :to="{ name: link.name }"
          class="md-state-layer md-focus-ring relative inline-flex min-h-10 items-center rounded-corner-full border border-outline-variant px-4 md-label-large text-primary no-underline hover:underline"
        >
          {{ link.label }}
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
