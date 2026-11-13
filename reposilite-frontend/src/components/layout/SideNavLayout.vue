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
import { computed, nextTick, ref, watch } from 'vue'
import SideNavTree from './SideNavTree.vue'
import { useTreeFits } from '../../helpers/useTreeFits'

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
 *   groups       [{ heading, links: [{ key, label, description, to }] }], where `to` is a router location
 *   current      the key of the open section, or undefined when no section is open
 *   narrow       true below 1240 pixels; from 1240 pixels the navigation is a column beside the page
 *   replace      true when a link replaces the history entry instead of adding one
 *   overview     true on Overview: below 1240 pixels the lead is drawn above the index, and the page beside it
 *
 * From 1240 pixels the column is sticky only while it fits between the top bar and the save bar; otherwise it scrolls
 * with the page and has no scroll box of its own (see useTreeFits).
 *
 * The page content is the default slot. The optional `lead` slot is drawn first in the page, or above the index on
 * Overview below 1240 pixels, so the page header and Needs attention come before the index there.
 */
const props = defineProps({
  label: { type: String, required: true },
  groups: { type: Array, required: true },
  current: { type: String, default: undefined },
  narrow: { type: Boolean, default: false },
  replace: { type: Boolean, default: false },
  // The keys of the entries whose domain has an unsaved draft (see unsavedTreeKeys in settingsHub.js).
  unsaved: { type: Array, default: () => [] },
  // True on the Overview page: below 1240 pixels it draws the page beside the index, and the index marks Overview.
  overview: { type: Boolean, default: false }
})

// On narrow widths the index replaces the page while no section is open. Overview is the page and the index at once:
// its page sits beside the index, so no back arrow leads anywhere.
const showsIndex = computed(() => props.narrow && (props.current === undefined || props.overview))
const showsPage = computed(() => !(props.narrow && props.current === undefined))
const showsSideNav = computed(() => props.groups.length > 0 && (!props.narrow || showsIndex.value))
// The lead is drawn above the index only on Overview below 1240 pixels; everywhere else it is the first thing of the page.
const leadAbove = computed(() => props.narrow && props.overview)

const navTree = ref(null)
const { fits, measure } = useTreeFits(() => navTree.value?.$el)
// A save bar that appears or goes away changes the room between the bars, so the decision is taken again.
watch(() => props.unsaved.length, () => nextTick(measure))

const navClasses = computed(() => (props.narrow
  ? 'w-full'
  : ['w-52 shrink-0', fits.value ? 'sticky top-[calc(4rem+1rem)]' : '']))
</script>

<template>
  <div
    class="flex min-w-0 flex-col gap-6 min-[1240px]:flex-row min-[1240px]:items-start min-[1240px]:gap-10"
    data-ingot-component="side-nav-layout"
  >
    <div
      v-if="leadAbove && $slots.lead"
      data-ingot-part="lead"
      class="flex min-w-0 flex-col gap-4"
    >
      <slot name="lead" />
    </div>
    <SideNavTree
      v-if="showsSideNav"
      ref="navTree"
      data-ingot-part="nav"
      :class="navClasses"
      :label="label"
      :groups="groups"
      :current="current"
      :replace="replace"
      :unsaved="unsaved"
      :described="narrow"
    />

    <div
      v-if="showsPage"
      data-ingot-part="page"
      class="flex min-w-0 flex-1 flex-col gap-4"
    >
      <div
        v-if="!leadAbove && $slots.lead"
        data-ingot-part="lead"
        class="flex min-w-0 flex-col gap-4"
      >
        <slot name="lead" />
      </div>
      <slot />
    </div>
  </div>
</template>
