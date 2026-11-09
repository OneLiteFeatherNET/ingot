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
// The first stop of a keyboard user, which skips the top bar and the navigation. The click is handled here: the
// default fragment navigation would add a history entry that the router does not own, so the address stays as it is.
const focusMain = (event) => {
  event.preventDefault()
  document.getElementById('main')?.focus()
}
</script>

<template>
  <a
    href="#main"
    class="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-corner-medium focus:bg-surface-container-highest focus:px-4 focus:py-2 md-label-large text-on-surface"
    @click="focusMain"
  >
    Skip to main content
  </a>
</template>
