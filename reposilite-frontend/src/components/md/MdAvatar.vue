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
 * A circle that holds an initial, such as the first letter of an account name. The initial is the default
 * slot. Without a label the avatar is decoration and is hidden from assistive technology, because the name
 * is read elsewhere. With a label the avatar is an image named by that label, for the case where the initial
 * is the only content that says who the account is.
 *
 * Props:
 *   size   sm (24px) or md (32px, the default)
 *   label  the accessible name; omit it when the name is written next to the avatar
 * Slots: default (the initial)
 */
const props = withDefaults(defineProps<{
  size?: 'sm' | 'md'
  label?: string
}>(), {
  size: 'md',
  label: undefined
})

defineSlots<{
  default?(): unknown
}>()

const SIZES: Record<string, string> = {
  sm: 'size-6 md-label-medium',
  md: 'size-8 md-title-small'
}
</script>

<template>
  <span
    :data-size="props.size"
    :role="props.label ? 'img' : undefined"
    :aria-label="props.label || undefined"
    :aria-hidden="props.label ? undefined : 'true'"
    :class="['inline-flex shrink-0 items-center justify-center rounded-corner-full bg-primary-container text-on-primary-container', SIZES[props.size]]"
    data-ingot-component="md-avatar"
  >
    <slot />
  </span>
</template>
