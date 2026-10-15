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
// The page footer: the operator's footer text and the legal line. IndexPage places it, once
// inside the content sheet and once below the page, and CSS shows the one that fits the window.
import SiteFooter from './SiteFooter.vue'
import SiteLegal from './SiteLegal.vue'
import { useAppearance } from '../../store/appearance'

const appearance = useAppearance()
</script>

<template>
  <div>
    <SiteFooter :text="appearance.footerText" />
    <SiteLegal />
  </div>
</template>
