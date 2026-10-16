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
import { ref } from 'vue'
import StorageUsagePanel from './StorageUsagePanel.vue'
import CleanupPoliciesPanel from './CleanupPoliciesPanel.vue'

// A finished run changes the storage figures, so the storage panel is asked to reload.
const storageRevision = ref(0)
</script>

<template>
  <div class="container mx-auto flex flex-col gap-6 pb-12 pt-7">
    <p class="md-body-large text-on-surface-variant">
      Storage usage and cleanup for this Ingot instance. Only managers can see this page.
    </p>
    <StorageUsagePanel :revision="storageRevision" />
    <CleanupPoliciesPanel @ran="storageRevision += 1" />
  </div>
</template>
