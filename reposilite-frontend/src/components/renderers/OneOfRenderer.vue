<template>
  <div
    v-if="control.visible"
    :class="styles.oneOf.root"
  >
    <MdSelect
      :id="`${control.id}-variant`"
      :model-value="String(tabIndex)"
      :label="control.label || 'Variants'"
      :options="variantOptions"
      :supporting-text="descriptionText(control.description)"
      :error="Boolean(control.errors)"
      :error-text="control.errors || ''"
      :disabled="!control.enabled"
      @update:model-value="onVariantSelected"
    />
    <div
      v-if="selectedRenderInfo"
      role="tabpanel"
      class="pt-4"
    >
      <DispatchRenderer
        :schema="selectedRenderInfo.schema"
        :uischema="selectedRenderInfo.uischema"
        :path="control.path"
        :renderers="control.renderers"
        :cells="control.cells"
      />
    </div>
  </div>
  <DialogWrapper :open="dialog">
    <p>
      Your data will be cleared if you navigate away from this tab. 
      Do you want to proceed?
    </p>
    <div class="dialog-actions">
      <button
        class="md-state-layer md-focus-ring"
        @click="cancel"
      >
        No
      </button>
      <button
        ref="confirm"
        class="md-state-layer md-focus-ring"
        @click="confirm"
      >
        Yes
      </button>
    </div>
  </DialogWrapper>
</template>

<script>
import {inject, ref, computed} from 'vue'
import {
  createCombinatorRenderInfos, createDefaultValue,
  getConfig,
  getData, getSchema, isInherentlyEnabled,
  isOneOfControl,
  rankWith,
} from '@jsonforms/core'
import {DispatchRenderer, rendererProps, useJsonFormsOneOfControl} from '@jsonforms/vue'
import {useVanillaControl} from '@dzikoysk/vue-vanilla'
import MdSelect from '../md/MdSelect.vue'
import isEmpty from 'lodash/isEmpty'
import DialogWrapper from '../util/DialogWrapper.vue'
import { descriptionText } from './description.js'

/*
 * The variant is picked from an outlined select, the same field as an enum. The rank is above the
 * vanilla oneOf renderer (3), which would otherwise draw its own select with the native chevron.
 */
export const tester = rankWith(4, isOneOfControl)

const isControlEnabled = (ownProps, jsonforms) => {
  const state = { jsonforms }
  const config = getConfig(state)
  const rootData = getData(state)
  const { uischema } = ownProps
  const rootSchema = getSchema(state)

  return isInherentlyEnabled(
      state,
      ownProps,
      uischema,
      ownProps.schema || rootSchema,
      rootData,
      config
  )
}

export default {
  name: 'OneOfRenderer',
  components: {
    DialogWrapper,
    DispatchRenderer,
    MdSelect
  },
  props: rendererProps(),
  setup(props) {
    const input = useJsonFormsOneOfControl(props)
    const selectedIndex = ref(input?.control?.value?.data?.type == 's3' ? 1 : (input.control.indexOfFittingSchema || 0)) // well...
    const tabIndex = ref(selectedIndex.value)
    const newSelectedIndex = ref(0)
    const dialog = ref(false)
    const jsonforms = inject('jsonforms')

    if (!jsonforms) {
      throw new Error("'jsonforms' couldn't be injected. Are you within JSON Forms?")
    }
    
    const controlEnabled = computed(() => isControlEnabled(props, jsonforms))

    return {
      ...useVanillaControl(input),
      selectedIndex,
      tabIndex,
      newSelectedIndex,
      dialog,
      controlEnabled,
      descriptionText
    }
  },
  computed: {
    subSchema() {
      return this.control.schema
    },
    oneOfTabs() {
      return this.oneOfRenderInfos.map((info, index) => ({ key: index, label: info.label }))
    },
    variantOptions() {
      return this.oneOfTabs.map(tab => ({ value: String(tab.key), label: tab.label }))
    },
    selectedRenderInfo() {
      return this.oneOfRenderInfos[this.selectedIndex]
    },
    oneOfRenderInfos() {
      return createCombinatorRenderInfos(
          this.subSchema.oneOf,
          this.control.rootSchema,
          'oneOf',
          this.control.uischema,
          this.control.path,
          this.control.uischemas
      )
    }
  },
  methods: {
    /* The select's value follows the choice first, so tabChanged sees the variant that was picked. */
    onVariantSelected(value) {
      this.tabIndex = Number(value)
      this.tabChanged()
    },
    tabChanged() {
      if (this.controlEnabled && !isEmpty(this.control.data)) {
        this.dialog = true
        this.$nextTick(() => {
          this.newSelectedIndex = this.tabIndex
          this.tabIndex = this.selectedIndex
        })
        setTimeout(() => this.$refs.confirm.focus())
      } else {
        this.$nextTick(() => {
          this.selectedIndex = this.tabIndex
        })
      }
    },
    confirm() {
      this.openNewTab()
      this.dialog = false
    },
    cancel() {
      this.newSelectedIndex = this.selectedIndex
      this.dialog = false
    },
    openNewTab() {
      this.handleChange(
          this.path,
          createDefaultValue(this.control.schema.oneOf[this.newSelectedIndex])
      )
      this.tabIndex = this.newSelectedIndex
      this.selectedIndex = this.newSelectedIndex
    }
  }
}
</script>

<style scoped>
@reference "../../style.css";
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 1.5rem;
}
dialog {
  @apply rounded-corner-extra-large border-0 bg-surface-container-high p-6 text-on-surface shadow-elevation-3 md-body-medium;
  @apply max-w-md;
}
dialog::backdrop {
  background-color: var(--md-sys-color-scrim);
  opacity: 0.4;
}
/* Scoped to this dialog. The rule used to be a bare `button`, which restyled every button in the app. */
.dialog-actions button {
  @apply relative inline-flex min-h-12 items-center justify-center rounded-corner-full bg-primary px-6 md-label-large text-on-primary;
}
</style>
