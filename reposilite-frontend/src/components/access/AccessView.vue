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
import { useMediaQuery } from '@vueuse/core'
import { useSession } from '../../store/session'
import { groupedChildren, sideNavOf } from '../../navigation/registry'
import SideNavLayout from '../layout/SideNavLayout.vue'
import PrincipalsPanel from './PrincipalsPanel.vue'
import TeamsPanel from './TeamsPanel.vue'
import SsoProvidersPanel from './SsoProvidersPanel.vue'
import LdapPanel from './LdapPanel.vue'
import SecurityPanel from './SecurityPanel.vue'
import CheckPermissionView from './check/CheckPermissionView.vue'

/*
 * The Access section for instance admins: users, machine accounts and teams, and the provider pages,
 * in a side navigation. The section and the open principal come from the address, and each section
 * is a real link to its own address.
 *
 * Props:
 *   tab   users, machines, teams, sso, ldap, security or check (the permission check); none shows the index
 *   name  the open user, machine account or team, if any
 */
defineProps({
  tab: {
    type: String,
    default: undefined,
    validator: (value) => ['users', 'machines', 'teams', 'sso', 'ldap', 'security', 'check'].includes(value)
  },
  name: {
    type: String,
    default: undefined
  }
})

const { can } = useSession()

// Below 600px the side navigation is an index instead of a column beside the page; see SideNavLayout.
const narrow = useMediaQuery('(max-width: 599px)')

// A section whose node the user lacks is left out, not greyed out. The sections, their groups and the
// node each one needs come from the navigation registry, the same node the route guard checks.
const groups = computed(() => groupedChildren('access', can))
const sideNav = sideNavOf('access')
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

    <SideNavLayout
      :label="sideNav.label"
      :section-label="sideNav.sectionLabel"
      :groups="groups"
      :current="tab"
      :index-to="sideNav.indexTo"
      :narrow="narrow"
    >
      <div>
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
        <SecurityPanel
          v-else-if="tab === 'security'"
        />
        <TeamsPanel
          v-else-if="tab === 'teams'"
          :name="name"
        />
        <CheckPermissionView v-else-if="tab === 'check'" />
      </div>
    </SideNavLayout>
  </section>
</template>
