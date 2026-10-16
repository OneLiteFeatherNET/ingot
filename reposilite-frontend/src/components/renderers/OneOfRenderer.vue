<template>
  <div
    v-if="control.visible"
    :class="styles.oneOf.root"
  >
    <MdSelect
      :id="variantSelectId"
      :model-value="String(shownIndex)"
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
    <VueFinalModal
      :model-value="dialog"
      teleport-to="body"
      :overlay-style="SCRIM_STYLE"
      :focus-trap="focusTrap"
      class="flex min-h-full items-center justify-center p-4"
      @update:model-value="onDialogUpdate"
    >
      <!-- The role sits on the card: the modal root always carries role="dialog" of its own. -->
      <div
        class="w-full min-w-[280px] max-w-[560px] rounded-corner-extra-large bg-surface-container-high p-6 text-on-surface shadow-elevation-3"
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="switchTitleId"
        :aria-describedby="switchBodyId"
      >
        <h2
          :id="switchTitleId"
          class="md-headline-small text-on-surface"
        >
          {{ switchTitle }}
        </h2>
        <p
          :id="switchBodyId"
          class="mt-4 md-body-medium text-on-surface-variant"
        >
          {{ switchBody }}
        </p>
        <div class="mt-6 flex flex-wrap justify-end gap-2">
          <MdButton
            :id="switchCancelId"
            variant="text"
            type="button"
            @click="cancelSwitch"
          >
            Cancel
          </MdButton>
          <MdButton
            :id="switchConfirmId"
            variant="text"
            type="button"
            @click="confirmSwitch"
          >
            Switch
          </MdButton>
        </div>
      </div>
    </VueFinalModal>
  </div>
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
import {VueFinalModal} from 'vue-final-modal'
import MdButton from '../md/MdButton.vue'
import MdSelect from '../md/MdSelect.vue'
import isEqual from 'lodash/isEqual'
import isPlainObject from 'lodash/isPlainObject'
import {SCRIM_STYLE} from '../settings/dialog'
import { descriptionText } from './description.js'

/*
 * The variant is picked from an outlined select, the same field as an enum. The rank is above the
 * vanilla oneOf renderer (3), which would otherwise draw its own select with the native chevron.
 */
export const tester = rankWith(4, isOneOfControl)

/* The properties a variant fixes to one value, such as the discriminator `type: { const: 's3' }`. */
export const constantsOf = (branch) => Object.entries(branch?.properties ?? {})
  .filter(([, schema]) => schema && Object.prototype.hasOwnProperty.call(schema, 'const'))
  .map(([key, schema]) => [key, schema.const])

/*
 * The index of the variant whose constants all match the data, or undefined when none does. A
 * branch without a constant cannot be recognised from the data and is never a match.
 */
export const variantIndexOf = (branches, data) => {
  if (!isPlainObject(data)) return undefined
  const index = branches.findIndex((branch) => {
    const constants = constantsOf(branch)
    return constants.length > 0 && constants.every(([key, value]) => data[key] === value)
  })
  return index === -1 ? undefined : index
}

/*
 * The data a variant starts from. createDefaultValue leaves out a constant without a default, so
 * the constants are written in: the discriminator is what later recognises the variant.
 */
export const defaultValueOf = (branch) => {
  const value = createDefaultValue(branch)
  if (!isPlainObject(value)) return value
  return {...value, ...Object.fromEntries(constantsOf(branch))}
}

/* A value nothing has been typed or chosen into: missing, null, or an empty string. */
const isBlank = (value) => value === undefined || value === null || value === ''

/*
 * Switching throws away the data of the current variant, so the dialog only asks when a field
 * holds something a switch would lose: a value that is neither blank nor the variant's default.
 * The discriminator equals its default, so it never counts on its own.
 */
