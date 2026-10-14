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
import { computed } from 'vue'
import { useTokens } from '../../store/tokens'
import TokenTag from './TokenTag.vue'

const props = defineProps({
  token: {
    type: Object,
    required: true
  },
  /**
   * The reference time in epoch milliseconds. The view leaves it out and gets the current time;
   * tests pass a fixed value so that "3 days left" does not depend on the day it runs.
   */
  now: {
    type: Number,
    default: undefined
  }
})

const { toMs, tokenIsManager } = useTokens()

const DAY = 86400000
const current = computed(() => props.now ?? Date.now())
const amount = (count, unit) => `${count} ${unit}${count === 1 ? '' : 's'}`
const rel = (ms) => {
  const days = Math.floor(Math.abs(current.value - ms) / DAY)
  if (days < 1) return 'today'
  if (days < 30) return amount(days, 'day')
  if (days < 365) return amount(Math.floor(days / 30), 'month')
  return amount(Math.floor(days / 365), 'year')
}
const formatDate = (value) => value == null ? null : new Date(toMs(value)).toLocaleDateString(undefined, { timeZone: 'UTC' })

const age = computed(() => {
  const since = rel(toMs(props.token.createdAt))
  return since === 'today' ? 'new' : `${since} old`
})

const expired = computed(() => !!props.token.expiresAt && current.value > toMs(props.token.expiresAt))

/*
 * Written out rather than shortened. This is read once, in passing, and a reader who has to
 * work out what a glyph stands for is a reader who gets the expiry of a credential wrong.
 */
const expiry = computed(() => {
  if (!props.token.expiresAt) return 'never expires'
  if (expired.value) return `expired on ${formatDate(props.token.expiresAt)}`
  const left = rel(toMs(props.token.expiresAt))
  const until = formatDate(props.token.expiresAt)
  return left === 'today' ? `expires today (${until})` : `${left} left (until ${until})`
})
</script>

<template>
  <div class="flex min-w-0 flex-1 flex-col gap-1">
    <div class="flex flex-wrap items-center gap-2">
      <span class="md-title-medium truncate text-on-surface">{{ token.name }}</span>
      <TokenTag>{{ token.identifier.type.toLowerCase() }}</TokenTag>
      <TokenTag
        v-if="tokenIsManager(token)"
        accent
      >
        manager
      </TokenTag>
    </div>
    <p
      v-if="token.description"
      class="md-body-medium truncate text-on-surface-variant"
    >
      {{ token.description }}
    </p>
    <p class="md-body-small flex flex-wrap items-center gap-x-2 text-on-surface-variant">
      <span>{{ age }}</span>
      <span aria-hidden="true">·</span>
      <span :class="expired ? 'text-error' : ''">{{ expiry }}</span>
    </p>
  </div>
</template>
