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
import { useHead } from '@unhead/vue'
import { useSession } from "./store/session"
import useTheme from "./store/theme"
import useQualifier from "./store/qualifier"
import usePlaceholders from './store/placeholders'

const { title, description, icpLicense, privacyPolicy } = usePlaceholders()
const { theme, fetchColorMode } = useTheme()
const { initializeSession } = useSession()
const { qualifier } = useQualifier()

useHead({
  title, 
  description
})
fetchColorMode()
initializeSession().catch(() => {})
</script>

<template>
  <div v-bind:class="{ 'dark': theme.isDark }">
    <div class="min-h-screen dark:bg-black dark:text-white">
      <router-view 
        class="router-view-full "
        :qualifier="qualifier"
      />
      <div v-if="icpLicense || privacyPolicy" class="absolute h-8 pb-2 w-full text-center text-xs dark:bg-black dark:text-white">
        <a v-if="icpLicense" href="https://beian.miit.gov.cn" target="_blank">{{ icpLicense }}</a>
        <span v-if="icpLicense && privacyPolicy" class="mx-1">·</span>
        <a v-if="privacyPolicy" :href="privacyPolicy" target="_blank">Privacy Policy</a>
      </div>
    </div>
  </div>
</template>

<!--
  - The global rules this component used to carry now live in src/style.css. They were
  - never scoped to App.vue to begin with, and a component style block cannot express the
  - cascade layer each of them needs.
  -->

