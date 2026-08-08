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
import { useSession } from '../../store/session'
import MenuButton from './MenuButton.vue'
import LoginModal from './LoginModal.vue'
import MoonIcon from '../icons/MoonIcon.vue'
import SunIcon from '../icons/SunIcon.vue'
import LogoutIcon from '../icons/LogoutIcon.vue'
import useTheme from "../../store/theme"

const { theme, changeTheme } = useTheme()
const { token, isLogged, logout } = useSession()

const toggleTheme = () => {
  switch (theme.mode) {
    case 'light':
      changeTheme('dark')
      break
    case 'dark':
      changeTheme('auto')
      break
    case 'auto':
      changeTheme('light')
      break
  }
}
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
    <div class="flex justify-center items-center rounded-full w-[40px] h-[35px] default-button" @click="toggleTheme()">
      <SunIcon v-if="theme.mode === 'light'"/>
      <MoonIcon class="pl-0.5" v-if="theme.mode === 'dark'"/>
      <div class="font-bold w-full text-center text-lg" v-if="theme.mode === 'auto'">
        A
      </div>
    </div>
  </nav>
</template>
