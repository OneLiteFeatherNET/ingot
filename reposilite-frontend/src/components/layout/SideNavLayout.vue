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
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

/*
 * The split layout of a page with sections, such as Access and Settings. On wide widths the sections
 * are a side navigation in headed groups, and the page of the open section is on the right. Each
 * section is a real link to its own address. The caller decides the narrow state from a media query,
 * so this component reads no window. On narrow widths the side navigation is an index of the groups
 * when no section is open; with a section open, the page is shown beside the back arrow of the frame (see
 * AdministrationFrame, which also draws the heading and the trail above this layout).
 *
 * Props:
 *   label        the accessible name of the side navigation
 *   groups       [{ heading, links: [{ key, label, to }] }], where `to` is a router location
 *   current      the key of the open section, or undefined when no section is open
 *   narrow       true below 1240 pixels; from 1240 pixels the navigation is a sticky column beside the page
 *   replace      true when a link replaces the history entry instead of adding one
 *
 * The page content is the default slot.
 */
const props = defineProps({
  label: { type: String, required: true },
  groups: { type: Array, required: true },
  current: { type: String, default: undefined },
  narrow: { type: Boolean, default: false },
  replace: { type: Boolean, default: false }
})

// On narrow widths the index replaces the page while no section is open.
const showsIndex = computed(() => props.narrow && props.current === undefined)
const showsPage = computed(() => !showsIndex.value)
const showsSideNav = computed(() => props.groups.length > 0 && (!props.narrow || showsIndex.value))

// The id of the visible heading of a group. Each group list is labelled by it, so a screen reader names the group on entry.
const headingId = (group) => `admin-group-${group.heading.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`

const linkClasses = (active) => [
  'md-state-layer md-focus-ring relative flex min-h-10 items-center rounded-corner-full px-4 md-label-large no-underline',
  'transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
  active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant hover:text-on-surface'
]
</script>

<template>
  <div
    class="flex min-w-0 flex-col gap-6 min-[1240px]:flex-row min-[1240px]:items-start min-[1240px]:gap-10"
    data-ingot-component="side-nav-layout"
  >
    <nav
      v-if="showsSideNav"
      data-ingot-part="nav"
      :aria-label="label"
      :class="narrow
        ? 'flex w-full flex-col gap-4'
        : 'sticky top-[calc(4rem+1rem)] flex max-h-[calc(100dvh-6rem)] w-52 shrink-0 flex-col gap-4 overflow-y-auto'"
    >
      <div
        v-for="group in groups"
        :key="group.heading ?? group.label"
        class="flex flex-col gap-1"
      >
        <p
          v-if="group.heading"
          :id="headingId(group)"
          class="px-4 pb-1 md-label-medium text-on-surface-variant"
        >
          {{ group.heading }}
        </p>
        <ul
          :aria-labelledby="group.heading ? headingId(group) : undefined"
          :aria-label="group.heading ? undefined : group.label"
          class="flex flex-col gap-1"
        >
          <li
            v-for="link in group.links"
            :key="link.key"
          >
            <RouterLink
              v-slot="{ href, navigate }"
              :to="link.to"
              :replace="replace"
              custom
            >
              <a
                :href="href"
                :aria-current="link.key === current ? 'page' : undefined"
                :class="linkClasses(link.key === current)"
                @click="navigate"
              >
                {{ link.label }}
              </a>
            </RouterLink>
          </li>
        </ul>
      </div>
    </nav>

    <div
      v-if="showsPage"
      data-ingot-part="page"
      class="flex min-w-0 flex-1 flex-col gap-4"
    >
      <slot />
    </div>
  </div>
</template>
