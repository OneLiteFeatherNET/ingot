/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
-->

<script setup>
import { computed } from 'vue'
import { typeBadgeOf } from '../../store/maven/repositoryTypes'

/*
 * The type of a repository as a static MD3 label. A repository without a type of its own is
 * marked legacy: the hint is the tooltip, and it is also in the text for screen readers, so it
 * does not depend on hovering.
 *
 * Props:
 *   repository  the list entry, with `type` and `typeExplicit`
 */
const props = defineProps({
  repository: {
    type: Object,
    required: true
  }
})

const badge = computed(() => typeBadgeOf(props.repository))

const TONES = {
  hosted: 'bg-tertiary-container text-on-tertiary-container',
  proxy: 'bg-secondary-container text-on-secondary-container',
  group: 'bg-primary-container text-on-primary-container',
  mixed: 'border border-outline text-on-surface-variant'
}
</script>

<template>
  <span
    data-test="type-badge"
    :data-type="badge.key"
    :title="badge.hint || undefined"
    class="md-label-medium inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-corner-small px-2"
    :class="TONES[badge.key]"
  >
    {{ badge.label }}
    <span
      v-if="badge.legacy"
      data-test="legacy-hint"
      class="md-label-small opacity-80"
    >legacy</span>
    <span
      v-if="badge.legacy"
      class="sr-only"
    >{{ badge.hint }}</span>
  </span>
</template>
