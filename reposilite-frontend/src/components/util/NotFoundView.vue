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
import MdButton from '../md/MdButton.vue'
</script>

<template>
  <section
    class="container mx-auto flex flex-col items-start gap-4 py-12"
    aria-labelledby="not-found-title"
  >
    <h2
      id="not-found-title"
      class="md-headline-small text-on-surface"
    >
      Page not found
    </h2>
    <p class="md-body-large text-on-surface-variant">
      Nothing lives at this address. It may have moved, or the link may be mistyped.
    </p>
    <router-link
      v-slot="{ href, navigate }"
      :to="{ name: 'overview' }"
      custom
    >
      <MdButton
        variant="tonal"
        :href="href"
        @click="navigate"
      >
        Back to overview
      </MdButton>
    </router-link>
  </section>
</template>
