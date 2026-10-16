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
import { GENERIC_KEY_PATH, brandPathOf } from './providerIcons'

/*
 * The mark of a sign in provider, drawn in the current text colour (MD3 keeps brand colours out of
 * the controls). Decorative: the control that shows it is named by the provider label.
 */
const props = defineProps({
  icon: {
    type: String,
    default: 'GENERIC'
  }
})

const brand = computed(() => brandPathOf(props.icon))
const path = computed(() => brand.value ?? GENERIC_KEY_PATH)
const viewBox = computed(() => (brand.value ? '0 0 24 24' : '0 -960 960 960'))
</script>

<template>
  <svg
    class="size-6 shrink-0"
    :viewBox="viewBox"
    fill="currentColor"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="path" />
  </svg>
</template>
