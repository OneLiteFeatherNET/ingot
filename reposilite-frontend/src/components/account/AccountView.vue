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
import { computed, onMounted, ref } from 'vue'
import { useSession } from '../../store/session'
import TokenList from '../identity/TokenList.vue'
import PasswordCard from './PasswordCard.vue'
import SecondFactorsCard from './SecondFactorsCard.vue'
import type { SecondFactorsApi } from './secondFactorsApi'
import ProfileCard from './ProfileCard.vue'
import YourAccess from './YourAccess.vue'
import { accessView, signInSources, type OwnAccessSummary } from './accessView'
import MdPage from '../md/MdPage.vue'
import MdPageHeader from '../md/MdPageHeader.vue'

/*
 * "My account": four sections, each with its heading. Profile shows each value once. Sign-in and security holds the
 * password card and, in the named slot for two-step sign-in, the second factors card. Tokens are the signed in user's
 * own. Your access is read only and shows the caller's own access summary; the page asks for no principal, so none can
 * come from the address.
 * When the server requires a new password (mustChangePassword), a banner says so and the sign-in and security section
 * moves to the top; the router keeps the other pages closed until it is done.
 */
const { principal, authMode, mustChangePassword, client } = useSession()

const identity = computed(() => client.value.identity.me)
// The card reads the factors as typed by its own API; the generated client types the answer with optional fields.
const secondFactors = computed(() => identity.value as SecondFactorsApi)
const passwordAvailable = computed(() => authMode.value === 'cookie')
const blocks = computed<string[]>(() => (mustChangePassword.value ? ['security', 'overview'] : ['overview', 'security']))

const passwordChanged = () => {
  mustChangePassword.value = false
}

// The access summary is asked for once, when the page opens. Until it arrives the section says so; the rest of the page does not wait.
const summary = ref<OwnAccessSummary | 'loading' | 'unavailable'>('loading')
const loadAccess = async () => {
  try {
    summary.value = (await identity.value.access()).data
  } catch {
    summary.value = 'unavailable'
  }
}
onMounted(loadAccess)

const access = computed(() => accessView(summary.value))
const answer = computed<OwnAccessSummary | undefined>(() => (typeof summary.value === 'string' ? undefined : summary.value))
const sources = computed<string[]>(() => (answer.value
  ? signInSources(answer.value.principal?.providers ?? [], principal.value?.kind ?? '')
  : []))
</script>

<template>
  <div data-ingot-component="account-view">
    <MdPage class="pb-12 pt-7">
      <MdPageHeader
        class="pb-7"
        title="My account"
        description="Your profile, your password, the tokens you created and what your account may do."
      />

      <p
        v-if="mustChangePassword"
        role="alert"
        data-test="must-change"
        class="mb-4 rounded-corner-large bg-tertiary-container px-4 py-3 md-title-small text-on-tertiary-container"
      >
        Set a new password before you continue
      </p>

      <div class="grid grid-cols-1 items-start gap-4 expanded:grid-cols-2 large:grid-cols-3">
        <template
          v-for="block in blocks"
          :key="block"
        >
          <div
            v-if="block === 'overview'"
            class="flex min-w-0 flex-col gap-4 large:contents"
          >
            <ProfileCard
              v-if="principal"
              class="min-w-0"
              :principal="principal"
              :sources="sources"
            />
            <YourAccess
              v-if="principal"
              class="min-w-0"
              :access="access"
            />
          </div>
          <section
            v-else
            class="flex min-w-0 max-w-md-reading flex-col gap-4"
            :class="mustChangePassword ? 'col-span-full' : ''"
            aria-labelledby="security-title"
            data-test="security-section"
          >
            <h3
              id="security-title"
              class="md-title-large text-on-surface"
            >
              Sign-in and security
            </h3>
            <PasswordCard
              :api="identity"
              :available="passwordAvailable"
              :focus="mustChangePassword"
              @changed="passwordChanged"
            />
            <div
              data-test="sign-in-slot"
              data-ingot-slot="add-multi-factor-authentication"
              class="min-w-0"
            >
              <SecondFactorsCard
                :api="secondFactors"
                :account-name="principal?.name ?? ''"
              />
            </div>
          </section>
        </template>
        <div class="col-span-full min-w-0">
          <TokenList :api="identity.tokens" />
        </div>
      </div>
    </MdPage>
  </div>
</template>
