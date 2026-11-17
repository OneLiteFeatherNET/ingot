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
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import ArrowRightIcon from '../icons/ArrowRightIcon.vue'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'

/*
 * The note at the top of the General section of the Search index page. A full rebuild runs on the Index tab of
 * Maintenance, so the note says what a rebuild does and where it runs, and offers the link to that tab.
 *
 * Props:
 *   to  the router location of the Index tab of Maintenance
 */
defineProps<{ to: RouteLocationRaw }>()
</script>

<template>
  <section
    class="mb-6"
    data-ingot-component="search-rebuild-note"
  >
    <MdCard
      variant="outlined"
      class="flex flex-col items-start gap-4 p-4"
    >
      <p class="md-body-large text-on-surface">
        Full rebuild. Rebuilding drops and refills the whole index from storage. It runs on the Maintenance page, where running jobs are shown.
      </p>
      <RouterLink
        v-slot="{ href, navigate }"
        :to="to"
        custom
      >
        <MdButton
          variant="tonal"
          :href="href"
          @click="navigate"
        >
          Open Maintenance
          <ArrowRightIcon />
        </MdButton>
      </RouterLink>
    </MdCard>
  </section>
</template>
