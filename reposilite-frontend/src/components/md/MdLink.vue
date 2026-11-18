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
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import MdIcon from './MdIcon.vue'

/*
 * Inline link in the primary colour role, with a focus ring and a state layer. A link with `to` is a router link, so it
 * navigates without a reload. A link with `href` is a plain link. A link with `external` opens in a new tab with
 * rel="noopener noreferrer", shows a small icon and says in its accessible name that it opens in a new tab. `external`
 * is for a page outside the dashboard, so it always uses `href`, and `to` is ignored when `external` is set.
 *
 * Props:
 *   to        a route location, for a link inside the dashboard
 *   href      a plain link, for a page outside the router
 *   external  the link opens in a new tab and says so
 * Slots: default (the link text).
 */
const props = withDefaults(defineProps<{
  to?: RouteLocationRaw
  href?: string
  external?: boolean
}>(), {
  to: undefined,
  href: undefined,
  external: false
})

defineSlots<{
  default?(): unknown
}>()

// A link with `to` is a router link. Both kinds render one element, which carries the anchor.
const routed = computed(() => props.to !== undefined && !props.external)

// The attributes of the element: a router link takes its route, a plain link takes its href and, when external, the new tab.
const destination = computed(() => (routed.value
  ? { to: props.to }
  : {
      href: props.href,
      target: props.external ? '_blank' : undefined,
      rel: props.external ? 'noopener noreferrer' : undefined
    }))

const LINK = 'md-touch-target md-focus-ring md-state-layer rounded-corner-small text-primary no-underline hover:underline'
</script>

<template>
  <component
    :is="routed ? RouterLink : 'a'"
    v-bind="destination"
    :class="LINK"
    data-ingot-component="md-link"
  >
    <slot />
    <template v-if="external">
      <MdIcon
        name="open-in-new"
        size="sm"
        class="ml-1 inline-block align-text-bottom"
      />
      <span class="sr-only">(opens in a new tab)</span>
    </template>
  </component>
</template>
