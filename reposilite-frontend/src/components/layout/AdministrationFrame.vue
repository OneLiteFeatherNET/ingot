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
import SideNavLayout from './SideNavLayout.vue'

/*
 * The frame of every Administration page. The header draws, in this order: the trail (Administration, the group of the
 * page as plain text and, on a detail page, the list page as a link), the one level one heading that names the page,
 * the description of the page, and the page actions with the shortcuts of the page on the right. Beside it, below the
 * header, the Administration tree and the page content. The heading is the label of the current tree entry unless a
 * title is given (a detail page names its item, a plugin page its manifest title). The description is the one of the
 * current entry unless one is given.
 *
 * Props:
 *   title        the heading, when it is not the label of the current entry
 *   description  the one-line description under the heading; by default the description of the current entry
 *   detail       true on a detail page: the trail then adds the list page of the current entry as a link
 *   label        the accessible name of the side navigation
 *   sectionLabel the first crumb, and the name of the back arrow
 *   groups       the Administration tree: [{ id, heading, links: [{ key, label, description, to }] }]; empty draws no side navigation
 *   current      the key of the entry on screen, or undefined
 *   indexTo      the router location of the index, which the first crumb and the back arrow go to
 *   shortcuts    the links to related pages: [{ label, to }], drawn in the header
 *   narrow       true below 1240 pixels, where the tree is an index (see SideNavLayout) and the trail is its last crumb
 *   replace      true when a link replaces the history entry
 *   reading      true for a reading page (text and forms): the content keeps a 960 pixel measure. A wide page (a list
 *                or a table, the default) takes the full width after the side navigation.
 *
 * Slots: `actions` (buttons of the page, beside the heading) and the default slot (the page content).
 */
const props = defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: undefined },
  detail: { type: Boolean, default: false },
  label: { type: String, required: true },
  sectionLabel: { type: String, required: true },
  groups: { type: Array, required: true },
  current: { type: String, default: undefined },
  indexTo: { type: Object, required: true },
  shortcuts: { type: Array, default: () => [] },
  narrow: { type: Boolean, default: false },
  replace: { type: Boolean, default: false },
  reading: { type: Boolean, default: false },
  unsaved: { type: Array, default: () => [] },
  overview: { type: Boolean, default: false }
})

const currentLink = computed(() => props.groups
  .flatMap(group => group.links)
  .find(link => link.key === props.current))

const currentGroup = computed(() => props.groups
  .find(group => group.links.some(link => link.key === props.current)))

// The trail: the section, the group of the current entry, and on a detail page the list page. Empty when no entry is
// on screen, as on the settings hub, whose heading already is the section.
const crumbs = computed(() => {
  if (!currentLink.value || !currentGroup.value || currentGroup.value.heading === undefined) return []
  const trail = [{ label: props.sectionLabel, to: props.indexTo }, { label: currentGroup.value.heading }]
  if (props.detail) trail.push({ label: currentLink.value.label, to: currentLink.value.to })
  return trail
})

// Below 1240 pixels the trail is its last crumb on one line; its full text is the title and the accessible name.
const visibleCrumbs = computed(() => (props.narrow ? crumbs.value.slice(-1) : crumbs.value))
const fullTrail = computed(() => crumbs.value.map(crumb => crumb.label).join(' / '))
const heading = computed(() => props.title || currentLink.value?.label || props.sectionLabel)
const lineDescription = computed(() => props.description ?? currentLink.value?.description ?? '')
// The back arrow leads to the index. Overview is the index on narrow widths, so it draws none.
const showsBack = computed(() => props.narrow && props.groups.length > 0 && props.current !== undefined && !props.overview)

// The slash between two crumbs. Tailwind reads the class names from this file, so they stay whole.
const SEPARATOR = "after:px-2 after:content-['/']"
</script>

<template>
  <section
    :class="reading ? 'container max-w-[960px] pb-12 pt-7' : 'container mx-auto max-w-none pb-12 pt-7'"
    aria-labelledby="administration-title"
    data-ingot-component="administration-frame"
  >
    <header class="flex flex-col gap-4 pb-6 min-[600px]:flex-row min-[600px]:items-end min-[600px]:justify-between">
      <div class="flex min-w-0 flex-col gap-1">
        <div
          v-if="showsBack || crumbs.length > 0"
          class="flex min-w-0 items-center gap-2"
        >
          <RouterLink
            v-if="showsBack"
            v-slot="{ href, navigate }"
            :to="indexTo"
            custom
          >
            <a
              :href="href"
              :aria-label="`Back to ${sectionLabel}`"
              class="md-state-layer md-focus-ring relative inline-flex size-12 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant hover:text-on-surface"
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
            v-if="crumbs.length > 0"
            :aria-label="narrow ? fullTrail : 'Breadcrumb'"
            :title="narrow ? fullTrail : undefined"
            class="min-w-0"
          >
            <ol
              class="flex min-w-0 items-center md-label-large text-on-surface-variant"
              :class="narrow ? 'whitespace-nowrap' : 'flex-wrap'"
            >
              <li
                v-for="(crumb, index) in visibleCrumbs"
                :key="index"
                class="min-w-0"
                :class="index < visibleCrumbs.length - 1 ? SEPARATOR : ''"
              >
                <RouterLink
                  v-if="crumb.to"
                  v-slot="{ href, navigate }"
                  :to="crumb.to"
                  custom
                >
                  <a
                    :href="href"
                    class="md-focus-ring rounded-corner-small hover:text-on-surface"
                    @click="navigate"
                  >{{ crumb.label }}</a>
                </RouterLink>
                <span
                  v-else
                  class="block truncate"
                >{{ crumb.label }}</span>
              </li>
            </ol>
          </nav>
        </div>
        <h1
          id="administration-title"
          class="md-headline-medium text-on-surface"
        >
          {{ heading }}
        </h1>
        <p
          v-if="lineDescription"
          class="md-body-large text-on-surface-variant"
        >
          {{ lineDescription }}
        </p>
      </div>
      <div
        v-if="$slots.actions || shortcuts.length > 0"
        class="flex shrink-0 flex-wrap items-center gap-2"
      >
        <nav
          v-if="shortcuts.length > 0"
          aria-label="Related pages"
          class="flex flex-wrap items-center gap-2"
        >
          <RouterLink
            v-for="shortcut in shortcuts"
            :key="shortcut.label"
            v-slot="{ href, navigate }"
            :to="shortcut.to"
            custom
          >
            <a
              :href="href"
              class="md-state-layer md-focus-ring md-label-large relative inline-flex min-h-10 items-center rounded-corner-full px-3 text-primary no-underline"
              @click="navigate"
            >
              {{ shortcut.label }}
            </a>
          </RouterLink>
        </nav>
        <slot name="actions" />
      </div>
    </header>

    <SideNavLayout
      :label="label"
      :groups="groups"
      :current="current"
      :narrow="narrow && groups.length > 0"
      :replace="replace"
      :unsaved="unsaved"
      :overview="overview"
    >
      <slot />
    </SideNavLayout>
  </section>
</template>
