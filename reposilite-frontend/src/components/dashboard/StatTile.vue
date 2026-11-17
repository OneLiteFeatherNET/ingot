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
import { computed } from 'vue'
import type { PropType } from 'vue'
import { RouterLink } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import { STATUS } from '../../store/palette'
import LinkIcon from '../icons/LinkIcon.vue'
import WarningIcon from '../icons/WarningIcon.vue'
import MdCard from '../md/MdCard.vue'
import StatusLabel from './StatusLabel.vue'

// The reserved state names of the palette. Declared at runtime below, so the validator stays in place.
type StatusName = keyof typeof STATUS

// A value in a warning or error state is coloured and carries an icon, so the state never rests on colour alone.
const VALUE_TONE: Partial<Record<StatusName, string>> = {
  warning: 'text-warning',
  critical: 'text-error'
}

const props = defineProps({
  label: {
    type: String,
    required: true
  },
  value: {
    type: String,
    required: true
  },
  /** Small print under the value: a limit, a comparison, whatever gives the number scale. */
  detail: {
    type: String,
    default: ''
  },
  /**
   * Share of a limit, 0 to 1. Present it and the tile grows a meter, which answers "how
   * close to full" at a glance where "151.5 of 512 MB" makes the reader do the division.
   */
  ratio: {
    type: Number as PropType<number | null>,
    default: null
  },
  /**
   * What a screen reader says for the meter, in words: the value, the limit and the unit. Without it the
   * meter announces only its percentage.
   */
  meterText: {
    type: String,
    default: ''
  },
  /** One of the reserved state names. Always shown with `statusLabel`, never colour alone. */
  status: {
    type: String as PropType<StatusName | null>,
    default: null,
    validator: (value: StatusName | null) => value === null || value in STATUS
  },
  statusLabel: {
    type: String,
    default: ''
  },
  /** Colours the value and puts an icon before it, for a value that needs the reader's attention. */
  valueStatus: {
    type: String as PropType<StatusName | null>,
    default: null,
    validator: (value: StatusName | null) => value === null || value in STATUS
  },
  /** Makes the whole card a link to a page inside the application, named by its label and value. */
  to: {
    type: [String, Object] as PropType<RouteLocationRaw | null>,
    default: null
  },
  link: {
    type: String,
    default: ''
  }
})

// A meter reads 0 to 100. A value above its limit fills the bar and is announced as 100, never as more.
const meterValue = computed(() =>
  props.ratio === null ? 0 : Math.min(100, Math.max(0, Math.round(props.ratio * 100))))

const meterWidth = computed(() => `${meterValue.value}%`)

// The meter fill is the primary role until the reading is one the operator has to act on,
// and only then does it turn to a state colour.
const meterColor = computed(() => {
  if (props.ratio === null) return undefined
  if (props.ratio >= 0.9) return STATUS.critical
  if (props.ratio >= 0.75) return STATUS.warning
  return 'var(--md-sys-color-primary)'
})
</script>

<template>
  <MdCard
    :as="to ? RouterLink : 'div'"
    :to="to || undefined"
    :aria-label="to ? `${label}, ${value}` : undefined"
    variant="outlined"
    :class="['flex min-w-0 flex-col', to ? 'no-underline md-focus-ring hover:border-outline' : '']"
  >
    <div
      class="flex items-center gap-1.5"
      data-ingot-component="stat-tile"
    >
      <span class="md-label-large text-on-surface-variant">{{ label }}</span>
      <a
        v-if="link"
        :href="link"
        target="_blank"
        rel="noopener"
        class="md-state-layer md-focus-ring inline-flex size-6 items-center justify-center rounded-corner-full text-on-surface-variant hover:text-on-surface"
        :aria-label="`${label}: open in a new tab`"
      >
        <LinkIcon class="w-3" />
      </a>
    </div>

    <!--
      Tabular figures on purpose: several of these values are replaced while the reader is
      looking at them, and proportional digits make the whole tile twitch on every poll.
    -->
    <p
      :class="['pt-1 md-headline-medium tabular-nums', valueStatus ? VALUE_TONE[valueStatus] : 'text-on-surface']"
    >
      <WarningIcon
        v-if="valueStatus && VALUE_TONE[valueStatus]"
        class="mr-2 inline-block size-6 align-text-bottom"
        aria-hidden="true"
      />
      {{ value }}
    </p>

    <div
      v-if="ratio !== null"
      class="mt-2 h-1.5 w-full overflow-hidden rounded-corner-full bg-surface-container-highest"
      role="meter"
      :aria-valuenow="meterValue"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuetext="meterText || `${meterValue} percent`"
      :aria-label="`${label}: ${meterValue} percent of the limit`"
    >
      <div
        class="h-full rounded-corner-full"
        :style="{ width: meterWidth, backgroundColor: meterColor }"
      />
    </div>

    <p
      v-if="detail"
      class="pt-1.5 md-body-small text-on-surface-variant"
    >
      {{ detail }}
    </p>

    <p
      v-if="status"
      class="pt-1.5 md-body-small text-on-surface-variant"
    >
      <StatusLabel :color="STATUS[status]">
        {{ statusLabel }}
      </StatusLabel>
    </p>
  </MdCard>
</template>
