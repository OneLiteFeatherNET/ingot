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
import { onMounted, ref } from 'vue'
import { useSession } from '../../store/session'

// The system information of the instance that answers. Every value on the page is already redacted by the server.
const { client } = useSession()
const info = ref(null)
// loading, ready, off (the opt-out answers 404) or failed
const state = ref('loading')
const failure = ref('')

const entriesOf = (object) => Object.entries(object ?? {}).sort(([left], [right]) => left.localeCompare(right))

const load = async () => {
  try {
    const { data } = await client.value.support.systemInfo()
    info.value = data
    state.value = 'ready'
  } catch (error) {
    if (error?.response?.status === 404) {
      state.value = 'off'
    } else {
      state.value = 'failed'
      failure.value = error?.message ?? 'The system information cannot be read.'
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="container mx-auto flex flex-col gap-6 pb-12 pt-7">
    <h1 class="md-headline-small text-on-surface">
      System information
    </h1>
    <p class="md-body-large text-on-surface-variant">
      What this instance runs on. Secrets are withheld or masked, and the page only reads.
    </p>

    <p
      v-if="state === 'loading'"
      class="md-body-medium text-on-surface-variant"
    >
      Reading the system information.
    </p>
    <p
      v-else-if="state === 'off'"
      class="md-body-medium text-on-surface-variant"
    >
      The support pages are turned off on this instance.
    </p>
    <p
      v-else-if="state === 'failed'"
      class="md-body-medium text-error"
    >
      {{ failure }}
    </p>

    <template v-else-if="info">
      <section
        aria-labelledby="support-ingot"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-ingot"
          class="md-title-large text-on-surface"
        >
          Ingot
        </h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
          <dt>Instance</dt><dd>{{ info.instance }}</dd>
          <dt>Version</dt><dd>{{ info.ingot.version }}</dd>
          <dt>Build time</dt><dd>{{ info.ingot.buildTime }}</dd>
          <dt>Commit</dt><dd>{{ info.ingot.commit }}</dd>
          <dt>Generated</dt><dd>{{ info.generatedAt }}</dd>
        </dl>
      </section>

      <section
        aria-labelledby="support-java"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-java"
          class="md-title-large text-on-surface"
        >
          Java
        </h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
          <dt>Vendor</dt><dd>{{ info.jvm.vendor }}</dd>
          <dt>Version</dt><dd>{{ info.jvm.version }}</dd>
          <dt>Uptime</dt><dd>{{ info.jvm.uptimeMillis }} ms</dd>
          <dt>Heap</dt><dd>{{ info.jvm.heapUsedMb.toFixed(1) }} MB used of {{ info.jvm.heapMaxMb }} MB</dd>
          <dt>Processors</dt><dd>{{ info.jvm.processors }}</dd>
          <dt>Threads</dt><dd>{{ info.jvm.threads }}</dd>
        </dl>
        <h3 class="md-title-small text-on-surface">
          Input arguments
        </h3>
        <ul class="font-mono text-sm">
          <li
            v-for="argument in info.jvm.inputArguments"
            :key="argument"
          >
            {{ argument }}
          </li>
        </ul>
      </section>

      <section
        aria-labelledby="support-os"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-os"
          class="md-title-large text-on-surface"
        >
          Operating system
        </h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
          <dt>Name</dt><dd>{{ info.operatingSystem.name }}</dd>
          <dt>Version</dt><dd>{{ info.operatingSystem.version }}</dd>
          <dt>Architecture</dt><dd>{{ info.operatingSystem.architecture }}</dd>
          <dt>Container</dt><dd>{{ info.operatingSystem.container ? 'yes' : 'no' }}</dd>
        </dl>
      </section>

      <section
        aria-labelledby="support-database"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-database"
          class="md-title-large text-on-surface"
        >
          Database
        </h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
          <dt>Kind</dt><dd>{{ info.database.kind }}</dd>
          <dt>Endpoint</dt><dd>{{ info.database.endpoint ?? 'none' }}</dd>
        </dl>
      </section>

      <section
        aria-labelledby="support-storage"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-storage"
          class="md-title-large text-on-surface"
        >
          Storage
        </h2>
        <ul>
          <li
            v-for="storage in info.storage"
            :key="storage.repository"
          >
            {{ storage.repository }}: {{ storage.kind }}
            <template v-if="storage.region">
              ({{ storage.region }})
            </template>
          </li>
        </ul>
      </section>

      <section
        aria-labelledby="support-cluster"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-cluster"
          class="md-title-large text-on-surface"
        >
          Cluster
        </h2>
        <p v-if="!info.cluster">
          No cluster bus is configured. This instance runs alone.
        </p>
        <template v-else>
          <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
            <dt>Bus</dt><dd>{{ info.cluster.bus.state }} ({{ info.cluster.bus.transport }}, {{ info.cluster.bus.url }})</dd>
          </dl>
          <ul>
            <li
              v-for="member in info.cluster.instances"
              :key="member.id"
            >
              {{ member.id }}: {{ member.version }}, {{ member.health }}
            </li>
          </ul>
        </template>
      </section>

      <section
        aria-labelledby="support-plugins"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-plugins"
          class="md-title-large text-on-surface"
        >
          Plugins
        </h2>
        <ul>
          <li
            v-for="plugin in info.plugins"
            :key="plugin.name"
          >
            {{ plugin.name }} {{ plugin.version }}
          </li>
        </ul>
      </section>

      <section
        aria-labelledby="support-environment"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-environment"
          class="md-title-large text-on-surface"
        >
          Environment
        </h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 font-mono text-sm">
          <template
            v-for="[name, value] in entriesOf(info.environment)"
            :key="name"
          >
            <dt>{{ name }}</dt><dd>{{ value }}</dd>
          </template>
        </dl>
      </section>

      <section
        aria-labelledby="support-properties"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-properties"
          class="md-title-large text-on-surface"
        >
          System properties
        </h2>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 font-mono text-sm">
          <template
            v-for="[name, value] in entriesOf(info.systemProperties)"
            :key="name"
          >
            <dt>{{ name }}</dt><dd>{{ value }}</dd>
          </template>
        </dl>
      </section>

      <section
        aria-labelledby="support-configuration"
        class="flex flex-col gap-2"
      >
        <h2
          id="support-configuration"
          class="md-title-large text-on-surface"
        >
          Configuration
        </h2>
        <h3 class="md-title-small text-on-surface">
          Local
        </h3>
        <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 font-mono text-sm">
          <template
            v-for="[name, value] in entriesOf(info.configuration.local)"
            :key="name"
          >
            <dt>{{ name }}</dt><dd>{{ value }}</dd>
          </template>
        </dl>
        <h3 class="md-title-small text-on-surface">
          Shared
        </h3>
        <pre class="overflow-x-auto font-mono text-sm">{{ JSON.stringify(info.configuration.shared, null, 2) }}</pre>
      </section>
    </template>
  </div>
</template>
