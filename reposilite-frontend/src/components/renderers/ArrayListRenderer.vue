<template>
  <section
    v-if="control.visible"
    class="flex min-w-0 flex-col gap-3"
    data-test="array"
    data-ingot-component="array-list-renderer"
  >
    <div class="flex flex-row flex-wrap items-center justify-between gap-2">
      <h3
        v-if="control.label"
        class="md-title-medium text-on-surface"
      >
        {{ control.label }}
      </h3>
      <MdButton
        variant="text"
        type="button"
        :disabled="!control.enabled"
        @click="addClick"
      >
        <template #icon>
          <svg
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z" />
          </svg>
        </template>
        {{ addLabel }}
      </MdButton>
    </div>
    <p
      v-if="control.description"
      class="md-body-small whitespace-pre-line text-on-surface-variant"
    >
      {{ descriptionText(control.description) }}
    </p>
    <p
      v-if="noData"
      class="md-body-medium text-on-surface-variant"
    >
      No entries yet.
    </p>

    <ul
      v-else-if="primitive"
      class="m-0 flex list-none flex-col gap-2 p-0"
    >
      <li
        v-for="(item, index) in control.data"
        :key="`${control.path}-${index}`"
        class="flex min-w-0 items-center gap-2"
      >
        <MdTextField
          :id="`${control.id}-${index}`"
          class="min-w-0 flex-1"
          :model-value="displayOf(item)"
          :label="itemLabel"
          variant="outlined"
          :type="numeric ? 'number' : 'text'"
          :disabled="!control.enabled"
          @update:model-value="setItem(index, $event)"
        />
        <MdIconButton
          :aria-label="`Remove ${itemLabel.toLowerCase()} ${index + 1}`"
          :disabled="!control.enabled"
          @click="removeAt(index)"
        >
          <svg
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
          </svg>
        </MdIconButton>
      </li>
    </ul>

    <div
      v-else
      class="flex flex-col gap-3"
    >
      <MdCard
        v-for="(element, index) in control.data"
        :key="`${control.path}-${index}`"
        variant="low"
        class="min-w-0"
      >
        <!-- The remove button sits inside the card, so every card spans the same width as the fields. -->
        <div class="flex items-start gap-2">
          <MdDisclosure
            class="min-w-0 flex-1"
            open
          >
            <template #summary>
              {{ childLabelForIndex(index) || `${itemLabel} ${index + 1}` }}
            </template>
            <div class="flex flex-col gap-4">
              <DispatchRenderer
                :schema="control.schema"
                :uischema="childUiSchema"
                :path="composePaths(control.path, `${index}`)"
                :enabled="control.enabled"
                :renderers="control.renderers"
                :cells="control.cells"
              />
            </div>
          </MdDisclosure>
          <MdIconButton
            class="shrink-0"
            :aria-label="`Remove ${itemLabel.toLowerCase()} ${index + 1}`"
            :disabled="!control.enabled"
            @click="removeAt(index)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
            </svg>
          </MdIconButton>
        </div>
      </MdCard>
    </div>
  </section>
</template>

<script lang="ts">
import { composePaths, rankWith, schemaTypeIs } from '@jsonforms/core'
import type { ControlElement } from '@jsonforms/core'
import { DispatchRenderer, rendererProps, useJsonFormsArrayControl, useJsonFormsControl } from '@jsonforms/vue'
import { useVanillaArrayControl } from '@dzikoysk/vue-vanilla'
import { computed, defineComponent } from 'vue'
import { createEntryValue } from './util'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdDisclosure from '../md/MdDisclosure.vue'
import MdIconButton from '../md/MdIconButton.vue'
import MdTextField from '../md/MdTextField.vue'
import { descriptionText } from './description'

/*
 * Arrays, drawn as a list rather than as tabs. An array of plain values is a list of rows, each an
 * outlined text field with a remove button. An array of objects is a list of expandable items, each
 * item a section with its own fields. Add and remove go through the JSON Forms array helpers, and
 * a row's value is written to its own path, so the data stays bound to the form.
 */
export const tester = rankWith(2, schemaTypeIs('array'))

const PRIMITIVE_TYPES = ['string', 'number', 'integer']

/* The part of an entry's schema that the list reads: its type for the row kind and its title for the labels. */
interface ItemSchema {
  type?: string
  title?: string
}

export default defineComponent({
  name: 'ArrayListRenderer',
  components: {
    DispatchRenderer,
    MdButton,
    MdCard,
    MdDisclosure,
    MdIconButton,
    MdTextField
  },
  props: {
    ...rendererProps<ControlElement>()
  },
  setup(props) {
    const arrayControl = useVanillaArrayControl(useJsonFormsArrayControl(props))
    const { handleChange } = useJsonFormsControl(props)
    // The entries of the array are a list, so the template iterates them with a numeric index.
    const control = computed(() => ({
      ...arrayControl.control.value,
      data: arrayControl.control.value.data as unknown[] | undefined
    }))
    return {
      ...arrayControl,
      control,
      handleChange,
      composePaths,
      descriptionText
    }
  },
  computed: {
    noData() {
      return !this.control.data || this.control.data.length === 0
    },
    /* The control's schema is the schema of one entry, not of the array, so the entry's title and type live on it. */
    itemSchema(): ItemSchema {
      return (this.control.schema ?? {}) as ItemSchema
    },
    primitive() {
      return PRIMITIVE_TYPES.includes(this.itemSchema.type ?? '')
    },
    numeric() {
      return ['number', 'integer'].includes(this.itemSchema.type ?? '')
    },
    itemLabel() {
      return this.itemSchema.title ?? 'Entry'
    },
    /* The add button names the kind of entry it adds, taken from the item schema title. */
    addLabel() {
      return `Add ${this.itemLabel.toLowerCase()}`
    }
  },
  methods: {
    displayOf(item: unknown): string {
      return item === undefined || item === null ? '' : String(item)
    },
    addClick() {
      this.addItem(this.control.path, createEntryValue(this.control.schema))()
    },
    removeAt(index: number) {
      this.removeItems!(this.control.path, [index])()
    },
    /* An emptied text stays an empty string, so the row keeps its place. A number is read as a number. */
    setItem(index: number, raw: string) {
      let value: string | number | undefined = raw
      if (this.numeric) {
        const parsed = Number(raw)
        value = raw === '' || Number.isNaN(parsed) ? undefined : parsed
      }
      this.handleChange(composePaths(this.control.path, `${index}`), value)
    }
  }
})
</script>
