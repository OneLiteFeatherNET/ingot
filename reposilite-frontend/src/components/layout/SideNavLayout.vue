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
 * when no section is open; with a section open, the page is shown with a back arrow to the index.
 *
 * Props:
 *   label        the accessible name of the side navigation
 *   sectionLabel the first crumb of the breadcrumb, and the name of the back arrow
 *   groups       [{ heading, links: [{ key, label, to }] }], where `to` is a router location
 *   current      the key of the open section, or undefined when no section is open
 *   indexTo      the router location of the index, which the back arrow and the first crumb go to
 *   narrow       true on narrow widths
 *   replace      true when a link replaces the history entry instead of adding one
 *
 * The page content is the default slot.
 */
const props = defineProps({
  label: { type: String, required: true },
  sectionLabel: { type: String, required: true },
  groups: { type: Array, required: true },
  current: { type: String, default: undefined },
  indexTo: { type: Object, required: true },
  narrow: { type: Boolean, default: false },
  replace: { type: Boolean, default: false }
})

const currentLink = computed(() => props.groups
  .flatMap(group => group.links)
  .find(link => link.key === props.current))

// On narrow widths the index replaces the page while no section is open.
const showsIndex = computed(() => props.narrow && props.current === undefined)
const showsPage = computed(() => !showsIndex.value)
const showsSideNav = computed(() => props.groups.length > 0 && (!props.narrow || showsIndex.value))
const showsBack = computed(() => props.narrow && props.groups.length > 0 && props.current !== undefined)

const linkClasses = (active) => [
  'md-state-layer md-focus-ring relative flex min-h-10 items-center rounded-corner-full px-4 md-label-large no-underline',
  'transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
  active ? 'bg-secondary-container text-on-secondary-container' : 'text-on-surface-variant hover:text-on-surface'
]
</script>

<template>
  <div class="flex min-w-0 flex-col gap-6 md:flex-row md:items-start md:gap-10">
    <nav
      v-if="showsSideNav"
      :aria-label="label"
      :class="narrow ? 'flex w-full flex-col gap-4' : 'flex w-56 shrink-0 flex-col gap-4'"
    >
      <div
        v-for="group in groups"
        :key="group.heading"
        role="group"
        :aria-label="group.heading"
        class="flex flex-col gap-1"
      >
        <p class="px-4 pb-1 md-label-medium text-on-surface-variant">
          {{ group.heading }}
        </p>
        <RouterLink
          v-for="link in group.links"
          :key="link.key"
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
      </div>
    </nav>

    <div
      v-if="showsPage"
      class="flex min-w-0 flex-1 flex-col gap-4"
    >
      <div class="flex min-w-0 items-center gap-2">
        <RouterLink
          v-if="showsBack"
          v-slot="{ href, navigate }"
          :to="indexTo"
          custom
        >
          <a
            :href="href"
            :aria-label="`Back to ${sectionLabel}`"
            class="md-state-layer md-focus-ring relative inline-flex size-10 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant hover:text-on-surface"
            @click="navigate"
          >
            <svg
              class="size-6"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M10 19l-7-7m0 0l7-7m-7 7h18"
              />
            </svg>
          </a>
        </RouterLink>
        <nav
          v-if="currentLink"
          aria-label="Breadcrumb"
        >
          <ol class="flex min-w-0 flex-wrap items-center md-label-large text-on-surface-variant">
            <li class="after:px-2 after:content-['/']">
              <RouterLink
                v-slot="{ href, navigate }"
                :to="indexTo"
                custom
              >
                <a
                  :href="href"
                  class="md-focus-ring rounded-corner-small hover:text-on-surface"
                  @click="navigate"
                >{{ sectionLabel }}</a>
              </RouterLink>
            </li>
            <li
              aria-current="page"
              class="text-on-surface"
            >
              {{ currentLink.label }}
            </li>
          </ol>
        </nav>
      </div>

      <slot />
    </div>
  </div>
</template>
