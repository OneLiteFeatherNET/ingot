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
import DomainSettingsPanel from '../settings/DomainSettingsPanel.vue'
</script>

<!--
  Analytics is the place for what is collected: the usage statistics settings, and the metrics endpoint. The traffic chart
  is on the overview, where the instance status is. The panel fetches the configuration only while this page is open.
-->
<template>
  <div
    class="flex flex-col gap-8"
    data-ingot-component="analytics-view"
  >
    <DomainSettingsPanel
      domain="statistics"
      heading="Usage statistics"
    />

    <section
      aria-labelledby="metrics-heading"
      data-test="metrics-endpoint"
      class="flex flex-col gap-3"
    >
      <h2
        id="metrics-heading"
        class="md-title-large text-on-surface"
      >
        Metrics endpoint
      </h2>
      <p class="md-body-large text-on-surface-variant">
        The Prometheus plugin ships with Ingot and serves the metrics at <code>/metrics</code>. It is off until the server sets
        <code>INGOT_METRICS_ENABLED=true</code>, and it has no settings on this page. The Monitor status and failures page of the
        documentation lists the metrics and how to scrape them.
      </p>
    </section>
  </div>
</template>
