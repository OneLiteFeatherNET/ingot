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

<script setup>
import InstanceStatusPanel from './InstanceStatusPanel.vue'
import ResourceTrends from './ResourceTrends.vue'
import ResolvedRequestsChart from './ResolvedRequestsChart.vue'
</script>

<!--
  Read top to bottom, the page answers three questions in the order an operator asks them:
  is anything wrong right now, how have the resources been behaving, and what is the traffic
  doing.
  The panels fetch and poll themselves and are only ever mounted while this tab is open, so
  a slow endpoint holds up its own card instead of the whole page, and the polling stops by
  unmounting rather than by checking which tab is selected.
-->
<template>
  <div class="container mx-auto pt-7 pb-12">
    <section aria-labelledby="instance-status-heading">
      <h2
        id="instance-status-heading"
        class="pb-3 md-title-large text-on-surface"
      >
        Instance status
      </h2>
      <InstanceStatusPanel />
    </section>

    <section
      class="pt-6"
      aria-labelledby="resources-heading"
    >
      <h2
        id="resources-heading"
        class="pb-3 md-title-large text-on-surface"
      >
        Resources
      </h2>
      <ResourceTrends />
    </section>

    <section
      class="pt-6"
      aria-labelledby="traffic-heading"
    >
      <h2
        id="traffic-heading"
        class="pb-3 md-title-large text-on-surface"
      >
        Traffic
      </h2>
      <ResolvedRequestsChart />
    </section>
  </div>
</template>
