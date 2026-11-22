<!--
  - Copyright (c) 2023 dzikoysk
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
import { ref, watchEffect, type PropType } from 'vue'
import type { SnippetConfiguration } from './snippetTypes'
import { useTabIndicator } from '../../composables/useTabIndicator'

defineProps({
  configurations: {
    type: Object as PropType<SnippetConfiguration[]>,
    required: true
  }
})

const emit = defineEmits<{
  'select-tab': [tab: string]
  /** @deprecated Listen to select-tab instead. The alias is kept for one release with the same tab name. */
  selectTab: [tab: string]
}>()

const selectedTab = ref(localStorage.getItem('card-tab') || 'Maven')
watchEffect(() => {
  localStorage.setItem('card-tab', selectedTab.value)
  emit('select-tab', selectedTab.value)
  emit('selectTab', selectedTab.value)
})

const selectConfiguration = (configuration: SnippetConfiguration) =>
  selectedTab.value = configuration.name

// One indicator for the whole row, moved under the selected tab with a transform (design "Indicator slide"), 8 pixels in
// from both edges of the tab as before. The first paint places it at once and a later choice slides it on the motion
// tokens. Reduced motion is the base rule of src/style.css, which makes the slide an instant swap.
const buttons = new Map<string, HTMLElement>()
const remember = (name: string, element: unknown) => {
  if (element instanceof HTMLElement) buttons.set(name, element)
}
const indicator = useTabIndicator(
  selectedTab,
  () => buttons.get(selectedTab.value),
  () => [...buttons.values()],
  8,
)
</script>

<!--
  One row of build tools that scrolls sideways when it runs out of width, which is what the
  main navigation does. There used to be a second implementation underneath: a dropdown that
  only appeared below 640px, so the same choice was made two different ways depending on the
  window. It also never reopened after a reload, because its open flag came back from
  localStorage as the string "false", which is true.
-->
<template>
  <div
    id="card-menu"
    class="-mx-2 overflow-x-auto border-b border-outline-variant [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
    data-ingot-component="card-menu"
  >
    <div class="relative flex flex-nowrap">
      <button
        v-for="configuration in configurations"
        :key="configuration.name"
        :ref="(element) => remember(configuration.name, element)"
        type="button"
        class="md-state-layer md-focus-ring relative shrink-0 cursor-pointer whitespace-nowrap rounded-t-corner-small px-4 min-h-12 md-title-small transition-colors duration-(--md-sys-motion-duration-short3)"
        :class="configuration.name === selectedTab ? 'text-primary' : 'text-on-surface-variant'"
        :aria-current="configuration.name === selectedTab ? 'true' : undefined"
        @click="selectConfiguration(configuration)"
      >
        {{ configuration.name }}
      </button>
      <span
        v-show="indicator.transform.value"
        data-test="tab-indicator"
        aria-hidden="true"
        class="pointer-events-none absolute bottom-0 left-0 h-0.5 w-px origin-left bg-primary"
        :class="indicator.moved.value ? 'transition-transform duration-md-short4 ease-md-standard-decelerate' : ''"
        :style="{ transform: indicator.transform.value }"
      />
    </div>
  </div>
</template>
