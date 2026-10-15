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
import { useRouter } from 'vue-router'
import { useSession } from '../../store/session'
import { ACCESS_NODES, ADMIN_NODES, INSTANCE_ADMIN_NODE } from '../../store/nodeAccess'
import SettingsTabs from '../settings/SettingsTabs.vue'
import PrincipalsPanel from './PrincipalsPanel.vue'
import TeamsPanel from './TeamsPanel.vue'
import SsoProvidersPanel from './SsoProvidersPanel.vue'
import LdapPanel from './LdapPanel.vue'
import CheckPermissionView from './check/CheckPermissionView.vue'

/*
 * The Access section for instance admins: users, machine accounts and teams, as tabs. The tab
 * and the open principal come from the address, and a tab change goes through the router.
 *
 * Props:
 *   tab   users, machines, teams, sso, ldap or check (the permission check)
 *   name  the open user, machine account or team, if any
 */
defineProps({
  tab: {
    type: String,
    default: 'users',
    validator: (value) => ['users', 'machines', 'teams', 'sso', 'ldap', 'check'].includes(value)
  },
  name: {
    type: String,
    default: undefined
  }
})

const router = useRouter()

const { can } = useSession()

// A tab whose node the user lacks is left out, not greyed out. The check is open to anyone who
// may use one of the three lists. The provider pages save the instance settings and test the
// provider connection, which the server answers only to the full instance admin.
const ALL_TABS = [
  { key: 'users', label: 'Users', nodes: [ADMIN_NODES.users] },
  { key: 'machines', label: 'Machine accounts', nodes: [ADMIN_NODES.tokens] },
  { key: 'teams', label: 'Teams', nodes: [ADMIN_NODES.teams] },
  { key: 'sso', label: 'SSO', nodes: [INSTANCE_ADMIN_NODE] },
  { key: 'ldap', label: 'LDAP', nodes: [INSTANCE_ADMIN_NODE] },
  { key: 'check', label: 'Check', nodes: ACCESS_NODES }
]
]
const TABS = computed(() => ALL_TABS.filter(tab => tab.nodes.some(node => can(node))))

const select = (key) => router.push({ name: `access-${key}` })
</script>

<template>
  <section
    class="container mx-auto pb-12 pt-7"
    aria-labelledby="access-title"
  >
    <div class="flex flex-col gap-1 pb-4">
      <h2
        id="access-title"
        class="md-headline-small text-on-surface"
      >
        Access
      </h2>
      <p class="md-body-large text-on-surface-variant">
        Who can sign in to this instance and what they can reach.
      </p>
    </div>

    <SettingsTabs
      :model-value="tab"
      label="Access sections"
      :tabs="TABS"
      @update:model-value="select"
    />

    <div class="pt-4">
      <PrincipalsPanel
        v-if="tab === 'users'"
        kind="USER"
        :name="name"
      />
      <PrincipalsPanel
        v-else-if="tab === 'machines'"
        kind="MACHINE"
        :name="name"
      />
      <SsoProvidersPanel
        v-else-if="tab === 'sso'"
      />
      <LdapPanel
        v-else-if="tab === 'ldap'"
      />
      <TeamsPanel
        v-else-if="tab === 'teams'"
        :name="name"
      />
      <CheckPermissionView v-else />
    </div>
  </section>
</template>
