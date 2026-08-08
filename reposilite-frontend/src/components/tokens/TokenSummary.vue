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
import { computed } from 'vue'
import { useTokens } from '../../store/tokens'
import TokenTag from './TokenTag.vue'

const props = defineProps({
  token: {
    type: Object,
    required: true
  }
})

const { toMs, tokenIsManager } = useTokens()

const DAY = 86400000
const amount = (count, unit) => `${count} ${unit}${count === 1 ? '' : 's'}`
const rel = (ms) => {
  const days = Math.floor(Math.abs(Date.now() - ms) / DAY)
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

const expired = computed(() => !!props.token.expiresAt && Date.now() > toMs(props.token.expiresAt))

/*
 * Written out rather than shortened. This is read once, in passing, and a reader who has to
 * work out what a glyph stands for is a reader who gets the expiry of a credential wrong.
 */
const expiry = computed(() => {
  if (!props.token.expiresAt) return 'never expires'
  if (expired.value) return 'expired'
  const left = rel(toMs(props.token.expiresAt))
  return left === 'today' ? 'expires today' : `${left} left`
})

const dates = computed(() =>
  `Created ${formatDate(props.token.createdAt)}` +
  (props.token.expiresAt ? `  •  Expires ${formatDate(props.token.expiresAt)}` : '  •  Never expires'))
</script>

<template>
  <span class="whitespace-nowrap font-semibold text-gray-800 dark:text-gray-100">{{ token.name }}</span>
  <TokenTag>{{ token.identifier.type.toLowerCase() }}</TokenTag>
  <TokenTag v-if="tokenIsManager(token)" accent>
    manager
  </TokenTag>
  <span class="min-w-0 flex-1 truncate text-gray-500 dark:text-gray-500">{{ token.description }}</span>
  <span
    class="inline-flex cursor-default items-center gap-2 whitespace-nowrap text-xs text-gray-500 max-sm:hidden dark:text-gray-400"
    :title="dates"
  >
    <span>{{ age }}</span>
    <span class="text-gray-400 dark:text-gray-500">·</span>
    <span :class="expired ? 'text-red-500' : 'text-gray-600 dark:text-gray-300'">{{ expiry }}</span>
  </span>
</template>
