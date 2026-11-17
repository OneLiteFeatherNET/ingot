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
/*
 * Material Design 3 breadcrumb trail. A crumb with a `to` route is a RouterLink, so it keeps the
 * router base and navigates without a reload. A crumb with an `href` is a plain link, for a page
 * outside the dashboard. The last crumb is the current page: it carries aria-current="page" and is
 * never a link. The separator follows each crumb but the last and is hidden from assistive
 * technology. The middle crumbs truncate below the crumb width token, so a long trail keeps its
 * first and last crumbs.
 */
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

export interface MdBreadcrumbItem {
  label: string
  to?: RouteLocationRaw
  href?: string
  title?: string
  // A muted link is drawn in on-surface-variant, as the root of the file browser trail is.
  muted?: boolean
}

// "default" is the MD3 label trail. "large" is the file browser trail: title-medium text, on-surface
// links with primary on hover, and tighter spacing without the gap between crumbs.
export type MdBreadcrumbSize = 'default' | 'large'

const props = withDefaults(defineProps<{
  items: MdBreadcrumbItem[]
  label?: string
  size?: MdBreadcrumbSize
}>(), {
  label: 'Breadcrumb',
  size: 'default'
})

defineSlots<{
  default?(): unknown
}>()

// Every crumb, the current one included, has the same minimum height, so the text lines up on one centre line.
const CRUMB = 'flex min-h-12 min-w-0 items-center'
const LINK = `md-touch-target md-focus-ring rounded-corner-small ${CRUMB}`
const MIDDLE = 'min-w-0 max-w-md-crumb'

const large = computed(() => props.size === 'large')
const isLast = (index: number) => index === props.items.length - 1
const isMiddle = (index: number) => index > 0 && !isLast(index)
const crumbClass = (index: number) => (isMiddle(index) ? MIDDLE : 'min-w-0')

const listClass = computed(() => (large.value
  ? 'md-title-medium flex flex-wrap items-center text-on-surface'
  : 'md-label-large flex flex-wrap items-center gap-2 text-on-surface-variant'))
const itemClass = computed(() => (large.value
  ? 'flex min-w-0 max-w-full items-center'
  : 'flex min-w-0 max-w-full items-center gap-2'))
const linkClass = (index: number) => {
  const colour = props.items[index].muted ? 'text-on-surface-variant' : (large.value ? 'text-on-surface' : 'text-primary')
  return [LINK, crumbClass(index), colour, large.value && 'px-1 py-1 hover:text-primary']
}
const currentClass = (index: number) => [CRUMB, crumbClass(index), 'text-on-surface', large.value && 'px-1 py-1']
const separatorClass = computed(() => (large.value ? 'px-1 text-outline' : 'text-outline'))
</script>

<template>
  <nav
    :aria-label="label"
    data-ingot-component="md-breadcrumb"
  >
    <ol :class="listClass">
      <li
        v-for="(item, index) in items"
        :key="index"
        :class="itemClass"
      >
        <RouterLink
          v-if="item.to !== undefined && !isLast(index)"
          :to="item.to"
          :title="item.title"
          :class="linkClass(index)"
        >
          <span class="min-w-0 truncate">{{ item.label }}</span>
        </RouterLink>
        <a
          v-else-if="item.href && !isLast(index)"
          :href="item.href"
          :title="item.title"
          :class="linkClass(index)"
        ><span class="min-w-0 truncate">{{ item.label }}</span></a>
        <span
          v-else
          :aria-current="isLast(index) ? 'page' : undefined"
          :title="item.title"
          :class="currentClass(index)"
        ><span class="min-w-0 truncate">{{ item.label }}</span></span>
        <span
          v-if="!isLast(index)"
          aria-hidden="true"
          data-test="breadcrumb-separator"
          :class="separatorClass"
        >/</span>
      </li>
    </ol>
  </nav>
</template>
