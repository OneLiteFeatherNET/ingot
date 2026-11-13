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
 * The part of the Overview page of /settings below its heading that the tree cannot show: At a glance, the count of every
 * settings collection, and the link to the Dashboard, where the instance status is. The Administration pages themselves are
 * the tree beside the page, so this page lists none of them. The root carries the settings-hub anchor. Needs attention is
 * SettingsAttention, which the frame draws above the index on narrow widths.
 *
 * Props:
 *   glance     [{ key, label, count }], as glanceRows builds them
 *   dashboard  true when the visitor may open the Dashboard
 */
defineProps({
  glance: { type: Array, required: true },
  dashboard: { type: Boolean, default: false }
})
</script>

<template>
  <div
    class="flex flex-col gap-8"
    data-test="settings-hub"
    data-ingot-component="settings-hub"
  >
    <section
      v-if="glance.length > 0"
      aria-labelledby="settings-glance-title"
      class="flex flex-col gap-3"
    >
      <h2
        id="settings-glance-title"
        class="md-title-medium text-on-surface"
      >
        At a glance
      </h2>
      <dl class="grid grid-cols-[1fr_auto] gap-x-6 gap-y-2 md-body-large">
        <template
          v-for="row in glance"
          :key="row.key"
        >
          <dt class="text-on-surface">
            {{ row.label }}
          </dt>
          <dd class="text-right text-on-surface-variant tabular-nums">
            {{ row.count }}
          </dd>
        </template>
      </dl>
    </section>

    <p
      v-if="dashboard"
      class="md-body-medium text-on-surface-variant"
      data-test="settings-hub-dashboard-line"
    >
      Instance status and resources are on the
      <RouterLink
        :to="{ name: 'dashboard' }"
        class="text-primary underline"
        data-test="settings-hub-dashboard"
      >
        Dashboard
      </RouterLink>
    </p>
  </div>
</template>
