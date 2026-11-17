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

<script setup lang="ts">
import { computed } from 'vue'
import { browseLocation, parentPathOf } from '../../router/paths'
import MdBreadcrumb, { type MdBreadcrumbItem } from '../md/MdBreadcrumb.vue'
import MdIconButton from '../md/MdIconButton.vue'
import BrowserIcon from './BrowserIcon.vue'

// The repository path being browsed, without a leading slash, as the file browser knows it.
const props = defineProps<{
  path: string
}>()

const segments = computed(() => props.path.split('/').filter(segment => segment !== ''))

// "Index of" first, then one crumb per directory. The last crumb is the directory being viewed and
// is the current page, so MdBreadcrumb draws it as text. A long name is cut with an ellipsis, so its
// full name is kept as a title.
const trail = computed<MdBreadcrumbItem[]>(() => {
  const directories = segments.value.map((segment, index) => ({
    label: segment,
    to: browseLocation(segments.value.slice(0, index + 1).join('/')),
    title: segment
  }))
  return [{ label: 'Index of', to: { name: 'overview' } }, ...directories]
})

const parentLocation = computed(() => browseLocation(parentPathOf(props.path)))
</script>

<template>
  <div class="flex min-w-0 flex-wrap items-center gap-y-1 md-title-medium">
    <!-- The anchor stays on the trail's nav, so plugin stylesheets keep matching it. -->
    <MdBreadcrumb
      :items="trail"
      class="min-w-0"
      data-ingot-component="breadcrumb-navigation"
    />
    <!-- The parent control is an action beside the trail, not a crumb. Its link is the icon button itself. -->
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
  </div>
</template>
