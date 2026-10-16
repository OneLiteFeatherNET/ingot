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
import { property } from '../../helpers/vue-extensions'
import { browseLocation, parentPathOf } from '../../router/paths'
import MdIconButton from '../md/MdIconButton.vue'
import BrowserIcon from './BrowserIcon.vue'

// The repository path being browsed, without a leading slash, as the file browser knows it.
const props = defineProps({
  path: property(String, true)
})

const segments = computed(() => props.path.split('/').filter(segment => segment !== ''))

// The root first, then one crumb per directory. The last one is the directory being viewed.
// `segment` is the directory name as it is in the path. `name` adds the separator shown after
// it, and is what the crumb displays. The full segment is kept as a title, because a long one
// is cut with an ellipsis.
const trail = computed(() => [
  { key: '', to: { name: 'overview' }, segment: '/', name: '/', current: segments.value.length === 0 },
  ...segments.value.map((segment, index) => ({
    key: segments.value.slice(0, index + 1).join('/'),
    to: browseLocation(segments.value.slice(0, index + 1).join('/')),
    segment,
    name: index === segments.value.length - 1 ? segment : segment + '/',
    current: index === segments.value.length - 1
  }))
])

const parentLocation = computed(() => browseLocation(parentPathOf(props.path)))
</script>

<template>
  <nav
    aria-label="Breadcrumb"
    class="flex min-w-0 flex-wrap items-center gap-y-1 md-title-medium"
  >
    <router-link
      :to="{ name: 'overview' }"
      class="md-state-layer md-focus-ring inline-flex min-h-12 items-center rounded-corner-small px-2 py-1 text-on-surface-variant hover:text-primary"
    >
      Index of
    </router-link>
    <ol class="flex min-w-0 flex-wrap items-center">
      <li
        v-for="crumb of trail"
        :key="crumb.key"
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
          :to="crumb.to"
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
      :to="parentLocation"
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
