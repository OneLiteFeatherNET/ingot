<script setup lang="ts">
// A page a server plugin contributed, drawn from its declaration. The permission is checked by the page
// that shows it (IndexPage), and the page is drawn inside the Administration frame, which names it in
// its heading. This view draws the description and the plugin of the page, and then its blocks.
import { computed, provide, reactive } from 'vue'
import { contributedPageByRoute, pluginPageMotion } from '../../navigation/registry'
import PluginBlock from './PluginBlock.vue'
import NotFoundView from '../util/NotFoundView.vue'
import MdTransition from '../md/MdTransition.vue'
import { NoMotion } from '../md/NoMotion'
import { PLUGIN_FILTER_SCOPE, type DrawnPage, type PluginFilterScope } from './pluginBlocks'

const props = defineProps({
  plugin: { type: String, required: true },
  pageId: { type: String, required: true },
})

const page = computed(() => contributedPageByRoute(props.plugin, props.pageId) as DrawnPage | undefined)

// The page id reaches the DOM only in the shape the server accepts for a page id, so a plugin cannot put free text there.
const PAGE_ID = /^[a-z0-9-]{1,32}$/
const anchoredPageId = computed(() => (page.value && PAGE_ID.test(page.value.id) ? page.value.id : undefined))

// The page is swapped with the route transition, unless the page opts out. The key is the page id, so a change of page
// is a change of content, and the same page keeps its element.
const pageMotion = computed(() => (pluginPageMotion(page.value) === 'none' ? NoMotion : MdTransition))

// The filters of this page write their values here, and the list after them reads the values and the counts.
provide(PLUGIN_FILTER_SCOPE, reactive<PluginFilterScope>({ values: {}, facets: {} }))
</script>

<template>
  <component
    :is="pageMotion"
    pattern="fade-through"
    :data-ingot-plugin-page="anchoredPageId"
    data-ingot-component="plugin-page"
  >
    <NotFoundView v-if="!page" />
    <section
      v-else
      :key="page.id"
      class="flex flex-col gap-6"
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
  </component>
</template>
