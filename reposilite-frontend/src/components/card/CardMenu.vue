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
  One row that scrolls sideways when it runs out of width, which is what the main navigation
  does. There used to be a second implementation underneath: a dropdown that only appeared
  below 640px, so the same choice was made two different ways depending on the window.
  It also never reopened after a reload, because its open flag came back from localStorage
  as the string "false", which is true.
-->
<template>
  <div id="card-menu" class="mt-2 overflow-x-auto">
    <div class="flex flex-nowrap">
      <button
        v-for="configuration in configurations"
        :key="configuration.name"
        type="button"
        class="shrink-0 grow cursor-pointer whitespace-nowrap border-b-2 border-transparent px-7 py-4 text-center"
        :class="{ 'border-gray-800! dark:border-gray-100!': configuration.name === selectedTab }"
        :aria-current="configuration.name === selectedTab ? 'true' : undefined"
        @click="selectConfiguration(configuration)"
      >
        {{ configuration.name }}
      </button>
    </div>
  </div>
</template>
