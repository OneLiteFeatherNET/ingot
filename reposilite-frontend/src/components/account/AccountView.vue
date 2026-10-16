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
import { useSession } from '../../store/session'
import TokenList from '../identity/TokenList.vue'
import PasswordCard from './PasswordCard.vue'
import ProfileCard from './ProfileCard.vue'

/*
 * "My account": the signed in principal's profile, password and own tokens. It is open to every
 * signed in principal, not only to instance admins. When the server requires a new password
 * (mustChangePassword), a banner says so and the password card moves to the top; the router
 * keeps the other pages closed until it is done.
 */
const { principal, details, instanceAdmin, authMode, mustChangePassword, client } = useSession()

const identity = computed(() => client.value.identity.me)
const teams = computed(() => details.value?.teams ?? [])
const passwordAvailable = computed(() => authMode.value === 'cookie')

const passwordChanged = () => {
  mustChangePassword.value = false
}
</script>

<template>
  <section
    class="container mx-auto pb-12 pt-7"
    aria-labelledby="account-title"
  >
    <div class="flex flex-col gap-1 pb-7">
      <h2
        id="account-title"
        class="md-headline-small text-on-surface"
      >
        My account
      </h2>
      <p class="md-body-large text-on-surface-variant">
        Your profile, your password and the tokens you created.
      </p>
    </div>

    <p
      v-if="mustChangePassword"
      role="alert"
      data-test="must-change"
      class="mb-4 rounded-corner-large bg-tertiary-container px-4 py-3 md-title-small text-on-tertiary-container"
    >
      Set a new password before you continue
    </p>

    <div
      class="grid grid-cols-1 items-start gap-4 min-[960px]:grid-cols-2"
      :class="mustChangePassword ? '[&>*:first-child]:min-[960px]:col-span-2' : ''"
    >
      <PasswordCard
        v-if="mustChangePassword"
        :api="identity"
        :available="passwordAvailable"
        focus
        @changed="passwordChanged"
      />
      <ProfileCard
        v-if="principal"
        :principal="principal"
        :teams="teams"
        :instance-admin="instanceAdmin"
      />
      <PasswordCard
        v-if="!mustChangePassword"
        :api="identity"
        :available="passwordAvailable"
        @changed="passwordChanged"
      />
      <div class="min-w-0 min-[960px]:col-span-2">
        <TokenList :api="identity.tokens" />
      </div>
    </div>
  </section>
</template>
