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
import { GLYPHS } from './icons.js'

/*
 * One icon of the dashboard, drawn from a path or from a glyph of the registry in icons.js. An icon is decorative unless
 * it has a label, so a screen reader skips it. The default size is 24px, the size of the existing icon slots.
 *
 * Props:
 *   path   the `d` string of a 24 by 24 viewBox, used when no name is given
 *   name   a glyph of the registry in icons.js
 *   size   sm (16px), md (20px) or lg (24px, the default)
 *   label  the accessible name; without one the icon is hidden from assistive technology
 */
const props = defineProps({
  path: { type: String, default: undefined },
  name: { type: String, default: undefined },
  size: {
    type: String,
    default: 'lg',
    validator: (value) => ['sm', 'md', 'lg'].includes(value)
  },
  label: { type: String, default: undefined }
})

const SIZES = Object.freeze({ sm: 'size-4', md: 'size-5', lg: 'size-6' })

const drawing = computed(() => props.path ?? GLYPHS[props.name])
</script>

<template>
  <svg
    :class="SIZES[size]"
    fill="none"
    stroke="currentColor"
    viewBox="0 0 24 24"
    xmlns="http://www.w3.org/2000/svg"
    :role="label ? 'img' : undefined"
    :aria-label="label"
    :aria-hidden="label ? undefined : 'true'"
    data-ingot-component="md-icon"
  >
    <path
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="2"
      :d="drawing"
    />
  </svg>
</template>
