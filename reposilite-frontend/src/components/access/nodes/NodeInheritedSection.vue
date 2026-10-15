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
import { ref, useId } from 'vue'
import { inheritedSourceLabel, isOverridden } from './nodeList'

/*
 * The nodes a subject gets from teams, read only. The section is open to start with and a button
 * collapses it. A node that the subject also holds directly (same node text) is still listed and
 * is marked as overridden, because the direct node decides first.
 */
defineProps({
  /** [{ node, allowed, source, subject, depth }] */
  inherited: {
    type: Array,
    required: true
  },
  /** The nodes held directly: the draft list of the editor. */
  direct: {
    type: Array,
    default: () => []
  },
  /** One line for each team whose nodes could not be read. */
  warnings: {
    type: Array,
    default: () => []
  }
})

const open = ref(true)
const regionId = `inherited-${useId()}`
</script>

<template>
  <section
    class="flex flex-col gap-2"
    data-test="inherited-section"
  >
    <button
      type="button"
      class="md-state-layer md-focus-ring relative inline-flex min-h-12 w-fit items-center gap-2 rounded-corner-medium px-3 md-title-small text-on-surface"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="regionId"
      data-test="inherited-toggle"
      @click="open = !open"
    >
      <svg
        class="size-6 shrink-0 transition-transform duration-(--md-sys-motion-duration-short3)"
        :class="open ? '' : '-rotate-90'"
        viewBox="0 -960 960 960"
        aria-hidden="true"
        focusable="false"
      >
        <path
          fill="currentColor"
          d="M480-345 240-585l56-56 184 184 184-184 56 56-240 240Z"
        />
      </svg>
      Inherited from teams ({{ inherited.length }})
    </button>
    <div
      v-show="open"
      :id="regionId"
      class="flex flex-col gap-2"
    >
      <p class="md-body-medium text-on-surface-variant">
        These nodes come from teams and cannot be changed here. A direct node that matches the same permission decides first.
      </p>
      <p
        v-for="warning in warnings"
        :key="warning"
        role="alert"
        class="md-body-medium text-warning"
        data-test="inherited-warning"
      >
        {{ warning }}
      </p>
      <ul
        v-if="inherited.length > 0"
        class="flex flex-col divide-y divide-outline-variant rounded-corner-medium border border-outline-variant"
      >
        <li
          v-for="entry in inherited"
          :key="`${entry.source}:${entry.subject}:${entry.node}`"
          class="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2"
          data-test="inherited-row"
          :data-node="entry.node"
        >
          <div class="min-w-0 flex-1 basis-56">
            <code class="break-all font-mono md-body-medium text-on-surface">{{ entry.node }}</code>
            <p
              class="md-body-small text-on-surface-variant"
              data-test="inherited-source"
            >
              {{ inheritedSourceLabel(entry) }}
            </p>
          </div>
          <span
            v-if="isOverridden(entry, direct)"
            class="rounded-corner-small bg-surface-container-highest px-2 py-1 md-label-small text-on-surface-variant"
            data-test="inherited-overridden"
          >Overridden by a direct node</span>
          <span
            class="md-label-large"
            :class="entry.allowed ? 'text-on-surface' : 'text-error'"
            data-test="inherited-state"
          >{{ entry.allowed ? 'Allowed' : 'Denied' }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>
