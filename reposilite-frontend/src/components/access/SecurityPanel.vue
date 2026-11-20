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

<script setup lang="ts">
import { computed, inject } from 'vue'
import { useRoute } from 'vue-router'
import AuthenticationGroupForm from './AuthenticationGroupForm.vue'
import SignInSwitchNotice from './SignInSwitchNotice.vue'
import AppTabs from '../tabs/AppTabs.vue'
import { mfaWarnings, type MfaSettings } from './loginSettings'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'

// The tabs in order. The tab in the address (?tab=) opens one; an unknown or missing one opens the first.
// Each tab draws part of one settings domain. Its save writes that whole domain, and its button names it.
const TABS = [
  { key: 'ip', label: 'IP addresses' },
  { key: 'accounts', label: 'Accounts' },
  { key: 'passwords', label: 'Passwords' },
  { key: 'sessions', label: 'Sessions' },
  { key: 'second-factors', label: 'Second factors' },
]

const ACCOUNT_FIELDS = ['maxFailedAttempts', 'lockoutMinutes', 'accountThrottle']
const PASSWORD_FIELDS = ['passwordPolicy']
const SESSION_FIELDS = ['idleTimeoutMinutes']
const MFA_FIELDS = ['mfa']

// The notices of the second factor policy follow the draft in the form, so they change before the save.
const { store: sharedStore } = useConfiguration()
const store = inject(configurationStoreKey, null) ?? sharedStore
const breakGlassNotices = computed(() => {
  const mfa = store.configurations.value.login?.mfa as MfaSettings | undefined
  return mfa ? mfaWarnings(mfa) : []
})

// Only the open tab draws its form. AppTabs keeps every panel in the page, so the others would be drawn hidden.
// A draft of a tab stays in the shared store, so switching tabs does not lose it.
const route = useRoute()
const selected = computed(() => {
  const key = route.query.tab
  return typeof key === 'string' && TABS.some(tab => tab.key === key) ? key : TABS[0].key
})
</script>

<template>
  <section
    aria-labelledby="security-title"
    class="flex flex-col gap-6"
    data-test="security"
    data-ingot-component="security-panel"
  >
    <div class="flex flex-col gap-1">
      <h2
        id="security-title"
        class="md-title-medium text-on-surface"
      >
        Security
      </h2>
      <p class="md-body-medium text-on-surface-variant">
        Limits on repeated failed sign-ins, the rules for new passwords, the length of browser sessions and the second factors that signing in requires.
      </p>
    </div>

    <AppTabs
      :tabs="TABS"
      label="Sign-in protection sections"
    >
      <template #default="{ tab }">
        <template v-if="tab === selected">
          <AuthenticationGroupForm
            v-if="tab === 'ip'"
            group="bruteForceProtection"
            save-label="Save IP address limits"
          />
          <template v-else-if="tab === 'accounts'">
            <SignInSwitchNotice />
            <AuthenticationGroupForm
              domain="login"
              :fields="ACCOUNT_FIELDS"
              save-label="Save account limits"
            />
          </template>
          <AuthenticationGroupForm
            v-else-if="tab === 'passwords'"
            domain="login"
            :fields="PASSWORD_FIELDS"
            save-label="Save password rules"
          />
          <AuthenticationGroupForm
            v-else-if="tab === 'sessions'"
            domain="login"
            :fields="SESSION_FIELDS"
            save-label="Save session limits"
          />
          <template v-else-if="tab === 'second-factors'">
            <p
              v-for="notice in breakGlassNotices"
              :key="notice"
              role="note"
              data-test="mfa-break-glass-notice"
              class="md-body-medium text-on-surface-variant"
            >
              {{ notice }}
            </p>
            <AuthenticationGroupForm
              domain="login"
              :fields="MFA_FIELDS"
              save-label="Save second factor policy"
            />
          </template>
        </template>
      </template>
    </AppTabs>
  </section>
</template>
