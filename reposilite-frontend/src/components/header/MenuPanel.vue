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
import { useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import MdIconButton from '../md/MdIconButton.vue'
import MdOverflowMenu from '../md/MdOverflowMenu.vue'
import LoginModal from './LoginModal.vue'
import MoonIcon from '../icons/MoonIcon.vue'
import SunIcon from '../icons/SunIcon.vue'
import ContrastIcon from '../icons/ContrastIcon.vue'
import useTheme from "../../store/theme"

/*
 * The account side of the top bar. A signed in visitor sees one button, the avatar and the name, and
 * its menu holds the name, the account page, the theme choice and logout. A visitor who is not signed
 * in sees the Sign in button and the theme icon, since the theme is theirs to choose too.
 */
const router = useRouter()
const { theme, changeTheme } = useTheme()
const { token, isLogged, logout, principal } = useSession()

// A cookie session has no token name; the principal's display name stands in for it.
const signedInName = computed(() => principal?.value?.displayName || principal?.value?.name || token.value.name)
const initial = computed(() => (signedInName.value || '?').charAt(0).toUpperCase())

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

// The account menu: the name, the account page, the three themes (the one in use checked) and logout.
const accountItems = computed(() => [
  { value: 'account', label: 'My account', href: router.resolve({ name: 'account' }).href },
  { type: 'separator' },
  { value: 'theme:light', label: 'Light', type: 'radio', checked: theme.mode === 'light' },
  { value: 'theme:dark', label: 'Dark', type: 'radio', checked: theme.mode === 'dark' },
  { value: 'theme:auto', label: 'Follow system', type: 'radio', checked: theme.mode === 'auto' },
  { type: 'separator' },
  { value: 'logout', label: 'Logout' }
])

const onAccountSelect = (value) => {
  if (value === 'account') {
    router.push({ name: 'account' })
  } else if (value === 'logout') {
    logout()
  } else if (value.startsWith('theme:')) {
    changeTheme(value.slice('theme:'.length))
  }
}
</script>

<template>
  <nav
    aria-label="Account"
    class="flex min-w-0 flex-row items-center gap-2"
  >
    <MdOverflowMenu
      v-if="isLogged"
      :label="`Account menu for ${signedInName}`"
      :items="accountItems"
      trigger-class="min-w-0 max-w-full gap-2 !px-2"
      @select="onAccountSelect"
    >
      <template #trigger>
        <span
          class="flex size-8 shrink-0 items-center justify-center rounded-corner-full bg-primary-container md-title-small text-on-primary-container"
          aria-hidden="true"
        >{{ initial }}</span>
        <!-- Below 900px the name leaves the layout but stays the button's accessible name. -->
        <span
          data-test="account-name"
          class="sr-only min-w-0 truncate md-label-large text-on-surface min-[900px]:not-sr-only"
        >{{ signedInName }}</span>
      </template>
      <template #header>
        <p
          data-test="menu-heading"
          class="truncate md-title-small text-on-surface"
        >
          {{ signedInName }}
        </p>
      </template>
    </MdOverflowMenu>
    <template v-else>
      <LoginModal />
      <MdIconButton
        :aria-label="themeLabel"
        :title="themeLabel"
        @click="toggleTheme()"
      >
        <SunIcon v-if="theme.mode === 'light'" />
        <MoonIcon v-else-if="theme.mode === 'dark'" />
        <ContrastIcon v-else />
      </MdIconButton>
    </template>
  </nav>
</template>
