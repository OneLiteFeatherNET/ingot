<template>
  <fieldset v-if="control.visible" :class="styles.arrayList.root">
    <legend :class="styles.arrayList.legend">
      <label :class="styles.arrayList.label">
        {{ control.label }}
      </label>
      <MdButton
        variant="tonal"
        type="button"
        @click="addButtonClick"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
            <path d="M12 5v14M5 12h14" />
          </svg>
        </template>
        {{ addLabel }}
      </MdButton>
    </legend>
    <div v-if="control?.description" :class="styles.arrayList.description">
      {{ control.description }}
    </div>
    <div v-if="noData" :class="styles.arrayList.noData">
      No data
    </div>
    <div v-else class="tabs-component">
      <SettingsTabs
        v-model="selectedIndex"
        :label="control.label"
        variant="secondary"
        :tabs="itemTabs"
      />
      <div
        v-if="selectedIndex < control.data.length"
        :key="`${control.path}-${selectedIndex}-panel`"
        role="tabpanel"
        class="pt-4"
      >
        <div :class="styles.arrayList.itemWrapper">
          <array-list-element
            :moveUp="moveUp(control.path, selectedIndex)"
            :moveUpEnabled="selectedIndex > 0"
            :moveDown="moveDown(control.path, selectedIndex)"
            :moveDownEnabled="selectedIndex < control.data.length - 1"
            :delete="removeItems(control.path, [selectedIndex])"
            :label="childLabelForIndex(selectedIndex)"
            :styles="styles"
          >
            <dispatch-renderer
              :schema="control.schema"
              :uischema="childUiSchema"
              :path="composePaths(control.path, `${selectedIndex}`)"
              :enabled="control.enabled"
              :renderers="control.renderers"
              :cells="control.cells"
            />
          </array-list-element>
        </div>
      </div>
    </div>
  </fieldset>
</template>

<script>
import { ref } from 'vue'
import { composePaths, createDefaultValue, rankWith, schemaTypeIs } from '@jsonforms/core'
import { DispatchRenderer, rendererProps, useJsonFormsArrayControl } from '@jsonforms/vue'
import { useVanillaArrayControl } from '@dzikoysk/vue-vanilla'
import MdButton from '../md/MdButton.vue'
import SettingsTabs from '../settings/SettingsTabs.vue'
import ArrayListElement from './ArrayListElement.vue'

export const tester = rankWith(2, schemaTypeIs('array'))

export default {
  name: 'array-list-renderer',
  components: {
    ArrayListElement,
    DispatchRenderer,
    MdButton,
    SettingsTabs
  },
  props: {
    ...rendererProps()
  },
  setup(props) {
    const vanillaArrayControl = useVanillaArrayControl(useJsonFormsArrayControl(props))
    const selectedIndex = ref(0)

    const createLabel = (element) => {
      if (element.id?.length > 0) {
        const capitalized = element.id.charAt(0).toUpperCase() + element.id.slice(1)
        return capitalized
      }

      if (element.reference) {
        let adjustedUrl = element
          ?.reference
          ?.replace('https://', 'http://')
          ?.replace('http://', '')

        if (adjustedUrl.indexOf("/") > -1) {
          adjustedUrl = adjustedUrl.substring(0, adjustedUrl.indexOf("/"))
        }

        return adjustedUrl
      }

      if (typeof element == 'string' && element) {
        return element
      }

      return '<New>'
    }
    
    return {
      createLabel,
      selectedIndex,
      ...vanillaArrayControl
    }
  },
  computed: {
    noData() {
      return !this.control.data || this.control.data.length === 0
    },
    itemTabs() {
      return (this.control.data || []).map((element, index) => ({
        key: index,
        label: this.createLabel(element)
      }))
    },
    /* The add button names the kind of entry it adds, taken from the item schema title. */
    addLabel() {
      const title = this.control.schema?.items?.title
      return `Add ${title ? title.toLowerCase() : 'entry'}`
    }
  },
  methods: {
    composePaths,
    createDefaultValue,
    addButtonClick() {
      this.addItem(
        this.control.path,
        createDefaultValue(this.control.schema)
      )()
    }
  },
  watch: {
    'control.data'(newValue, oldValue) {
      newValue = newValue || []
      if (newValue.length != oldValue?.length) {
        this.selectedIndex = newValue.length - 1
      }
    }
  }
}
</script>

<style>
.tabs-component {
  width: 100%;
}
@media (min-width: 640px) {
  .tabs-component {
    max-width: 520px;
  }
}
@media (min-width: 768px) {
  .tabs-component {
    max-width: 648px;
  }
}
@media (min-width: 1024px) {
  .tabs-component {
    max-width: 900px;
  }
}
@media (min-width: 1280px) {
  .tabs-component {
    max-width: 1110px;
  }
}
</style>

