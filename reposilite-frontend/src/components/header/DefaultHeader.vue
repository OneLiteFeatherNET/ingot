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
import { useWindowScroll } from '@vueuse/core'
import usePlaceholders from '../../store/placeholders'
import MenuPanel from './MenuPanel.vue'

const { title } = usePlaceholders()

// The top app bar is a flat surface at the top of the page. Once the page scrolls under it, it
// takes the surface container colour, so it separates from the content it now overlaps. The
// colour change is the only effect: MD3 small top app bars do not add a shadow on scroll.
const { y } = useWindowScroll()
const scrolled = computed(() => y.value > 0)
</script>

<template>
  <header
    class="sticky top-0 z-30 text-on-surface transition-colors duration-(--md-sys-motion-duration-short4) ease-md-standard motion-reduce:transition-none"
    :class="scrolled ? 'bg-surface-container' : 'bg-surface'"
  >
    <!-- The top app bar: the title on the left, the account and theme actions on the right. -->
    <div class="container mx-auto flex min-h-16 flex-row items-center justify-between gap-4 py-2 max-sm:px-5">
      <h1 class="md-title-large min-w-0 truncate">
        <router-link
          :to="'/'"
          class="md-focus-ring rounded-corner-small text-on-surface no-underline"
        >
          {{ title }}
        </router-link>
      </h1>
      <MenuPanel />
    </div>
    <!--
      The avatar, description and organisation link used to sit here, which put them above
      the console, the settings form and every other view an operator works in: 356px of
      chrome before the first line of content, on a screen that is often 900px tall. They
      describe the repository, so they belong to the page that presents the repository, and
      the overview tab renders HeaderHero itself.
    -->
  </header>
</template>