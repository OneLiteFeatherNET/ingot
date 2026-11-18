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

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import MdIconButton from '../md/MdIconButton.vue'
import MdOverflowMenu from '../md/MdOverflowMenu.vue'
import type { MenuItem } from '../md/types'
import LoginModal from './LoginModal.vue'
import AccountIdentity from './AccountIdentity.vue'
import MoonIcon from '../icons/MoonIcon.vue'
import SunIcon from '../icons/SunIcon.vue'
import ContrastIcon from '../icons/ContrastIcon.vue'
import useTheme from "../../store/theme"
import { kindLabel, signInSources, type OwnAccessSummary } from '../account/accessView'

/*
 * The account side of the top bar. A signed in visitor sees one button, the avatar and the name, and
 * its menu holds the identity line, the account page, the theme choice and logout. The name is drawn
 * once, in the button; the menu says who and by which sign-in, never the name again. A visitor who is
 * not signed in sees the Sign in button and the theme icon, since the theme is theirs to choose too.
 */
const router = useRouter()
const { theme, changeTheme } = useTheme()
const { token, isLogged, logout, principal, client } = useSession()

// A cookie session has no token name; the principal's display name stands in for it.
const signedInName = computed<string>(() => principal?.value?.displayName || principal?.value?.name || token.value.name)
const initial = computed<string>(() => (signedInName.value || '?').charAt(0).toUpperCase())

// The own access summary gives the sign-in sources of the principal. Until it has arrived, or when the
// server does not have the endpoint, the menu names the kind alone rather than a source it does not know.
const access = ref<OwnAccessSummary | undefined>(undefined)
const loadAccess = async (): Promise<void> => {
  access.value = undefined
  if (!isLogged.value) return
  try {
    access.value = (await client.value.identity.me.access()).data
  } catch {
    access.value = undefined
  }
}
watch([isLogged, principal], loadAccess, { immediate: true })

// The username is shown only when it adds something to the name shown in the bar, which falls back to the login name.
const username = computed<string>(() => {
  const who = principal?.value
  return who?.name && who.name !== signedInName.value ? who.name : ''
})
const identityLine = computed<string>(() => {
  const kind = principal?.value?.kind ?? ''
  const sources = access.value ? signInSources(access.value.principal?.providers ?? [], kind) : []
  return [kindLabel(kind), ...sources].filter(Boolean).join(', ')
})

// The theme modes are plain strings in the theme store, so the lookups are keyed by string.
const NEXT_MODE: Record<string, string> = { light: 'dark', dark: 'auto', auto: 'light' }

const toggleTheme = () => changeTheme(NEXT_MODE[theme.mode])

// The control cycles through three modes and used to say which one it was in only by
// swapping an unlabelled glyph, so it never announced itself to a screen reader and never
// told anyone what pressing it would do.
const THEME_LABELS: Record<string, string> = {
  light: 'Theme: light. Switch to dark.',
  dark: 'Theme: dark. Switch to follow the system.',
  auto: 'Theme: follows the system. Switch to light.'
}
const themeLabel = computed<string>(() => THEME_LABELS[theme.mode])

// The account menu: the name, the account page, the three themes (the one in use checked) and logout.
const accountItems = computed<MenuItem[]>(() => [
  { value: 'account', label: 'My account', href: router.resolve({ name: 'account' }).href },
  { type: 'separator' },
  { value: 'theme:light', label: 'Light', type: 'radio', checked: theme.mode === 'light' },
  { value: 'theme:dark', label: 'Dark', type: 'radio', checked: theme.mode === 'dark' },
  { value: 'theme:auto', label: 'Follow system', type: 'radio', checked: theme.mode === 'auto' },
  { type: 'separator' },
  { value: 'logout', label: 'Logout' }
])

const onAccountSelect = (value: string): void => {
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
    data-ingot-component="menu-panel"
  >
    <MdOverflowMenu
      v-if="isLogged"
      :label="`Account menu for ${signedInName}`"
      :trigger-label="`Account menu, ${signedInName}`"
      :items="accountItems"
      trigger-class="min-w-0 max-w-full gap-2 !px-2"
      @select="onAccountSelect"
    >
      <template #trigger>
        <span
          class="flex size-8 shrink-0 items-center justify-center rounded-corner-full bg-primary-container md-title-small text-on-primary-container"
          aria-hidden="true"
        >{{ initial }}</span>
        <!-- The trigger label names the button as the account menu of the name. Below 900px the name leaves the layout; the full name is the title. -->
        <span
          data-test="account-name"
          :title="signedInName"
          class="sr-only min-w-0 truncate md-label-large text-on-surface min-[900px]:not-sr-only"
        >{{ signedInName }}</span>
      </template>
      <template #header>
        <AccountIdentity
          :username="username"
          :kind="identityLine"
        />
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
