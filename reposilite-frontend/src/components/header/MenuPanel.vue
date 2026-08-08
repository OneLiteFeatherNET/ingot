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
import MenuButton from './MenuButton.vue'
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
  <nav class="flex flex-row max-sm:max-w-[100px] max-sm:flex-wrap max-sm:flex-1 max-sm:justify-end max-sm:min-w-1/2">
    <div v-if="isLogged" class="pt-[0.275rem] px-2 max-sm:hidden">
      Welcome 
      <span class="font-bold underline">{{ token.name }}</span>
    </div>
    <LoginModal>
      <template v-slot:button>
        <MenuButton v-if="!isLogged">
          Sign in
        </MenuButton>
      </template>
    </LoginModal>
    <MenuButton v-if="isLogged" @click="logout()" class="max-sm:hidden">
      Logout
    </MenuButton>
    <div
      v-if="isLogged"
      class="hidden px-[0.675rem] pt-[0.2rem] mr-1.5 cursor-pointer rounded-full bg-white dark:bg-gray-900 max-h-[35px] max-sm:block max-sm:pt-1.5"
    >
      <LogoutIcon @click="logout()"/>
    </div>
    <button
      type="button"
      class="default-button flex h-[35px] w-[40px] items-center justify-center rounded-full"
      :aria-label="themeLabel"
      :title="themeLabel"
      @click="toggleTheme()"
    >
      <SunIcon v-if="theme.mode === 'light'" />
      <MoonIcon v-else-if="theme.mode === 'dark'" class="pl-0.5" />
      <ContrastIcon v-else />
    </button>
  </nav>
</template>
