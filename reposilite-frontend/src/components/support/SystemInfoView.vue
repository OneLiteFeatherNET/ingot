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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import download from 'downloadjs'
import { useSession } from '../../store/session'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import type { components } from '../../types/api'
import AppTabs from '../tabs/AppTabs.vue'
import SettingsEncryptionAlert from './SettingsEncryptionAlert.vue'
import { filterRows, tabForFragment } from './systemInfoTabs'

// The system information of the instance that answers. Every value on the page is already redacted by the server.
const { client } = useSession()
const route = useRoute()
const router = useRouter()
const root = ref<HTMLElement | null>(null)
const info = ref<components['schemas']['SystemInformation'] | null>(null)
// The system information: loading, ready, off (the opt-out answers 404) or failed.
const state = ref<'loading' | 'ready' | 'off' | 'failed'>('loading')
const failure = ref('')
// The support bundle: loading, ready (the download is offered), off, or failed when the check cannot be made.
const support = ref<'loading' | 'ready' | 'off' | 'failed'>('loading')
const busy = ref(false)
// What the page shows as a whole. The support state replaces the content; a failed system information stays in the Overview.
const phase = computed(() => {
  if (support.value === 'loading' || state.value === 'loading') return 'loading'
  if (support.value === 'off' || state.value === 'off') return 'off'
  if (support.value === 'failed') return 'failed'
  return 'ready'
})

const DEFAULT_FILE_NAME = 'ingot-support-bundle.zip'
const fileNameOf = (disposition?: string) => disposition?.match(/filename="([^"]+)"/)?.[1] ?? DEFAULT_FILE_NAME
// The filter of the Environment and Configuration tabs. It is kept here only, so a reload starts without one.
const filter = ref('')

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'plugins', label: 'Plugins' },
  { key: 'environment', label: 'Environment' },
  { key: 'configuration', label: 'Configuration' },
]

const entriesOf = (object: Record<string, string> | undefined) => Object.entries(object ?? {}).sort(([left], [right]) => left.localeCompare(right))
const environmentRows = computed(() => filterRows(entriesOf(info.value?.environment), filter.value))
const propertyRows = computed(() => filterRows(entriesOf(info.value?.systemProperties), filter.value))
const localRows = computed(() => filterRows(entriesOf(info.value?.configuration.local), filter.value))
const noMatch = (rows: unknown[]) => rows.length === 0 && filter.value.trim() !== ''

// The tier arrives in lower case. A plugin without a known tier is Community, the default of the server.
const TIER_LABELS: Record<string, string> = { core: 'Core', official: 'Official', community: 'Community', experimental: 'Experimental' }
const tierLabel = (tier: string | undefined) => TIER_LABELS[tier ?? 'community'] ?? 'Community'

// A section anchor opens the tab that holds its section, writes it into the address, and scrolls the section into view.
const openFragment = async () => {
  const tab = tabForFragment(route.hash)
  if (!tab) return
  if (route.query.tab !== tab) {
    await router.replace({ path: route.path, query: { ...route.query, tab }, hash: route.hash })
  }
  await nextTick()
  root.value?.querySelector(`[id="${route.hash.slice(1)}"]`)?.scrollIntoView?.({ block: 'start' })
}

const load = async () => {
  try {
    const { data } = await client.value.support.systemInfo()
    info.value = data
    state.value = 'ready'
  } catch (error) {
    if ((error as { response?: { status?: number } } | undefined)?.response?.status === 404) {
      state.value = 'off'
    } else {
      state.value = 'failed'
      failure.value = (error as Error | undefined)?.message ?? 'The system information cannot be read.'
    }
  }
}

const loadSupport = async () => {
  try {
    const { data } = await client.value.support.availability()
    support.value = data?.enabled === true ? 'ready' : 'off'
  } catch (error) {
    support.value = (error as { response?: { status?: number } } | undefined)?.response?.status === 404 ? 'off' : 'failed'
  }
}

