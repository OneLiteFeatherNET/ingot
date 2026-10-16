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
/*
 * An MD3 segmented button: one outlined track whose segments are single-choice radios.
 *
 * The track is a radiogroup, so a screen reader announces the choice and the position of
 * each option. Arrow keys move the selection through the segments and wrap at the ends; Home
 * and End jump to the first and last. A move focuses the target and clicks it, so the parent's
 * click handler runs exactly as it does for a pointer. From the sm breakpoint the segments sit
 * in one row and split the track evenly. Below it a row cannot hold them all, so the track
 * wraps into rows and each segment keeps its label width. The track is 20px rounded: that is
 * a full pill for one 40px row and a rounded rectangle for two.
 */
defineProps({
  /** Names the group for a screen reader, which sees only the option labels otherwise. */
  label: {
    type: String,
    required: true
  }
})

const STEP = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }

const onKeydown = (event) => {
  const radios = [...event.currentTarget.querySelectorAll('[role="radio"]:not(:disabled)')]
  if (radios.length === 0) return
  const current = radios.indexOf(event.target)
  let next
  if (event.key === 'Home') {
    next = 0
  } else if (event.key === 'End') {
    next = radios.length - 1
  } else if (event.key in STEP && current >= 0) {
    next = (current + STEP[event.key] + radios.length) % radios.length
  } else {
    return
  }
  event.preventDefault()
  radios[next].focus()
  radios[next].click()
}
</script>

<template>
  <div
    role="radiogroup"
    :aria-label="label"
    class="flex w-full flex-wrap overflow-hidden rounded-[20px] border border-outline sm:flex-nowrap"
    @keydown="onKeydown"
  >
    <slot />
  </div>
</template>
