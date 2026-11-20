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
import { computed, inject, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import download from 'downloadjs'
import { useSession } from '../../store/session'
import { ADMIN_NODES } from '../../store/nodeAccess'
import { configurationStoreKey, useConfiguration } from '../../store/configuration'
import { createErrorToast, errorMessage } from '../../helpers/toast'
import type { components } from '../../types/api'
import AppTabs from '../tabs/AppTabs.vue'
import MdButton from '../md/MdButton.vue'
import MdSection from '../md/MdSection.vue'
import MdTextField from '../md/MdTextField.vue'
import FactoryResetModal from '../settings/FactoryResetModal.vue'
import SettingsEncryptionAlert from './SettingsEncryptionAlert.vue'
import KeyValueTree from './KeyValueTree.vue'
import { flattenNested } from './keyTree'
import { tabForFragment } from './systemInfoTabs'
import { formatUptime } from './uptime'

// The system information of the instance that answers. Every value on the page is already redacted by the server.
const session = useSession()
const { client } = session
// The factory reset of the settings, and the node that may edit them: the same check the settings pages make.
const store = inject(configurationStoreKey, null) ?? useConfiguration().store
const canEdit = computed(() => (typeof session.can === 'function' ? session.can(ADMIN_NODES.settings) : true))
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
const environmentEntries = computed(() => entriesOf(info.value?.environment))
const propertyEntries = computed(() => entriesOf(info.value?.systemProperties))
const localEntries = computed(() => entriesOf(info.value?.configuration.local))
// The shared configuration is nested JSON per domain. It is flattened to dotted keys, so it reads like the local one.
const sharedEntries = computed(() => flattenNested(info.value?.configuration.shared ?? {}))

// The tier arrives in lower case. A plugin without a known tier is Community, the default of the server.
const TIER_LABELS: Record<string, string> = { core: 'Core', official: 'Official', community: 'Community', experimental: 'Experimental' }
const tierLabel = (tier: string | undefined) => TIER_LABELS[tier ?? 'community'] ?? 'Community'

// The cards list Core first, then Official, Community and Experimental, and by name within each group. The filter appears above 12 plugins.
const TIER_ORDER: Record<string, number> = { core: 0, official: 1, community: 2, experimental: 3 }
const tierRank = (tier: string | undefined) => TIER_ORDER[tier ?? 'community'] ?? TIER_ORDER.community
const PLUGIN_FILTER_ABOVE = 12
// Where a plugin was loaded from, in words. The server sends the kind of place, never a path.
const ORIGIN_LABELS: Record<string, string> = {
  'built-in': 'Built into the server',
  'bundled-image': 'Bundled with the image',
  'plugins-directory': 'Plugins directory',
  'plugin-store': 'Plugin store',
}
const originLabel = (origin: string | undefined) => (origin && ORIGIN_LABELS[origin]) || 'Unknown'
// The filter of the plugin cards. It is its own, so the filter of the other tabs does not narrow the cards.
const pluginFilter = ref('')
const orderedPlugins = computed(() =>
  [...(info.value?.plugins ?? [])].sort((left, right) => tierRank(left.tier) - tierRank(right.tier) || left.name.localeCompare(right.name)),
)
const showPluginFilter = computed(() => orderedPlugins.value.length > PLUGIN_FILTER_ABOVE)
const shownPlugins = computed(() => {
  const text = pluginFilter.value.trim().toLowerCase()
  return text === '' ? orderedPlugins.value : orderedPlugins.value.filter(plugin => plugin.name.toLowerCase().includes(text))
})

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

// The reset writes the settings of every domain. The page may be opened before the settings are read, so they are read first.
// A failed read stops the reset here; the store reports the failure itself.
const factoryReset = async () => {
  if (store.domains.value.length === 0 && !(await store.fetchConfiguration())) return
  await store.factoryReset()
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
      <header class="flex flex-col items-start gap-4">
        <p class="md-body-large max-w-prose text-on-surface-variant">
          A zip with what a maintainer needs to look at this instance: the system information, the local and shared
          configuration, the cluster members, the plugins and the recent log lines of this instance only.
        </p>
        <details
          data-ingot-part="support-note"
          class="md-body-medium max-w-prose text-on-surface-variant"
        >
          <summary class="md-label-large w-fit cursor-pointer text-on-surface">
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
            class="grid grid-cols-1 gap-4 medium:grid-cols-2 expanded:grid-cols-3"
          >
            <article
              aria-labelledby="support-ingot"
              class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-ingot"
                class="md-title-medium text-on-surface"
              >
                Ingot
              </h2>
              <dl class="grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1">
                <dt>Instance</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.instance }}
                </dd>
                <dt>Version</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.ingot.version }}
                </dd>
                <dt>Build time</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.ingot.buildTime }}
                </dd>
                <dt>Commit</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.ingot.commit }}
                </dd>
                <dt>Generated</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.generatedAt }}
                </dd>
              </dl>
            </article>

            <article
              aria-labelledby="support-java"
              class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-java"
                class="md-title-medium text-on-surface"
              >
                Java
              </h2>
              <dl class="grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1">
                <dt>Vendor</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.jvm.vendor }}
                </dd>
                <dt>Version</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.jvm.version }}
                </dd>
                <dt>Uptime</dt><dd class="min-w-0 wrap-anywhere">
                  {{ formatUptime(info.jvm.uptimeMillis) }}
                </dd>
                <dt>Heap</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.jvm.heapUsedMb.toFixed(1) }} MB used of {{ info.jvm.heapMaxMb }} MB
                </dd>
                <dt>Processors</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.jvm.processors }}
                </dd>
                <dt>Threads</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.jvm.threads }}
                </dd>
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
              class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-os"
                class="md-title-medium text-on-surface"
              >
                Operating system
              </h2>
              <dl class="grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1">
                <dt>Name</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.operatingSystem.name }}
                </dd>
                <dt>Version</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.operatingSystem.version }}
                </dd>
                <dt>Architecture</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.operatingSystem.architecture }}
                </dd>
                <dt>Container</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.operatingSystem.container ? 'yes' : 'no' }}
                </dd>
              </dl>
            </article>

            <article
              aria-labelledby="support-database"
              class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
            >
              <h2
                id="support-database"
                class="md-title-medium text-on-surface"
              >
                Database
              </h2>
              <dl class="grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1">
                <dt>Kind</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.database.kind }}
                </dd>
                <dt>Endpoint</dt><dd class="min-w-0 wrap-anywhere">
                  {{ info.database.endpoint ?? 'none' }}
                </dd>
              </dl>
            </article>

            <article
              aria-labelledby="support-storage"
              class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
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
              class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
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
                <dl class="grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1">
                  <dt>Bus</dt><dd class="min-w-0 wrap-anywhere">
                    {{ info.cluster.bus.state }} ({{ info.cluster.bus.transport }}, {{ info.cluster.bus.url }})
                  </dd>
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

          <MdSection
            v-else-if="tab === 'plugins' && info"
            title="Plugins"
            heading-id="support-plugins"
            aria-labelledby="support-plugins"
          >
            <MdTextField
              v-if="showPluginFilter"
              v-model="pluginFilter"
              type="search"
              label="Filter plugins"
            />
            <p
              v-if="orderedPlugins.length === 0"
              data-ingot-state="empty"
              class="md-body-medium text-on-surface-variant"
            >
              No plugins are loaded.
            </p>
            <p
              v-else-if="shownPlugins.length === 0"
              data-ingot-state="empty"
              class="md-body-medium text-on-surface-variant"
            >
              No plugin matches the filter.
            </p>
            <div
              v-else
              data-ingot-part="plugin-grid"
              class="grid grid-cols-1 gap-4 medium:grid-cols-2 expanded:grid-cols-3"
            >
              <article
                v-for="(plugin, index) in shownPlugins"
                :key="plugin.name"
                :aria-labelledby="`support-plugin-${index}`"
                data-ingot-part="plugin-card"
                class="flex min-w-0 flex-col gap-2 wrap-anywhere rounded-corner-medium bg-surface-container-low p-4"
              >
                <header class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
                  <h3
                    :id="`support-plugin-${index}`"
                    class="md-title-medium min-w-0 wrap-anywhere text-on-surface"
                  >
                    {{ plugin.name }}
                  </h3>
                  <span class="md-body-medium text-on-surface-variant">{{ plugin.version }}</span>
                  <span
                    class="md-label-medium rounded-corner-small border border-outline px-2 text-on-surface-variant"
                    data-ingot-part="plugin-tier"
                  >{{ tierLabel(plugin.tier) }}</span>
                </header>
                <p
                  v-if="plugin.description"
                  class="md-body-medium min-w-0 wrap-anywhere text-on-surface-variant"
                  data-ingot-part="plugin-description"
                >
                  {{ plugin.description }}
                </p>
                <dl class="grid min-w-0 grid-cols-(--grid-md-key-value) gap-x-6 gap-y-1">
                  <dt>Origin</dt><dd class="min-w-0 wrap-anywhere">
                    {{ originLabel(plugin.origin) }}
                  </dd>
                  <dt>Depends on</dt><dd class="min-w-0 wrap-anywhere">
                    {{ plugin.dependencies.length > 0 ? plugin.dependencies.join(', ') : 'None' }}
                  </dd>
                  <dt>Settings</dt><dd class="min-w-0 wrap-anywhere">
                    <RouterLink
                      v-if="plugin.settingsDomain"
                      :to="`/settings/${plugin.settingsDomain}`"
                      class="md-label-large text-primary underline-offset-4 hover:underline"
                    >
                      Open settings
                    </RouterLink>
                    <template v-else>
                      None
                    </template>
                  </dd>
                </dl>
              </article>
            </div>
          </MdSection>

          <div
            v-else-if="tab === 'environment' && info"
            class="flex flex-col gap-6"
          >
            <label class="sticky top-(--spacing-md-top-bar) z-md-sticky md-body-medium flex flex-col gap-1 bg-surface py-2 text-on-surface-variant">
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
              <KeyValueTree
                :entries="environmentEntries"
                split="underscore"
                label="Environment variables"
                title="Environment"
                heading-id="support-environment"
                :query="filter"
              />
            </section>

            <section
              aria-labelledby="support-properties"
              class="flex flex-col gap-2"
            >
              <KeyValueTree
                :entries="propertyEntries"
                split="dot"
                label="System properties"
                title="System properties"
                heading-id="support-properties"
                :query="filter"
              />
            </section>
          </div>

          <div
            v-else-if="tab === 'configuration' && info"
            class="flex flex-col gap-6"
          >
            <label class="sticky top-(--spacing-md-top-bar) z-md-sticky md-body-medium flex flex-col gap-1 bg-surface py-2 text-on-surface-variant">
              Filter by key or value
              <input
                v-model="filter"
                type="search"
                data-ingot-part="filter"
                class="rounded-corner-small border border-outline bg-surface px-3 py-2 text-on-surface"
              >
            </label>
            <MdSection
              title="Configuration"
              heading-id="support-configuration"
              aria-labelledby="support-configuration"
            >
              <KeyValueTree
                :entries="localEntries"
                split="camel"
                label="Local configuration"
                title="Local"
                heading-id="support-local"
                :query="filter"
              />
              <KeyValueTree
                :entries="sharedEntries"
                split="dot"
                label="Shared configuration"
                title="Shared"
                heading-id="support-shared"
                :query="filter"
              />
            </MdSection>
          </div>

          <section
            v-if="tab === 'overview' && canEdit"
            aria-labelledby="system-danger-title"
            class="mt-10 flex flex-col gap-4 rounded-corner-large border border-error p-4 md:flex-row md:items-center md:justify-between md:p-6"
          >
            <div class="flex min-w-0 flex-col gap-1">
              <h2
                id="system-danger-title"
                class="md-title-large text-error"
              >
                Danger zone
              </h2>
              <p class="md-body-medium text-on-surface-variant">
                A factory reset sets every setting back to its default and saves it to all instances at once.
              </p>
            </div>
            <FactoryResetModal :callback="factoryReset">
              <template #button>
                <MdButton
                  variant="outlined"
                  type="button"
                  error
                  class="shrink-0 whitespace-nowrap"
                >
                  Factory reset
                </MdButton>
              </template>
            </FactoryResetModal>
          </section>
        </template>
      </AppTabs>
    </template>
  </div>
</template>
