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
import AuthenticationGroupForm from './AuthenticationGroupForm.vue'
</script>

<template>
  <section
    aria-labelledby="security-title"
    class="flex flex-col gap-6"
    data-test="security"
  >
    <div class="flex flex-col gap-1">
      <h3
        id="security-title"
        class="md-title-large text-on-surface"
      >
        Security
      </h3>
      <p class="md-body-medium text-on-surface-variant">
        Limits repeated failed sign in attempts from one IP address. An address that exceeds the limit is blocked
        for the ban duration.
      </p>
    </div>

    <AuthenticationGroupForm
      group="bruteForceProtection"
      save-label="Save security settings"
    />
  </section>
</template>
