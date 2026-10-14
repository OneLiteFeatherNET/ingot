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
import { computed } from 'vue'

/*
 * Material Design 3 switch for a boolean setting. The switch is a button with role="switch",
 * so its state is announced through aria-checked, and the label above it is tied to it with
 * <label for>. The label is the accessible name; the visible state text is for sighted users.
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

const toggle = () => {
  if (!props.disabled) emit('update:modelValue', !props.modelValue)
}

const trackClasses = computed(() => [
  'md-focus-ring relative inline-flex h-8 w-13 shrink-0 cursor-pointer items-center rounded-corner-full border-2',
  'transition-colors duration-(--md-sys-motion-duration-short3) ease-md-standard',
  'disabled:cursor-not-allowed disabled:opacity-40',
  props.modelValue ? 'border-primary bg-primary' : 'border-outline bg-surface-container-highest'
])

// Handle: 16px outlined when off, 24px on-primary and pushed to the end when on (MD3 switch sizes).
const handleClasses = computed(() => [
  'absolute top-1/2 -translate-y-1/2 rounded-corner-full transition-all duration-(--md-sys-motion-duration-short3) ease-md-standard',
  props.modelValue ? 'left-[calc(100%-28px)] size-6 bg-on-primary' : 'left-1 size-4 bg-outline'
])
</script>

<template>
  <div class="flex flex-col items-start py-1">
    <label
      :for="id"
      class="block px-2 pb-1 md-body-small text-on-surface-variant"
    >
      {{ label }}<span
        v-if="required"
        aria-hidden="true"
      > *</span>
    </label>
    <p
      v-if="description"
      class="md-body-small text-on-surface-variant px-2 pb-1"
    >
      {{ description }}
    </p>
    <div class="flex items-center gap-3 px-2">
      <button
        :id="id"
        type="button"
        role="switch"
        :aria-checked="modelValue ? 'true' : 'false'"
        :disabled="disabled"
        :class="trackClasses"
        @click="toggle"
      >
        <span
          aria-hidden="true"
          :class="handleClasses"
        />
      </button>
      <span class="md-body-large text-on-surface">
        {{ modelValue ? 'Enabled' : 'Disabled' }}
      </span>
    </div>
    <p
      v-if="errors"
      class="md-body-small text-error px-2 pt-1"
    >
      {{ errors }}
    </p>
  </div>
</template>
