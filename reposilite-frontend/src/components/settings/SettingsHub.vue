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
import StatTile from '../dashboard/StatTile.vue'
import type { SettingsGlanceRow } from './settingsHub'

/*
 * The part of the Overview page of /settings below its heading that the tree cannot show: At a glance, the count of every
 * settings collection, each a stat card that links to its page. The instance status is a section of Overview, not of this
 * hub. The Administration pages themselves are the tree beside the page, so this page lists none of them beyond the counts.
 * The root carries the settings-hub anchor. Needs attention is SettingsAttention, which the frame draws above the index on
 * narrow widths.
 *
 * Props:
 *   glance     [{ key, label, count, to }], as glanceRows builds them; `to` is the router location of the page
 */
defineProps<{
  glance: SettingsGlanceRow[]
}>()
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
        class="md-title-large text-on-surface"
      >
        At a glance
      </h2>
      <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <StatTile
          v-for="row in glance"
          :key="row.key"
          data-test="glance-card"
          :label="row.label"
          :value="`${row.count}`"
          :to="row.to"
        />
      </div>
    </section>
  </div>
</template>
