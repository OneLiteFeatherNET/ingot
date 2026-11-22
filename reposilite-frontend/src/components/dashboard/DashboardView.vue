<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup lang="ts">
import { ref } from 'vue'
import type { components } from '../../types/api'
import InstanceStatusPanel from './InstanceStatusPanel.vue'
import ResourceTrends from './ResourceTrends.vue'
import ResolvedRequestsChart from './ResolvedRequestsChart.vue'
import GettingStartedCard from './GettingStartedCard.vue'
import MdSection from '../md/MdSection.vue'

// The status panel reports the cluster it finds in its answer; the trends below draw from it.
const cluster = ref<components['schemas']['ClusterStatusResponse'] | null>(null)
</script>

<!--
  Read top to bottom, the page answers three questions in the order an operator asks them:
  is anything wrong right now, how have the resources been behaving, and how much traffic
  has the instance served. The analytics tab keeps the same traffic chart for now.
  The panels fetch and poll themselves and are only ever mounted while this tab is open, so
  a slow endpoint holds up its own card instead of the whole page, and the polling stops by
  unmounting rather than by checking which tab is selected.
-->
<template>
  <div
    class="min-w-0 max-w-none"
    data-ingot-component="dashboard-view"
  >
    <GettingStartedCard class="mb-6" />

    <MdSection
      title="Instance status"
      heading-id="instance-status-heading"
      aria-labelledby="instance-status-heading"
    >
      <InstanceStatusPanel @cluster="cluster = $event" />
    </MdSection>

    <MdSection
      title="Resources"
      heading-id="resources-heading"
      aria-labelledby="resources-heading"
      class="pt-6"
    >
      <ResourceTrends :cluster="cluster" />
    </MdSection>

    <MdSection
      title="Traffic"
      heading-id="traffic-heading"
      aria-labelledby="traffic-heading"
      class="pt-6"
    >
      <ResolvedRequestsChart />
    </MdSection>
  </div>
</template>
