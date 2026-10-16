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

// A failed chunk stays failed for the life of the page, so the only way to retry it is to
// load the page again.
const reload = () => window.location.reload()
</script>

<template>
  <div
    class="container mx-auto flex flex-col items-start gap-4 py-8"
    role="alert"
  >
    <p class="md-body-large text-on-surface">
      This view could not be loaded.
    </p>
    <MdButton
      variant="tonal"
      @click="reload()"
    >
      Reload
    </MdButton>
  </div>
</template>
