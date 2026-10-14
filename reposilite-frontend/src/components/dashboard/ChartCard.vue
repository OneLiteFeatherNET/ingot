<!--
  ~ Copyright (c) 2026 OneLiteFeather
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<script setup>
import MdCard from '../md/MdCard.vue'

defineProps({
  title: {
    type: String,
    required: true
  },
  /** Names the unit and the window, so the axis does not have to repeat either. */
  subtitle: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  /** Shown instead of the plot when there is genuinely nothing to draw. */
  emptyMessage: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <MdCard
    variant="outlined"
    class="min-w-0"
  >
    <header class="pb-3">
      <h2 class="md-title-medium text-on-surface">
        {{ title }}
      </h2>
      <p
        v-if="subtitle"
        class="md-body-small text-on-surface-variant"
      >
        {{ subtitle }}
      </p>
    </header>

    <div
      v-if="loading"
      class="skeleton-bars h-[220px] w-full rounded-corner-small bg-surface-container-high"
    />

    <!--
      An empty state says which switch produces the emptiness. A blank rectangle leaves the
      reader deciding between "nothing happened" and "this is broken".
    -->
    <p
      v-else-if="emptyMessage"
      class="flex h-[220px] items-center justify-center px-6 text-center md-body-medium text-on-surface-variant"
    >
      {{ emptyMessage }}
    </p>

    <slot v-else />
  </MdCard>
</template>
