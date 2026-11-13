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
import { adminGroups, pageTreeKey, sideNavOf } from '../../navigation/registry'
import AdministrationFrame from '../layout/AdministrationFrame.vue'
import { useSettingsDomains } from '../settings/useSettingsDomains'
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
const props = defineProps({
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

// Below 1240px the side navigation is an index instead of a column beside the page; see SideNavLayout.
const narrow = useMediaQuery('(max-width: 1239px)')

// A page whose node the user lacks is left out, not greyed out. The pages, their groups and the node each one
// needs come from the navigation registry, the same node the route guard checks. Every Administration page shows this tree,
// with the settings domains the visitor may open.
const settings = useSettingsDomains()
const groups = computed(() => adminGroups(can, { domains: settings.domains.value, titleOf: settings.titleOf }))
const sideNav = sideNavOf('access')

// The heading of the page: the item name on a detail page, the Access name on the index, and otherwise the label of the tab.
const heading = computed(() => props.name ?? (props.tab === undefined ? 'Access' : ''))
// A detail page links to the permission check. A user or a machine account goes there with the principal filled in; a
// team has no principal of the check, so the link opens the check alone.
const shortcuts = computed(() => {
  if (props.name === undefined) return []
  const to = props.tab === 'teams' ? { name: 'access-check' } : { name: 'access-check', query: { principal: props.name } }
  return [{ label: 'Check a permission', to }]
})
</script>

<template>
  <AdministrationFrame
    :title="heading"
    :detail="name !== undefined"
    :label="sideNav.label"
    :section-label="sideNav.sectionLabel"
    :groups="groups"
    :current="pageTreeKey(tab)"
    :index-to="sideNav.indexTo"
    :shortcuts="shortcuts"
    :narrow="narrow"
  >
    <div data-ingot-component="access-view">
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
  </AdministrationFrame>
</template>
