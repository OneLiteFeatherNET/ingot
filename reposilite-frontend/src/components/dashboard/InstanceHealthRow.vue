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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { STATUS } from '../../store/palette'
import { useSession } from '../../store/session'
import { describeHealth, findIntegrations, toAnswer } from './instanceHealth'
import StatusLabel from './StatusLabel.vue'

// Health moves as often as the status tiles it sits under, so it is polled on the same beat.
const POLL_INTERVAL_MS = 5000

const { client } = useSession()
const health = ref(null)
const integrations = ref([])
let timer = null

const indicator = computed(() => health.value ?? {
  state: 'pending',
  label: 'Checking health',
  details: []
})

const DOT_COLORS = {
  up: STATUS.good,
  down: STATUS.critical,
  unknown: 'var(--md-sys-color-outline)',
  pending: 'var(--md-sys-color-outline)'
}

const refreshHealth = () =>
  toAnswer(client.value.status.health())
    .then(answer => {
      health.value = describeHealth(answer)
    })

// Probed once per visit: the integrations do not come and go while the page is open, and a
// probe is a request to a plugin that may not even exist.
const probeIntegrations = () =>
  findIntegrations(
    (endpoint) => client.value.status.probe(endpoint),
    (endpoint) => client.value.status.url(endpoint)
  ).then(found => {
    integrations.value = found
  })

const onVisibilityChange = () => {
  if (!document.hidden) refreshHealth()
}

onMounted(() => {
  refreshHealth()
  probeIntegrations()
  timer = setInterval(() => {
    if (!document.hidden) refreshHealth()
  }, POLL_INTERVAL_MS)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="flex flex-wrap items-start justify-between gap-x-6 gap-y-2 px-1">
    <div
      role="status"
      class="flex flex-wrap items-center gap-x-2 md-body-medium text-on-surface"
    >
      <StatusLabel
        :color="DOT_COLORS[indicator.state]"
        class="md-label-large"
      >
        {{ indicator.label }}
      </StatusLabel>
      <span
        v-for="line in indicator.details"
        :key="line"
        class="block w-full pl-3.5 md-body-small text-on-surface-variant"
      >
        {{ line }}
      </span>
    </div>

    <nav
      v-if="integrations.length"
      aria-label="Integrations"
      class="flex flex-wrap items-center gap-x-4 gap-y-1"
    >
      <span class="md-label-large text-on-surface-variant">Integrations</span>
      <a
        v-for="integration in integrations"
        :key="integration.label"
        :href="integration.href"
        target="_blank"
        rel="noopener"
        class="md-label-large text-primary underline-offset-4 hover:underline md-focus-ring rounded-corner-extra-small"
      >
        {{ integration.label }}
      </a>
    </nav>
  </div>
</template>
