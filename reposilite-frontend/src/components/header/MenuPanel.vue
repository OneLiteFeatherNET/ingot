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
import { useSession } from '../../store/session'
import MdButton from '../md/MdButton.vue'
import MdIconButton from '../md/MdIconButton.vue'
import LoginModal from './LoginModal.vue'
import MoonIcon from '../icons/MoonIcon.vue'
import SunIcon from '../icons/SunIcon.vue'
import ContrastIcon from '../icons/ContrastIcon.vue'
import LogoutIcon from '../icons/LogoutIcon.vue'
import useTheme from "../../store/theme"

const { theme, changeTheme } = useTheme()
const { token, isLogged, logout } = useSession()

const NEXT_MODE = { light: 'dark', dark: 'auto', auto: 'light' }

const toggleTheme = () => changeTheme(NEXT_MODE[theme.mode])

// The control cycles through three modes and used to say which one it was in only by
// swapping an unlabelled glyph, so it never announced itself to a screen reader and never
// told anyone what pressing it would do.
const themeLabel = computed(() => ({
  light: 'Theme: light. Switch to dark.',
  dark: 'Theme: dark. Switch to follow the system.',
  auto: 'Theme: follows the system. Switch to light.'
}[theme.mode]))
</script>

<template>
  <nav
    aria-label="Account"
    class="flex flex-row items-center gap-1"
  >
    <span
      v-if="isLogged"
      class="px-3 md-body-large text-on-surface-variant max-sm:hidden"
    >
      Welcome
      <span class="font-medium text-on-surface">{{ token.name }}</span>
    </span>
    <LoginModal v-if="!isLogged" />
    <MdButton
      v-if="isLogged"
      variant="text"
      class="max-sm:hidden"
      @click="logout()"
    >
      Logout
    </MdButton>
    <!-- On small screens the text action gives way to an icon with the same name. -->
    <MdIconButton
      v-if="isLogged"
      aria-label="Logout"
      class="sm:hidden"
      @click="logout()"
    >
      <LogoutIcon />
    </MdIconButton>
    <MdIconButton
      :aria-label="themeLabel"
      :title="themeLabel"
      @click="toggleTheme()"
    >
      <SunIcon v-if="theme.mode === 'light'" />
      <MoonIcon v-else-if="theme.mode === 'dark'" />
      <ContrastIcon v-else />
    </MdIconButton>
  </nav>
</template>
