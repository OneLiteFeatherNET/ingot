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
// The legal line: the ICP licence number and the privacy policy link, when the operator set
// them. It is drawn in the flow of the page, so the footer that holds it can sit inside the
// content sheet as well as at the bottom of the page.
import usePlaceholders from '../../store/placeholders'

const { icpLicense, privacyPolicy } = usePlaceholders()
</script>

<template>
  <div
    v-if="icpLicense || privacyPolicy"
    class="h-8 pb-2 w-full text-center md-label-medium text-on-surface-variant"
  >
    <a
      v-if="icpLicense"
      class="text-primary"
      href="https://beian.miit.gov.cn"
      target="_blank"
    >{{ icpLicense }}</a>
    <span
      v-if="icpLicense && privacyPolicy"
      class="mx-1"
    >·</span>
    <a
      v-if="privacyPolicy"
      class="text-primary"
      :href="privacyPolicy"
      target="_blank"
    >Privacy Policy</a>
  </div>
</template>
