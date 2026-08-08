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
import usePlaceholders from '../../store/placeholders'
import MenuPanel from './MenuPanel.vue'

defineProps({
  logoClickCallback: {
    type: Function,
    required: false
  }
})

const { title } = usePlaceholders()
</script>

<template>
  <header class="bg-gray-100 dark:bg-black dark:text-white">
    <div class="container mx-auto flex flex-row py-10 justify-between max-sm:pb-2 max-sm:pt-5 max-sm:px-5 max-sm:justify-start">
      <h1 @click="logoClickCallback" class="text-xl font-medium py-1 max-sm:w-1/2">
        <router-link :to="'/'">{{ title }}</router-link>
      </h1>
      <MenuPanel class="mt-0.5"/>
    </div>
    <!--
      The avatar, description and organisation link used to sit here, which put them above
      the console, the settings form and every other view an operator works in: 356px of
      chrome before the first line of content, on a screen that is often 900px tall. They
      describe the repository, so they belong to the page that presents the repository, and
      the overview tab renders HeaderHero itself.
    -->
  </header>
</template>