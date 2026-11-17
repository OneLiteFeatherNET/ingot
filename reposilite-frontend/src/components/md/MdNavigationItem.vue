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
import { isModifiedClick } from '../../helpers/modifiedClick'
import type { RouterNavigate } from './types'

/*
 * One destination of the navigation bar and the navigation rail: an active indicator (a pill), the label and a
 * link or a button. The bar and the rail share this item and pass only their variant.
 *
 * Props:
 *   label    the visible text, which is also the accessible name
 *   to       a route location. The item is then a router link, and a plain click goes through the router's navigate,
 *            so the page is not reloaded.
 *   href     a plain link outside the router, followed by the browser. Without `to` or `href` the item is a button.
 *   active   whether this is the current destination. Without it, a router link is current when its route is the
 *            exact current route. The current destination carries aria-current="page" and the indicator.
 *   variant  bar (a 64px indicator, the item takes an equal share of the bar) or rail (a 56px indicator, the full
 *            width of the rail)
 * Slots: leading (the icon, decorative, drawn in the indicator).
 * Emits select with the click event for a plain primary click. A modified click (Ctrl, Shift, Meta, Alt or a
 * button other than the primary one) is left to the browser: it is not prevented and it does not emit.
 *
 * The root is a wrapper with display: contents that carries the anchor, so the item has one named root whatever
 * its link kind is. The link or the button inside takes the layout of the item.
 */
const props = withDefaults(defineProps<{
  label: string
  to?: RouteLocationRaw
  href?: string
  active?: boolean
  variant?: 'bar' | 'rail'
}>(), {
  to: undefined,
  href: undefined,
  active: undefined,
  variant: 'bar'
})

defineSlots<{
  leading?(): unknown
}>()

const emit = defineEmits<{
  select: [event: MouseEvent]
}>()

// The bar and the rail differ in the width of the indicator and in how the item fills its row.
const VARIANTS = Object.freeze({
  bar: { root: 'h-14 min-w-0 flex-1 md-focus-ring', indicator: 'w-16' },
  rail: { root: 'min-h-14 w-full md-focus-ring-inset', indicator: 'w-14' }
})

const layout = computed(() => VARIANTS[props.variant])

const ROOT = 'group relative flex flex-col items-center justify-center gap-1 rounded-corner-large py-1 no-underline transition-colors duration-md-short3 ease-md-standard motion-reduce:transition-none'
const INDICATOR = 'md-state-layer flex h-8 items-center justify-center rounded-corner-full transition-colors duration-md-medium2 ease-md-standard group-hover:md-state-hover motion-reduce:transition-none'
const LABEL = 'md-label-medium block max-w-full truncate whitespace-nowrap text-center'

// A plain click is handled here. The router link passes its navigate function, which prevents the default
// action and moves the router; a plain link and a button leave the browser to their own default.
const select = (event: MouseEvent, navigate?: RouterNavigate) => {
  if (isModifiedClick(event)) {
    return
  }
  if (navigate) {
    void navigate(event)
  }
  emit('select', event)
}
</script>

<template>
  <span
    class="contents"
    data-ingot-component="md-navigation-item"
  >
    <RouterLink
      v-if="to !== undefined"
      v-slot="{ href: routeHref, navigate, isExactActive }"
      :to="to"
      custom
    >
      <a
        :href="routeHref"
        :aria-current="(active ?? isExactActive) ? 'page' : undefined"
        :class="[ROOT, layout.root, (active ?? isExactActive) ? 'text-on-surface' : 'text-on-surface-variant']"
        @click="select($event, navigate)"
      >
        <span
          data-test="indicator"
          :class="[INDICATOR, layout.indicator, (active ?? isExactActive) ? 'bg-secondary-container text-on-secondary-container' : '']"
          aria-hidden="true"
        >
          <slot name="leading" />
        </span>
        <span :class="[LABEL, (active ?? isExactActive) ? 'md-label-medium-prominent text-on-surface' : '']">{{ label }}</span>
      </a>
    </RouterLink>
    <a
      v-else-if="href !== undefined"
      :href="href"
      :aria-current="active ? 'page' : undefined"
      :class="[ROOT, layout.root, active ? 'text-on-surface' : 'text-on-surface-variant']"
      @click="select($event)"
    >
      <span
        data-test="indicator"
        :class="[INDICATOR, layout.indicator, active ? 'bg-secondary-container text-on-secondary-container' : '']"
        aria-hidden="true"
      >
        <slot name="leading" />
      </span>
      <span :class="[LABEL, active ? 'md-label-medium-prominent text-on-surface' : '']">{{ label }}</span>
    </a>
    <button
      v-else
      type="button"
      :aria-current="active ? 'page' : undefined"
      :class="[ROOT, layout.root, active ? 'text-on-surface' : 'text-on-surface-variant']"
      @click="select($event)"
    >
      <span
        data-test="indicator"
        :class="[INDICATOR, layout.indicator, active ? 'bg-secondary-container text-on-secondary-container' : '']"
        aria-hidden="true"
      >
        <slot name="leading" />
      </span>
      <span :class="[LABEL, active ? 'md-label-medium-prominent text-on-surface' : '']">{{ label }}</span>
    </button>
  </span>
</template>
