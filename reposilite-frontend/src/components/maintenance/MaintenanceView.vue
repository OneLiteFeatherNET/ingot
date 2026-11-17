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
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import StorageUsagePanel from './StorageUsagePanel.vue'
import CleanupPoliciesPanel from './CleanupPoliciesPanel.vue'
import IndexPanel from './IndexPanel.vue'
import ReadOnlyToggle from '../dashboard/ReadOnlyToggle.vue'
import AppTabs from '../tabs/AppTabs.vue'
import SettingsBackupPanel from './SettingsBackupPanel.vue'
import { useSession } from '../../store/session'
import { ADMIN_NODES } from '../../store/nodeAccess'

// The sections of the page, one tab each, in this order. The tab in the address opens one; an unknown or missing one opens the first.
const TABS = [
  { key: 'storage', label: 'Storage usage' },
  { key: 'index', label: 'Search index' },
  { key: 'cleanup', label: 'Cleanup policies' },
  // The settings file actions. Only a principal who may edit the settings sees this tab, as only they saw the buttons on Overview.
  { key: 'backup', label: 'Settings backup', node: ADMIN_NODES.settings },
]

const session = useSession()
const can = (node: string): boolean => (typeof session.can === 'function' ? session.can(node) : true)
// Computed, because the permissions arrive after a direct visit has drawn the page: the backup tab must appear then.
const shownTabs = computed(() => TABS.filter(tab => tab.node === undefined || can(tab.node)))

const route = useRoute()
const selected = computed(() => {
  const key = route.query.tab
  return typeof key === 'string' && shownTabs.value.some(tab => tab.key === key) ? key : shownTabs.value[0].key
})

// A panel loads its data when its tab is first shown. It stays mounted after that, so switching back shows it as it was.
const opened = reactive(new Set<string>())
watch(selected, key => opened.add(key), { immediate: true })

// A finished run changes the storage figures, so the storage panel is asked to reload.
const storageRevision = ref(0)
</script>

<template>
  <div
    class="flex flex-col gap-6"
    data-ingot-component="maintenance-view"
  >
    <ReadOnlyToggle />
    <AppTabs
      :tabs="shownTabs"
      label="Maintenance sections"
    >
      <template #default="{ tab }">
        <template v-if="opened.has(tab)">
          <StorageUsagePanel
            v-if="tab === 'storage'"
            :revision="storageRevision"
          />
          <IndexPanel v-else-if="tab === 'index'" />
          <CleanupPoliciesPanel
            v-else-if="tab === 'cleanup'"
            @ran="storageRevision += 1"
          />
          <SettingsBackupPanel v-else-if="tab === 'backup'" />
        </template>
      </template>
    </AppTabs>
  </div>
</template>
