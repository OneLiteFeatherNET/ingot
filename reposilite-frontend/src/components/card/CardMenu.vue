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

<script setup>
import { ref, watchEffect } from 'vue'

defineProps({
  configurations: {
    type: Object,
    required: true
  }
})

const emit = defineEmits([
  'selectTab'
])

const selectedTab = ref(localStorage.getItem('card-tab') || 'Maven')
watchEffect(() => {
  localStorage.setItem('card-tab', selectedTab.value)
  emit('selectTab', selectedTab.value)
})

const selectConfiguration = (configuration) =>
  selectedTab.value = configuration.name
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
    class="-mx-2 overflow-x-auto border-b border-outline-variant"
  >
    <div class="flex flex-nowrap">
      <button
        v-for="configuration in configurations"
        :key="configuration.name"
        type="button"
        class="md-state-layer md-focus-ring relative shrink-0 cursor-pointer whitespace-nowrap rounded-t-corner-small px-4 min-h-12 md-title-small transition-colors duration-(--md-sys-motion-duration-short3)"
        :class="configuration.name === selectedTab ? 'text-primary' : 'text-on-surface-variant'"
        :aria-current="configuration.name === selectedTab ? 'true' : undefined"
        @click="selectConfiguration(configuration)"
      >
        {{ configuration.name }}
        <span
          aria-hidden="true"
          class="absolute inset-x-2 bottom-0 h-[3px] rounded-t-[3px] transition-colors duration-(--md-sys-motion-duration-short3)"
          :class="configuration.name === selectedTab ? 'bg-primary' : 'bg-transparent'"
        />
      </button>
    </div>
  </div>
</template>
