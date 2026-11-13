<script setup>
// A page a server plugin contributed, drawn from its declaration. The permission is checked by the page
// that shows it (IndexPage), and the page is drawn inside the Administration frame, which names it in
// its heading. This view draws the description and the plugin of the page, and then its blocks.
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
    class="flex flex-col gap-6"
    :data-ingot-plugin-page="anchoredPageId"
    data-ingot-component="plugin-page"
  >
    <header
      data-ingot-part="header"
      class="flex flex-col gap-2"
    >
      <p
        v-if="page.description"
        class="md-body-large text-on-surface-variant"
      >
        {{ page.description }}
      </p>
      <p class="md-body-medium text-on-surface-variant">
        Provided by the {{ page.plugin }} plugin
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
