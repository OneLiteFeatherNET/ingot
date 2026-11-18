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

<script setup lang="ts">
import MdCard from '../md/MdCard.vue'
import MdStatePanel from '../md/MdStatePanel.vue'

withDefaults(defineProps<{
  title: string
  /** Names the unit and the window, so the axis does not have to repeat either. */
  subtitle?: string
  loading?: boolean
  /**
   * The request behind the card failed. Shown in place of the plot with a retry, which is a
   * different thing from "nothing to draw": a failure must not read as an empty instance.
   */
  error?: string
  /** Shown instead of the plot when there is genuinely nothing to draw. */
  emptyMessage?: string
}>(), {
  subtitle: '',
  loading: false,
  error: '',
  emptyMessage: ''
})

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <MdCard
    variant="outlined"
    class="min-w-0"
  >
    <header
      class="pb-3"
      data-ingot-component="chart-card"
    >
      <h3 class="md-title-medium text-on-surface">
        {{ title }}
      </h3>
      <p
        v-if="subtitle"
        class="md-body-small text-on-surface-variant"
      >
        {{ subtitle }}
      </p>
    </header>

    <div
      v-if="loading"
      data-ingot-state="loading"
      class="flex h-56 items-center"
    >
      <MdStatePanel state="loading" />
    </div>

    <div
      v-else-if="error"
      class="flex h-56 items-center"
    >
      <MdStatePanel
        state="error"
        :description="error"
        @retry="emit('retry')"
      />
    </div>

    <!--
      An empty state says which switch produces the emptiness. A blank rectangle leaves the
      reader deciding between "nothing happened" and "this is broken".
    -->
    <div
      v-else-if="emptyMessage"
      class="flex h-56 items-center"
    >
      <MdStatePanel
        state="empty"
        :description="emptyMessage"
      />
    </div>

    <slot v-else />
  </MdCard>
</template>
