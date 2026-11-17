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
/*
 * The identity block at the top of the account menu: the username, when it differs from the display name
 * the bar shows, and a line with the kind and the sign-in sources. The display name is never drawn here.
 *
 * Props:
 *   username  the login name, or an empty text when it is the same as the display name
 *   kind      the line such as "User, LDAP"
 */
defineProps<{
  username: string
  kind: string
}>()
</script>

<template>
  <div
    data-ingot-component="account-identity"
    data-test="account-identity"
    class="flex min-w-0 flex-col"
  >
    <p
      v-if="username"
      data-test="account-username"
      :title="username"
      class="truncate md-body-small text-on-surface-variant"
    >
      {{ username }}
    </p>
    <p
      data-test="account-kind"
      class="truncate md-label-small text-on-surface-variant"
    >
      {{ kind }}
    </p>
  </div>
</template>
