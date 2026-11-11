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
import SideNavLayout from './SideNavLayout.vue'

/*
 * The frame of every Administration page: one heading named Administration, the description of the page, optional
 * actions of the page on the right of the heading, and the Administration tree beside the page content. The page
 * keeps its own content and description; the heading and the tree are the same on every page.
 *
 * Props:
 *   description  the one-line description of the page under the heading
 *   label        the accessible name of the side navigation
 *   sectionLabel the first crumb, and the name of the back arrow
 *   groups       the Administration tree: [{ heading, links: [{ key, label, to }] }]; empty draws no side navigation
 *   current      the key of the entry on screen, or undefined
 *   indexTo      the router location of the index
 *   narrow       true below 1240 pixels, where the tree is an index (see SideNavLayout)
 *   replace      true when a link replaces the history entry
 *
 * Slots: `actions` (buttons of the page, beside the heading) and the default slot (the page content).
 */
defineProps({
  description: { type: String, default: '' },
  label: { type: String, required: true },
  sectionLabel: { type: String, required: true },
  groups: { type: Array, required: true },
  current: { type: String, default: undefined },
  indexTo: { type: Object, required: true },
  narrow: { type: Boolean, default: false },
  replace: { type: Boolean, default: false }
})
</script>

<template>
  <section
    class="container mx-auto max-w-[1280px] pb-12 pt-7 min-[1920px]:max-w-[1600px]"
    aria-labelledby="administration-title"
  >
    <header class="flex flex-col gap-4 pb-6 min-[600px]:flex-row min-[600px]:items-end min-[600px]:justify-between">
      <div class="flex min-w-0 flex-col gap-1">
        <h1
          id="administration-title"
          class="md-headline-medium text-on-surface"
        >
          Administration
        </h1>
        <p class="md-body-large text-on-surface-variant">
          {{ description }}
        </p>
      </div>
      <div
        v-if="$slots.actions"
        class="flex shrink-0 flex-wrap items-center gap-2"
      >
        <slot name="actions" />
      </div>
    </header>

    <SideNavLayout
      :label="label"
      :section-label="sectionLabel"
      :groups="groups"
      :current="current"
      :index-to="indexTo"
      :narrow="narrow && groups.length > 0"
      :replace="replace"
    >
      <slot />
    </SideNavLayout>
  </section>
</template>
