<!--
  - Copyright (c) 2023 dzikoysk
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
import { useRoute } from 'vue-router'
import { property } from '../../helpers/vue-extensions'
import MdIconButton from '../md/MdIconButton.vue'
import BrowserIcon from './BrowserIcon.vue'

defineProps({
  parentPath: property(String, true)
})

const route = useRoute()

const breadcrumbs = computed(() => {
  const segments = route.path.split('/').filter(segment => segment !== '')

  const crumbs = segments.map((name, index) => ({
    link: '/' + segments.slice(0, index + 1).join('/'),
    name: index === segments.length - 1 ? name : name + '/',
    current: index === segments.length - 1
  }))

  return [{ link: '/', name: '/', current: segments.length === 0 }, ...crumbs]
})
</script>

<template>
  <nav
    aria-label="Breadcrumb"
    class="flex min-w-0 flex-wrap items-center gap-y-1 md-title-medium"
  >
    <router-link
      to="/"
      class="md-state-layer md-focus-ring inline-flex min-h-12 items-center rounded-corner-small px-2 py-1 text-on-surface-variant hover:text-primary"
    >
      Index of
    </router-link>
    <ol class="flex min-w-0 flex-wrap items-center">
      <li
        v-for="crumb of breadcrumbs"
        :key="crumb.link"
        class="flex items-center"
      >
        <span
          v-if="crumb.current"
          class="px-1 py-1 text-on-surface"
          :aria-current="'page'"
        >{{ crumb.name }}</span>
        <router-link
          v-else
          :to="crumb.link"
          class="md-focus-ring rounded-corner-small px-1 py-1 text-on-surface hover:text-primary"
        >
          {{ crumb.name }}
        </router-link>
      </li>
    </ol>
    <!-- The icon button is the link itself, so a click still navigates through the router. -->
    <router-link
      v-slot="{ href, navigate }"
      :to="parentPath"
      custom
    >
      <MdIconButton
        :href="href"
        aria-label="Parent directory"
        title="Parent directory"
        class="ml-1"
        @click="navigate"
      >
        <BrowserIcon name="arrowUp" />
      </MdIconButton>
    </router-link>
  </nav>
</template>
