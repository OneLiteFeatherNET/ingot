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
import ResolvedRequestsChart from './ResolvedRequestsChart.vue'
</script>

<!--
  The analytics tab shows usage over time, where the dashboard shows the running instance.
  The chart polls itself and is only mounted while this tab is open.
-->
<template>
  <div>
    <section aria-labelledby="traffic-heading">
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
