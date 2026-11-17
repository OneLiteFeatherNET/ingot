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
/*
 * Material Design 3 divider: a horizontal rule that separates content. The inset starts the rule
 * at 16px or 72px, and 72px clears a leading avatar. A label sits in the rule, as the "or" between
 * two ways to sign in does; the rule then runs on both sides of the label.
 */
const props = withDefaults(defineProps<{
  inset?: 'none' | 16 | 72
  label?: string
}>(), {
  inset: 'none',
  label: undefined
})

const INSET: Record<string, string> = {
  none: '',
  16: 'ps-md-inset-16',
  72: 'ps-md-inset-72'
}
</script>

<template>
  <div
    role="separator"
    :aria-label="label"
    :data-inset="String(props.inset)"
    :class="['flex items-center gap-4', INSET[String(props.inset)]]"
    data-ingot-component="md-divider"
  >
    <span
      data-test="divider-rule"
      aria-hidden="true"
      class="h-px flex-1 bg-outline-variant"
    />
    <template v-if="label">
      <span
        data-test="divider-label"
        class="md-label-medium text-on-surface-variant"
      >{{ label }}</span>
      <span
        data-test="divider-rule"
        aria-hidden="true"
        class="h-px flex-1 bg-outline-variant"
      />
    </template>
  </div>
</template>
