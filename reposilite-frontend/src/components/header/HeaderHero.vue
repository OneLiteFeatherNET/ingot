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
import GlobeIcon from '../icons/GlobeIcon.vue'
import usePlaceholders from '../../store/placeholders'
import useTheme from '../../store/theme'
import { useAppearance } from '../../store/appearance'
import { logoFor } from '../../theme/settings'

const { description, organizationWebsite, organizationLogo } = usePlaceholders()
const { theme } = useTheme()
const appearance = useAppearance()
// The operator's dark logo, when one is set, replaces the logo in the dark theme only.
const logo = computed(() => logoFor(appearance, organizationLogo, theme.isDark))
</script>

<template>
  <section class="border-b border-outline-variant bg-surface-container-low text-on-surface">
    <div
      class="olf-brand-bar"
      aria-hidden="true"
    />
    <!--
      The organisation's own introduction. It sits on a raised surface so that it reads as a
      separate band from the file list below, and it stacks into one centred column on small
      screens instead of leaving the logo alone on its own line.
    -->
    <div class="container mx-auto flex flex-col items-center gap-6 py-10 md:flex-row md:items-center md:gap-10 max-sm:py-6">
      <img
        class="size-32 shrink-0 rounded-corner-full border-2 border-outline-variant md:size-40"
        alt=""
        :src="logo"
      >
      <div class="flex min-w-0 flex-col items-center gap-3 text-center md:items-start md:text-left">
        <div
          class="md-title-large text-on-surface"
          v-html="description"
        />
        <a
          v-if="organizationWebsite"
          class="md-focus-ring md-label-large inline-flex min-h-12 items-center gap-2 rounded-corner-small text-primary no-underline hover:underline"
          :href="organizationWebsite"
          target="_blank"
          rel="noopener"
        >
          <GlobeIcon
            class="size-5"
            aria-hidden="true"
          />
          {{ organizationWebsite }}
        </a>
      </div>
    </div>
  </section>
</template>
