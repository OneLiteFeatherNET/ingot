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
import usePlaceholders from '../../store/placeholders'
import MenuPanel from './MenuPanel.vue'

/*
 * The top bar. It starts at the right edge of the navigation rail and reaches the window edge. Its title
 * row is the container the page content uses, so the title and the content share one left edge, and the
 * account actions end where the content ends. `framed` is set on the administration pages, whose content
 * has its own maximum width; the bar takes the same one.
 *
 * The brand rule runs along the bottom edge of the bar, across all of it.
 */
defineProps({
  framed: {
    type: Boolean,
    default: false
  }
})

const { title } = usePlaceholders()
</script>

<template>
  <header
    class="sticky top-0 z-30 bg-surface-container text-on-surface min-[1600px]:pr-4"
    data-ingot-component="default-header"
  >
    <!-- The top app bar: the title on the left, the account actions on the right. -->
    <div
      class="container mx-auto flex min-h-16 flex-row items-center justify-between gap-4 py-2"
      :class="framed ? 'max-w-[1280px] min-[1920px]:max-w-[1600px]' : ''"
    >
      <h1
        data-ingot-part="title"
        class="md-title-large min-w-0 truncate"
      >
        <router-link
          :to="'/'"
          class="md-focus-ring rounded-corner-small text-on-surface no-underline"
        >
          {{ title }}
        </router-link>
      </h1>
      <MenuPanel />
    </div>
    <div
      class="olf-brand-bar absolute inset-x-0 bottom-0"
      aria-hidden="true"
    />
  </header>
</template>
