<script setup>
import { computed } from 'vue'
import MdSwitch from '../md/MdSwitch.vue'

/*
 * Material Design 3 switch for a boolean setting in the generated forms, laid out as a list row:
 * the label and its description on the left, the switch on the right. The switch is MdSwitch.
 * Its visible state text is "Enabled" or "Disabled" for sighted users. The accessible name is the
 * label, through aria-labelledby, so the state text does not become part of it.
 */
const props = defineProps({
  id: {
    type: String,
    required: true
  },
  label: {
    type: String,
    required: true
  },
  modelValue: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  required: {
    type: Boolean,
    default: false
  },
  description: {
    type: String,
    default: undefined
  },
  errors: {
    type: String,
    default: undefined
  }
})

const emit = defineEmits(['update:modelValue'])

const labelId = computed(() => `${props.id}-label`)
</script>

<template>
  <div class="flex w-full flex-col py-1">
    <!-- A list row: the label and its description on the left, the switch on the right. -->
    <div class="flex min-h-14 items-center justify-between gap-4 px-4 py-2">
      <div class="flex min-w-0 flex-1 flex-col">
        <span
          :id="labelId"
          class="block md-body-large text-on-surface"
        >
          {{ label }}<span
            v-if="required"
            aria-hidden="true"
          > *</span>
        </span>
        <p
          v-if="description"
          class="md-body-medium text-on-surface-variant"
        >
          {{ description }}
        </p>
      </div>
      <MdSwitch
        :id="id"
        class="shrink-0"
        :model-value="modelValue"
        :disabled="disabled"
        :aria-labelledby="labelId"
        @update:model-value="emit('update:modelValue', $event)"
      >
        <!-- On phones the state word would squeeze the description, and the switch already announces its state. -->
        <span class="max-[599px]:sr-only">{{ modelValue ? 'Enabled' : 'Disabled' }}</span>
      </MdSwitch>
    </div>
    <p
      v-if="errors"
      class="md-body-small text-error px-4 pt-1"
    >
      {{ errors }}
    </p>
  </div>
</template>
