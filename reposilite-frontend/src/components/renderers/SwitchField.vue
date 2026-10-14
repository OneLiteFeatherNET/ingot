<script setup>
import { computed } from 'vue'
import MdSwitch from '../md/MdSwitch.vue'

/*
 * Material Design 3 switch for a boolean setting in the generated forms. The label and the
 * description sit above the switch, and the switch is MdSwitch. Its visible state text is
 * "Enabled" or "Disabled" for sighted users. The accessible name is the label above, through
 * aria-labelledby, so the state text does not become part of it.
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
  <div class="flex flex-col items-start py-1">
    <span
      :id="labelId"
      class="block px-2 pb-1 md-body-small text-on-surface-variant"
    >
      {{ label }}<span
        v-if="required"
        aria-hidden="true"
      > *</span>
    </span>
    <p
      v-if="description"
      class="md-body-small text-on-surface-variant px-2 pb-1"
    >
      {{ description }}
    </p>
    <div class="px-2">
      <MdSwitch
        :id="id"
        :model-value="modelValue"
        :disabled="disabled"
        :aria-labelledby="labelId"
        @update:model-value="emit('update:modelValue', $event)"
      >
        {{ modelValue ? 'Enabled' : 'Disabled' }}
      </MdSwitch>
    </div>
    <p
      v-if="errors"
      class="md-body-small text-error px-2 pt-1"
    >
      {{ errors }}
    </p>
  </div>
</template>
