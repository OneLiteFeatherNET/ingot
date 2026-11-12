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
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import MdButton from './MdButton.vue'

/*
 * A button that opens a menu of actions on what it names, such as the actions of one row of a table. The menu
 * is teleported to the body and placed from the bounding rect of the button, so a scrolling table cannot clip it.
 *
 * Props:
 *   label  the accessible name of the button, such as "Actions for releases"
 *   items  [{ value, label, ariaLabel, error, href }]: the text of each item, its accessible name, whether it is
 *          a destructive action (drawn in the error colour), and an optional address. An item with an address is a
 *          link with the menuitem role, so middle click and open in new tab work. A plain click on it is reported
 *          through `select` and not followed by the browser (the router takes it); a click with a modifier key is
 *          left to the browser, and the menu closes.
 *          An item with `type: 'radio'` and `checked` is a menu radio button, drawn as the choice in use. An item
 *          with `type: 'separator'` is a divider between groups, and takes no focus.
 * Slots:
 *   trigger  the content of the button, in place of the word Actions. The button is then named by its content,
 *            so the label names the menu alone; a visible name must not differ from the label of the button
 *   header   a line above the items, such as the name of the signed in user; it is not a menu item
 * Emits:
 *   select  the value of the chosen item; the menu closes and the focus returns to the button
 *
 * Keyboard: Enter or Space open the menu through the click of the native button, and ArrowDown opens it too. The
 * first item then has the focus. Arrow keys move between the items and wrap at the ends. Escape closes the menu
 * and returns the focus to the button. A focus that leaves the menu closes it, so Tab leaves the menu as well.
 */
defineProps({
  label: {
    type: String,
    required: true
  },
  items: {
    type: Array,
    required: true
  },
  // Classes for the trigger button, such as the width of an account button.
  triggerClass: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['select'])

const menuOpen = ref(false)
const root = ref(null)
const menuElement = ref(null)
const placement = ref({})

const GAP = 4
const triggerElement = () => root.value?.querySelector('button')
const menuItems = () => [...(menuElement.value?.querySelectorAll('[role="menuitem"], [role="menuitemradio"]') ?? [])]

const place = async () => {
  const trigger = triggerElement()
  if (!trigger) return
  const rect = trigger.getBoundingClientRect()
  const right = `${window.innerWidth - rect.right}px`
  placement.value = { top: `${rect.bottom + GAP}px`, right }
  await nextTick()
  const height = menuElement.value?.getBoundingClientRect().height ?? 0
  if (rect.bottom + GAP + height > window.innerHeight && rect.top - GAP - height >= 0) {
    placement.value = { bottom: `${window.innerHeight - rect.top + GAP}px`, right }
  }
}

const closeMenu = () => {
  menuOpen.value = false
}

const insideMenu = (node) => root.value?.contains(node) || menuElement.value?.contains(node)

const onPointerdown = (event) => {
  if (!insideMenu(event.target)) closeMenu()
}

// A scroll moves the button, so the menu follows it, and it closes only when the button has left the screen. A menu
// that closed on every scroll lost the race with a scroll that began before it opened: the browser scrolls a row into
// view for the click, and the scroll event arrives after the menu is drawn.
const onScroll = () => {
  const rect = triggerElement()?.getBoundingClientRect()
  if (!rect || rect.bottom < 0 || rect.top > window.innerHeight) closeMenu()
  else place()
}

const listen = (on) => {
  const toggle = on ? 'addEventListener' : 'removeEventListener'
  document[toggle]('pointerdown', onPointerdown)
  window[toggle]('scroll', onScroll, true)
  window[toggle]('resize', closeMenu)
}

watch(menuOpen, async (open) => {
  listen(open)
  if (!open) return
  await place()
  menuItems()[0]?.focus({ preventScroll: true })
})

onBeforeUnmount(() => listen(false))

const toggle = () => {
  menuOpen.value = !menuOpen.value
}

const openFromKeyboard = (event) => {
  event.preventDefault()
  menuOpen.value = true
}

const choose = (value) => {
  menuOpen.value = false
  emit('select', value)
  triggerElement()?.focus({ preventScroll: true })
}

// A link item is followed by the router on a plain click. With a modifier key, or a button other than the primary
// one, the browser opens the address itself, so the menu only closes.
const chooseLink = (event, value) => {
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) {
    closeMenu()
    return
  }
  event.preventDefault()
  choose(value)
}