const downloadBundle = async () => {
  busy.value = true
  try {
    const response = await client.value.support.bundle()
    download(response.data, fileNameOf(response.headers?.['content-disposition']), 'application/zip')
  } catch (error) {
    createErrorToast(errorMessage(error))
  } finally {
    busy.value = false
  }
}

// The sections open once both answers are in, and again when the fragment changes while the page is shown.
watch(phase, (value) => {
  if (value === 'ready') void openFragment()
})
watch(
  () => route.hash,
  () => {
    if (phase.value === 'ready') void openFragment()
  },
)

onMounted(() => {
  void load()
  void loadSupport()
})
</script>

<template>
  <div
    ref="root"
    class="flex flex-col gap-6"
    data-ingot-component="system-info-view"
    data-ingot-component-alias="support-view"
  >
    <SettingsEncryptionAlert />

    <p
      v-if="phase === 'loading'"
      data-ingot-state="loading"
      class="md-body-medium text-on-surface-variant"
    >
      Reading the system information.
    </p>
    <p
      v-else-if="phase === 'off'"
      data-ingot-state="empty"
      class="md-body-medium text-on-surface-variant"
    >
      The support pages are turned off on this instance.
    </p>
    <p
      v-else-if="phase === 'failed'"
      data-ingot-state="error"
      class="md-body-medium text-error"
    >
      The support bundle cannot be checked right now.
    </p>

    <template v-else>
      <header class="flex flex-col gap-4">
        <p class="md-body-large text-on-surface-variant">
          A zip with what a maintainer needs to look at this instance: the system information, the local and shared
          configuration, the cluster members, the plugins and the recent log lines of this instance only.
        </p>
        <div class="flex flex-wrap items-start justify-between gap-4">
          <details
            data-ingot-part="support-note"
            class="md-body-medium max-w-prose text-on-surface-variant"
          >
            <summary class="md-label-large cursor-pointer text-on-surface">
              What is removed
            </summary>
            <p class="mt-2">
              Secrets are removed or masked before the bundle is built: passwords, access keys, tokens, bus credentials and the
              database user and password. Values of settings that hold a secret are masked, and every log line is scrubbed of
              known secrets and of credentials in headers and URLs.
            </p>
            <p class="mt-2">
              Free text in a log line can still hold something no rule recognises, such as a customer name in a path.
              Read the zip before you attach it to a public issue.
            </p>
          </details>
          <button
            type="button"
            data-ingot-part="support-action"
            class="md-label-large w-fit rounded-full bg-primary px-6 py-3 text-on-primary disabled:opacity-60"
            :disabled="busy"
            @click="downloadBundle"
          >
            Download support bundle
          </button>
        </div>
      </header>

      <AppTabs
        :tabs="TABS"
        label="System information sections"
      >
        <template #default="{ tab }">
          <p
            v-if="tab === 'overview' && state === 'failed'"
            data-ingot-state="error"
            class="md-body-medium text-error"
          >
            {{ failure }}
          </p>
          <div
            v-else-if="tab === 'overview' && info"
            class="grid grid-cols-1 gap-4 medium:grid-cols-2 large:grid-cols-3"
          >
            <article
              aria-labelledby="support-ingot"
              class="flex flex-col gap-2 rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-ingot"
                class="md-title-medium text-on-surface"
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
            </article>

            <article
              aria-labelledby="support-java"
              class="flex flex-col gap-2 rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-java"
                class="md-title-medium text-on-surface"
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
              <details>
                <summary class="md-label-large cursor-pointer text-on-surface-variant">
                  Input arguments
                </summary>
                <ul class="font-mono text-sm">
                  <li
                    v-for="argument in info.jvm.inputArguments"
                    :key="argument"
                  >
                    {{ argument }}
                  </li>
                </ul>
              </details>
            </article>

            <article
              aria-labelledby="support-os"
              class="flex flex-col gap-2 rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-os"
                class="md-title-medium text-on-surface"
              >
                Operating system
              </h2>
              <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
                <dt>Name</dt><dd>{{ info.operatingSystem.name }}</dd>
                <dt>Version</dt><dd>{{ info.operatingSystem.version }}</dd>
                <dt>Architecture</dt><dd>{{ info.operatingSystem.architecture }}</dd>
                <dt>Container</dt><dd>{{ info.operatingSystem.container ? 'yes' : 'no' }}</dd>
              </dl>
            </article>

            <article
              aria-labelledby="support-database"
              class="flex flex-col gap-2 rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-database"
                class="md-title-medium text-on-surface"
              >
                Database
              </h2>
              <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1">
                <dt>Kind</dt><dd>{{ info.database.kind }}</dd>
                <dt>Endpoint</dt><dd>{{ info.database.endpoint ?? 'none' }}</dd>
              </dl>
            </article>

            <article
              aria-labelledby="support-storage"
              class="flex flex-col gap-2 rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-storage"
                class="md-title-medium text-on-surface"
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
            </article>

            <article
              aria-labelledby="support-cluster"
              class="flex flex-col gap-2 rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-cluster"
                class="md-title-medium text-on-surface"
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
            </article>
          </div>

          <section
            v-else-if="tab === 'plugins' && info"
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
                class="flex flex-wrap items-center gap-2"
              >
                {{ plugin.name }} {{ plugin.version }}
                <span
                  class="md-label-medium rounded-corner-small border border-outline px-2 text-on-surface-variant"
                  data-ingot-part="plugin-tier"
                >{{ tierLabel(plugin.tier) }}</span>
              </li>
            </ul>
          </section>

          <div
            v-else-if="tab === 'environment' && info"
            class="flex flex-col gap-6"
          >
            <label class="md-body-medium flex flex-col gap-1 text-on-surface-variant">
              Filter by key or value
              <input
                v-model="filter"
                type="search"
                data-ingot-part="filter"
                class="rounded-corner-small border border-outline bg-surface px-3 py-2 text-on-surface"
              >
            </label>

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
                  v-for="[name, value] in environmentRows"
                  :key="name"
                >
                  <dt>{{ name }}</dt><dd>{{ value }}</dd>
                </template>
              </dl>
              <p
                v-if="noMatch(environmentRows)"
                class="md-body-medium text-on-surface-variant"
              >
                No entry matches the filter.
              </p>
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
                  v-for="[name, value] in propertyRows"
                  :key="name"
                >
                  <dt>{{ name }}</dt><dd>{{ value }}</dd>
                </template>
              </dl>
              <p
                v-if="noMatch(propertyRows)"
                class="md-body-medium text-on-surface-variant"
              >
                No entry matches the filter.
              </p>
            </section>
          </div>

          <div
            v-else-if="tab === 'configuration' && info"
            class="flex flex-col gap-6"
          >
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
              <label class="md-body-medium flex flex-col gap-1 text-on-surface-variant">
                Filter by key or value
                <input
                  v-model="filter"
                  type="search"
                  data-ingot-part="filter"
                  class="rounded-corner-small border border-outline bg-surface px-3 py-2 text-on-surface"
                >
              </label>
              <h3 class="md-title-small text-on-surface">
                Local
              </h3>
              <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 font-mono text-sm">
                <template
                  v-for="[name, value] in localRows"
                  :key="name"
                >
                  <dt>{{ name }}</dt><dd>{{ value }}</dd>
                </template>
              </dl>
              <p
                v-if="noMatch(localRows)"
                class="md-body-medium text-on-surface-variant"
              >
                No entry matches the filter.
              </p>
              <h3 class="md-title-small text-on-surface">
                Shared
              </h3>
              <p class="md-body-medium text-on-surface-variant">
                The filter does not apply to the shared configuration.
              </p>
              <pre
              tabindex="0"
              class="overflow-x-auto font-mono text-sm"
            >{{ JSON.stringify(info.configuration.shared, null, 2) }}</pre>
            </section>
          </div>
        </template>
      </AppTabs>
    </template>
  </div>
</template>
