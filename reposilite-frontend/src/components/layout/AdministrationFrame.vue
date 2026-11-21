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
import { computed, ref, type PropType } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import SideNavLayout from './SideNavLayout.vue'
import SideNavTree from './SideNavTree.vue'
import AdministrationTrail from './AdministrationTrail.vue'
import MdSideSheet from '../md/MdSideSheet.vue'
import MdPage from '../md/MdPage.vue'
import MdPageHeader from '../md/MdPageHeader.vue'
import type { MdBreadcrumbItem } from '../md/MdBreadcrumb.vue'

interface NavLink {
  key: string | undefined
  label: string
  description?: string
  to: RouteLocationRaw
}

interface NavGroup {
  id?: string
  heading?: string
  label?: string
  links: NavLink[]
}

interface Shortcut {
  label: string
  to: RouteLocationRaw
}

/*
 * The frame of every Administration page. The page is an MdPage: the header draws, in this order: the trail (Administration,
 * the group of the page as plain text and, on a detail page, the group as a link to the list page and the page as text),
 * the one level one heading that names the page, the description of the page, and the page actions with the shortcuts of
 * the page on the right. Above the header, below 1240 pixels, the back arrow stands alone in its row.
 * Beside it, below the header, the Administration tree and the page content. The heading is the label of the current tree
 * entry unless a title is given (a detail page names its item, a plugin page its manifest title). The description is the
 * one of the current entry unless one is given. From 1240 pixels the header sits in the page column, beside the side
 * navigation, which starts at the same top edge (a grid of the page); below 1240 pixels the header stays above the side
 * navigation.
 *
 * Props:
 *   title        the heading, when it is not the label of the current entry
 *   description  the one-line description under the heading; by default the description of the current entry
 *   detail       true on a detail page: the trail then adds the list page of the current entry as a link, the back arrow
 *                leads to that list, and the list description is not drawn (only the description given)
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
 * Slots: `actions` (buttons of the page, beside the heading), `lead` (content of the page drawn before the page content, or
 * above the index on Overview below 1240 pixels; see SideNavLayout) and the default slot (the page content).
 */
const props = defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: undefined },
  detail: { type: Boolean, default: false },
  label: { type: String, required: true },
  sectionLabel: { type: String, required: true },
  groups: { type: Array as PropType<NavGroup[]>, required: true },
  current: { type: String, default: undefined },
  indexTo: { type: Object as PropType<RouteLocationRaw>, required: true },
  shortcuts: { type: Array as PropType<Shortcut[]>, default: () => [] },
  narrow: { type: Boolean, default: false },
  replace: { type: Boolean, default: false },
  reading: { type: Boolean, default: false },
  unsaved: { type: Array as PropType<string[]>, default: () => [] },
  overview: { type: Boolean, default: false }
})

const currentLink = computed(() => props.groups
  .flatMap(group => group.links)
  .find(link => link.key === props.current))

const currentGroup = computed(() => props.groups
  .find(group => group.links.some(link => link.key === props.current)))

// The trail: the section, the group of the current entry and, on a detail page, the current entry as text. On a detail
// page the group is a link to the list. Overview has no group and draws the section alone. A page whose entry has no
// group heading draws the section as its current crumb. Empty when no entry is on screen, as on the settings hub. While
// the tree does not hold the entry on screen yet (the settings domains are still loading), the section is the crumb too,
// so the header does not grow by the trail row when the tree arrives.
const crumbs = computed<MdBreadcrumbItem[]>(() => {
  if (props.overview) return [{ label: props.sectionLabel }]
  const link = currentLink.value
  if (!link) return props.current === undefined ? [] : [{ label: props.sectionLabel }]
  const heading = currentGroup.value?.heading
  if (heading === undefined) return [{ label: props.sectionLabel }]
  const trail: MdBreadcrumbItem[] = [
    { label: props.sectionLabel, to: props.indexTo },
    { label: heading, to: props.detail ? link.to : undefined }
  ]
  if (props.detail) trail.push({ label: link.label })
  return trail
})

const fullTrail = computed(() => crumbs.value.map(crumb => crumb.label).join(' / '))
// Below 1240 pixels the trail is its last crumb on one line: the back arrow beside it is the one link, and the full trail
// is the title of that crumb.
const visibleCrumbs = computed<MdBreadcrumbItem[]>(() => (props.narrow
  ? crumbs.value.slice(-1).map(crumb => ({ label: crumb.label, title: fullTrail.value }))
  : crumbs.value))