const onMenuKeydown = (event) => {
  const items = menuItems()
  const index = items.indexOf(document.activeElement)
  const move = (step) => {
    event.preventDefault()
    items[(index + step + items.length) % items.length]?.focus()
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu()
    triggerElement()?.focus()
  } else if (event.key === 'ArrowDown') {
    move(1)
  } else if (event.key === 'ArrowUp') {
    move(-1)
  } else if (event.key === 'Home') {
    event.preventDefault()
    items[0]?.focus()
  } else if (event.key === 'End') {
    event.preventDefault()
    items[items.length - 1]?.focus()
  }
}

// Closes the menu when the focus leaves both the button and the menu, so a click elsewhere does not leave it open.
const onFocusout = (event) => {
  if (!insideMenu(event.relatedTarget)) closeMenu()
}

const itemClass = 'md-state-layer md-focus-ring relative flex w-full items-center rounded-corner-small px-3 py-2 text-left md-label-large text-on-surface'
</script>

<template>
  <div
    ref="root"
    class="relative shrink-0"
    data-ingot-component="md-overflow-menu"
    @focusout="onFocusout"
  >
    <MdButton
      variant="text"
      type="button"
      :class="triggerClass"
      :aria-label="$slots.trigger ? undefined : label"
      aria-haspopup="menu"
      :aria-expanded="menuOpen ? 'true' : 'false'"
      @click="toggle"
      @keydown.down="openFromKeyboard"
    >
      <slot name="trigger">
        Actions
      </slot>
    </MdButton>
    <Teleport to="body">
      <div
        v-if="menuOpen"
        ref="menuElement"
        role="menu"
        :aria-label="label"
        :style="{ position: 'fixed', ...placement }"
        class="z-50 flex w-56 max-w-[calc(100vw-2rem)] flex-col gap-1 rounded-corner-extra-small bg-surface-container p-2 shadow-elevation-2"
        @keydown="onMenuKeydown"
        @focusout="onFocusout"
      >
        <div
          v-if="$slots.header"
          role="none"
          class="px-3 pb-2 pt-1"
        >
          <slot name="header" />
        </div>
        <template
          v-for="(item, index) in items"
          :key="item.value ?? `separator-${index}`"
        >
          <div
            v-if="item.type === 'separator'"
            role="separator"
            class="my-1 h-px bg-outline-variant"
          />
          <a
            v-else-if="item.href"
            :href="item.href"
            role="menuitem"
            :aria-label="item.ariaLabel"
            :class="[itemClass, item.error ? '!text-error' : '']"
            @click="chooseLink($event, item.value)"
          >
            {{ item.label }}
          </a>
          <button
            v-else-if="item.type === 'radio'"
            type="button"
            role="menuitemradio"
            :aria-checked="item.checked ? 'true' : 'false'"
            :aria-label="item.ariaLabel"
            :class="[itemClass, item.checked ? '!bg-secondary-container !text-on-secondary-container' : '']"
            @click="choose(item.value)"
          >
            {{ item.label }}
          </button>
          <button
            v-else
            type="button"
            role="menuitem"
            :aria-label="item.ariaLabel"
            :class="[itemClass, item.error ? '!text-error' : '']"
            @click="choose(item.value)"
          >
            {{ item.label }}
          </button>
        </template>
      </div>
    </Teleport>
  </div>
</template>
