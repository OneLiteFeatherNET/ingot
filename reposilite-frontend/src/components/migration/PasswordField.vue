<script setup>
/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// A password input with a show and hide control. The control is a button with aria-pressed, so its state is read
// with its label. The value is only emitted to the parent, which decides where it goes (the holder, a request body).
import { ref } from 'vue'
import MdTextField from '../md/MdTextField.vue'
import MdButton from '../md/MdButton.vue'

defineProps({
  id: {
    type: String,
    required: true
  },
  label: {
    type: String,
    default: 'Password'
  },
  modelValue: {
    type: String,
    default: ''
  },
  autocomplete: {
    type: String,
    default: 'current-password'
  },
  error: {
    type: Boolean,
    default: false
  },
  errorText: {
    type: String,
    default: ''
  },
  supportingText: {
    type: String,
    default: ''
  }
})

defineEmits(['update:modelValue'])

const revealed = ref(false)
</script>

<template>
  <div class="flex flex-col gap-2" data-ingot-component="password-field">
    <MdTextField
      :id="id"
      :label="label"
      :model-value="modelValue"
      :type="revealed ? 'text' : 'password'"
      :autocomplete="autocomplete"
      :error="error"
      :error-text="errorText"
      :supporting-text="supportingText"
      required
      variant="outlined"
      @update:model-value="$emit('update:modelValue', $event)"
    />
    <div>
      <MdButton
        variant="text"
        :aria-pressed="revealed ? 'true' : 'false'"
        @click="revealed = !revealed"
      >
        Show password
      </MdButton>
    </div>
  </div>
</template>
