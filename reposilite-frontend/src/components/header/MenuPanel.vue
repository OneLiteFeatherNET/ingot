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
import AccountIcon from '../icons/AccountIcon.vue'
import useTheme from "../../store/theme"

const { theme, changeTheme } = useTheme()
const { token, isLogged, logout, principal } = useSession()

// A cookie session has no token name; the principal's display name stands in for it.
const signedInName = computed(() => principal?.value?.displayName || principal?.value?.name || token.value.name)

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
  <!--
    min-w-0 lets the group give way to the title, and a long name is cut rather than pushing the
    theme toggle out of the header.
  -->
  <nav
    aria-label="Account"
    class="flex min-w-0 flex-row items-center gap-1"
  >
    <span
      v-if="isLogged"
      class="min-w-0 truncate px-3 md-body-large text-on-surface-variant max-sm:hidden"
    >
      Welcome
      <span class="font-medium text-on-surface">{{ signedInName }}</span>
    </span>
    <LoginModal v-if="!isLogged" />
    <!-- A real link to the account page, so it opens in a new tab as well. -->
    <router-link
      v-if="isLogged"
      v-slot="{ href, navigate }"
      :to="{ name: 'account' }"
      custom
    >
      <MdButton
        variant="text"
        class="max-sm:hidden"
        :href="href"
        @click="navigate"
      >
        My account
      </MdButton>
      <MdIconButton
        aria-label="My account"
        class="sm:hidden"
        :href="href"
        @click="navigate"
      >
        <AccountIcon />
      </MdIconButton>
    </router-link>
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
