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

  // `segment` is the directory name as it is in the path. `name` adds the separator shown after
  // it, and is what the crumb displays. The full segment is kept as a title, because a long one
  // is cut with an ellipsis.
  const crumbs = segments.map((segment, index) => ({
    link: '/' + segments.slice(0, index + 1).join('/'),
    segment,
    name: index === segments.length - 1 ? segment : segment + '/',
    current: index === segments.length - 1
  }))

  return [{ link: '/', segment: '/', name: '/', current: segments.length === 0 }, ...crumbs]
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
        class="flex min-w-0 max-w-full items-center"
      >
        <span
          v-if="crumb.current"
          class="block min-w-0 truncate px-1 py-1 text-on-surface"
          :title="crumb.segment"
          :aria-current="'page'"
        >{{ crumb.name }}</span>
        <router-link
          v-else
          :to="crumb.link"
          :title="crumb.segment"
          class="md-focus-ring inline-flex min-h-12 min-w-0 max-w-full items-center rounded-corner-small px-1 py-1 text-on-surface hover:text-primary"
        >
          <span class="min-w-0 truncate">{{ crumb.name }}</span>
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