export const holdsData = (data, branch) => {
  if (!isPlainObject(data)) return false
  const defaults = defaultValueOf(branch)
  return Object.entries(data).some(([key, value]) => !isBlank(value) && !isEqual(value, defaults[key]))
}

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
    DispatchRenderer,
    MdButton,
    MdSelect,
    VueFinalModal
  },
  props: rendererProps(),
  setup(props) {
    const input = useJsonFormsOneOfControl(props)
    const vanilla = useVanillaControl(input)
    const jsonforms = inject('jsonforms')

    if (!jsonforms) {
      throw new Error("'jsonforms' couldn't be injected. Are you within JSON Forms?")
    }

    const jsonformsControl = computed(() => input.control.value)
    const branches = computed(() => jsonformsControl.value.schema?.oneOf ?? [])
    const controlId = computed(() => jsonformsControl.value.id)

    // The variant the data is in. Its discriminator decides; the schema fit is only the fallback.
    const committedIndex = computed(() => variantIndexOf(branches.value, jsonformsControl.value.data)
        ?? jsonformsControl.value.indexOfFittingSchema
        ?? 0)

    // The variant the user picked while the dialog asks. The select shows it until the dialog closes.
    const pendingIndex = ref(null)
    const dialog = computed(() => pendingIndex.value !== null)
    const shownIndex = computed(() => pendingIndex.value ?? committedIndex.value)

    const oneOfRenderInfos = computed(() => createCombinatorRenderInfos(
        jsonformsControl.value.schema.oneOf,
        jsonformsControl.value.rootSchema,
        'oneOf',
        jsonformsControl.value.uischema,
        jsonformsControl.value.path,
        jsonformsControl.value.uischemas
    ))

    const controlEnabled = computed(() => isControlEnabled(props, jsonforms))

    const variantSelectId = computed(() => `${controlId.value}-variant`)
    const switchTitleId = computed(() => `${controlId.value}-switch-title`)
    const switchBodyId = computed(() => `${controlId.value}-switch-body`)
    const switchCancelId = computed(() => `${controlId.value}-switch-cancel`)
    const switchConfirmId = computed(() => `${controlId.value}-switch-confirm`)

    const variantOptions = computed(() => oneOfRenderInfos.value.map((info, index) => ({
      value: String(index),
      label: info.label
    })))
    const selectedRenderInfo = computed(() => oneOfRenderInfos.value[committedIndex.value])

    const switchTitle = computed(() => `Switch ${(jsonformsControl.value.label || 'Variants').toLowerCase()}?`)
    const switchBody = computed(() => {
      const current = oneOfRenderInfos.value[committedIndex.value]?.label
      return `The settings of ${current} will be removed. This takes effect when you save.`
    })

    /*
     * The modal traps focus, starts on Cancel and gives focus back to the select. Each id is read
     * when the dialog opens, so the functions below always find the elements that are on the page.
     */
    const focusTrap = computed(() => ({
      allowOutsideClick: true,
      initialFocus: () => document.getElementById(switchCancelId.value),
      setReturnFocus: () => document.getElementById(variantSelectId.value)
    }))

    const switchTo = (index) => {
      pendingIndex.value = null
      input.handleChange(jsonformsControl.value.path, defaultValueOf(branches.value[index]))
    }

    const onVariantSelected = (value) => {
      const next = Number(value)
      if (next === committedIndex.value) {
        pendingIndex.value = null
        return
      }
      if (controlEnabled.value && holdsData(jsonformsControl.value.data, branches.value[committedIndex.value])) {
        pendingIndex.value = next
      } else {
        switchTo(next)
      }
    }

    const cancelSwitch = () => {
      pendingIndex.value = null
    }

    const confirmSwitch = () => {
      const next = pendingIndex.value
      if (next !== null) switchTo(next)
    }

    const onDialogUpdate = (open) => {
      if (!open) cancelSwitch()
    }

    return {
      ...vanilla,
      dialog,
      focusTrap,
      oneOfRenderInfos,
      controlEnabled,
      committedIndex,
      shownIndex,
      selectedRenderInfo,
      variantOptions,
      variantSelectId,
      switchTitleId,
      switchBodyId,
      switchCancelId,
      switchConfirmId,
      switchTitle,
      switchBody,
      onVariantSelected,
      cancelSwitch,
      confirmSwitch,
      onDialogUpdate,
      SCRIM_STYLE,
      descriptionText
    }
  }
}
</script>
