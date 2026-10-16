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

// "Index of" first, then one crumb per directory. The last crumb is the directory being viewed and
// is the current page, so it is text rather than a link. A long name is cut with an ellipsis, so its
// full name is kept as a title.
const trail = computed(() => {
  const directories = segments.value.map((segment, index) => {
    const path = segments.value.slice(0, index + 1).join('/')
    return { key: path, to: browseLocation(path), name: segment, title: segment }
  })
  const crumbs = [
    { key: '', to: { name: 'overview' }, name: 'Index of', root: true },
    ...directories
  ]
  return crumbs.map((crumb, index) => ({ ...crumb, current: index === crumbs.length - 1 }))
})

const parentLocation = computed(() => browseLocation(parentPathOf(props.path)))
</script>

<template>
  <nav
    aria-label="Breadcrumb"
    class="flex min-w-0 flex-wrap items-center gap-y-1 md-title-medium"
  >
    <ol class="flex min-w-0 max-w-full flex-wrap items-center">
      <li
        v-for="(crumb, index) of trail"
        :key="crumb.key"
        class="flex min-w-0 max-w-full items-center"
      >
        <!-- Between two crumbs only. Hidden from assistive technology, because the list already gives the order. -->
        <span
          v-if="index > 0"
          aria-hidden="true"
          class="px-1 text-outline"
        >/</span>
        <span
          v-if="crumb.current"
          class="block min-w-0 truncate px-1 py-1 text-on-surface"
          :title="crumb.title"
          aria-current="page"
        >{{ crumb.name }}</span>
        <router-link
          v-else
          :to="crumb.to"
          :title="crumb.title"
          class="md-state-layer md-focus-ring inline-flex min-h-12 min-w-0 max-w-full items-center rounded-corner-small px-1 py-1 hover:text-primary"
          :class="crumb.root ? 'text-on-surface-variant' : 'text-on-surface'"
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