const heading = computed(() => props.title || currentLink.value?.label || props.sectionLabel)
// The list description belongs to the list; a detail page shows the description of its item only, when one is given.
const lineDescription = computed(() => props.description ?? (props.detail ? '' : currentLink.value?.description ?? ''))
// The back arrow leads to the list a detail page belongs to, and to the index everywhere else.
const back = computed(() => (props.detail && currentLink.value
  ? { label: currentLink.value.label, to: currentLink.value.to }
  : { label: props.sectionLabel, to: props.indexTo }))
// The back arrow is drawn below 1240 pixels on a page with an entry. Overview is the index on narrow widths, so it draws none.
// From 1240 pixels a detail page reaches its list by the group link of the trail, so the header never has two ways back.
const showsBack = computed(() => props.narrow && props.groups.length > 0 && props.current !== undefined && !props.overview)
// The Sections button sits beside the back arrow below 1240 pixels; it is drawn from 600 pixels (see the classes below)
// and opens the same tree in a modal side sheet, which closes on the choice of an entry.
const showsSections = computed(() => props.narrow && showsBack.value)
const sheetOpen = ref(false)

</script>

<template>
  <section
    aria-labelledby="administration-title"
    data-ingot-component="administration-frame"
  >
    <MdPage
      spaced
      class="large:grid large:grid-cols-(--grid-md-side-nav) large:grid-rows-(--grid-md-administration-rows) large:gap-x-10"
    >
      <div class="flex flex-col gap-4 pb-6 large:col-start-2 large:row-start-1">
        <!-- The back arrow is alone in its row. Its 48 pixel target is pulled 12 pixels into the gutter, so the glyph starts on the title edge. -->
        <div
          v-if="showsBack"
          class="flex"
        >
          <RouterLink
            v-slot="{ href, navigate }"
            :to="back.to"
            custom
          >
            <a
              :href="href"
              :aria-label="`Back to ${back.label}`"
              class="-ms-3 md-state-layer md-focus-ring relative inline-flex size-12 shrink-0 items-center justify-center rounded-corner-full text-on-surface-variant hover:text-on-surface"
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
        </div>
        <!-- A no-break space keeps the description line when a page has none, so the heading does not move between pages. -->
        <MdPageHeader
          heading-id="administration-title"
          :title="heading"
          :description="lineDescription || '\u00a0'"
        >
          <template
            v-if="visibleCrumbs.length > 0"
            #trail
          >
            <AdministrationTrail :items="visibleCrumbs" />
          </template>
          <template
            v-if="showsSections || shortcuts.length > 0 || $slots.actions"
            #actions
          >
            <button
              v-if="showsSections"
              type="button"
              aria-haspopup="dialog"
              class="md-state-layer md-focus-ring relative hidden min-h-12 items-center gap-2 rounded-corner-full px-4 md-label-large text-primary medium:inline-flex"
              @click="sheetOpen = true"
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
                  d="M4 6h16M4 12h16M4 18h16"
                />
              </svg>
              Sections
            </button>
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
          </template>
        </MdPageHeader>
      </div>

      <!-- The reading measure caps the layout (side navigation and page content), never the header: the header spans the
           container on every page, and a reading page only keeps its content column at 960 pixels. -->
      <div :class="reading ? 'max-w-md-reading large:contents' : 'min-w-0 large:contents'">
        <SideNavLayout
          :label="label"
          :groups="groups"
          :current="current"
          :narrow="narrow && groups.length > 0"
          :replace="replace"
          :unsaved="unsaved"
          :overview="overview"
          :reading="reading"
        >
          <template
            v-if="$slots.lead"
            #lead
          >
            <slot name="lead" />
          </template>
          <slot />
        </SideNavLayout>
      </div>

      <MdSideSheet
        v-if="showsSections"
        :open="sheetOpen"
        title="Administration pages"
        @update:open="sheetOpen = $event"
      >
        <SideNavTree
          :label="label"
          :groups="groups"
          :current="current"
          :unsaved="unsaved"
          :replace="replace"
          described
          @select="sheetOpen = false"
        />
      </MdSideSheet>
    </MdPage>
  </section>
</template>
