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
// A page a server plugin contributed, drawn from its declaration. The permission is checked by the page
// that shows it (IndexPage), so this view only draws what it was allowed to show.
import { computed } from 'vue'
import { contributedPageByRoute } from '../../navigation/registry'
import PluginBlock from './PluginBlock.vue'
import NotFoundView from '../util/NotFoundView.vue'
import { property } from '../../helpers/vue-extensions'

const props = defineProps({
  plugin: property(String, true),
  pageId: property(String, true),
})

const page = computed(() => contributedPageByRoute(props.plugin, props.pageId))

// The page id reaches the DOM only in the shape the server accepts for a page id, so a plugin cannot put free text there.
const PAGE_ID = /^[a-z0-9-]{1,32}$/
const anchoredPageId = computed(() => (page.value && PAGE_ID.test(page.value.id) ? page.value.id : undefined))
</script>

<template>
  <NotFoundView v-if="!page" />
  <section
    v-else
    class="container mx-auto flex flex-col gap-6 py-8"
    :aria-labelledby="`plugin-page-${page.plugin}-${page.id}`"
    :data-ingot-plugin-page="anchoredPageId"
    data-ingot-component="plugin-page"
  >
    <header
      data-ingot-part="header"
      class="flex flex-col gap-2"
    >
      <h2
        :id="`plugin-page-${page.plugin}-${page.id}`"
        class="md-headline-small text-on-surface"
      >
        {{ page.title }}
      </h2>
      <p
        v-if="page.description"
        class="md-body-large text-on-surface-variant"
      >
        {{ page.description }}
      </p>
    </header>
    <PluginBlock
      v-for="(block, index) in page.blocks"
      :key="index"
      :plugin="page.plugin"
      :block="block"
    />
  </section>
</template>
