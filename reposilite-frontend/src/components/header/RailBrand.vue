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
import usePlaceholders from '../../store/placeholders'
import useTheme from '../../store/theme'
import { useAppearance } from '../../store/appearance'
import { logoFor } from '../../theme/settings'

/*
 * The corner of the navigation rail, above its entries and beside the top bar. It shows the logo the
 * instance draws for the theme in use, and links to the start page. The logo has no text, so the link
 * is named after the site.
 */
const { title, organizationLogo } = usePlaceholders()
const { theme } = useTheme()
const appearance = useAppearance()
const logo = computed(() => logoFor(appearance, organizationLogo, theme.isDark))
</script>

<template>
  <!--
    A custom link: the router would mark the corner as the current page on the start page, and the
    Browse entry under it already is that page.
  -->
  <router-link
    v-slot="{ href, navigate }"
    :to="'/'"
    custom
  >
    <a
      :href="href"
      data-ingot-component="rail-brand"
      class="md-focus-ring flex size-12 items-center justify-center rounded-corner-full no-underline"
      :aria-label="`${title}, start page`"
      @click="navigate"
    >
      <img
        class="size-10 rounded-corner-full"
        alt=""
        :src="logo"
      >
    </a>
  </router-link>
</template>
