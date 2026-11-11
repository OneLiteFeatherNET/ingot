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
 * One block of a plugin page. The block is data: its text and cells are drawn by Vue interpolation, so markup a
 * plugin sends is shown as characters. A path is called only when it is below the prefix of the plugin.
 */
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { JsonForms } from '@jsonforms/vue'
import { createURL, handleRequest } from '../../store/client'
import { useConfiguration } from '../../store/configuration'
import { displayValue, pathAllowed, tableRows, BLOCK_TYPES } from './pluginBlocks'
import { property } from '../../helpers/vue-extensions'

const props = defineProps({
  plugin: property(String, true),
  block: property(Object, true),
})

const { renderers } = useConfiguration()

// The request of a block: credentials and the CSRF header, the same as every other call of the dashboard.
const send = (config) => axios.request(handleRequest({ withCredentials: true, headers: {}, ...config, url: createURL(config.url) }))
const read = (path) => axios.get(createURL(path), handleRequest({ withCredentials: true, headers: {} }))

const known = computed(() => BLOCK_TYPES.includes(props.block?.type))

// The path a block reads from or writes to, and whether the dashboard may call it at all.
const sourcePath = computed(() => props.block?.source)
const refused = computed(() => {
  const paths = [props.block?.source, props.block?.target, props.block?.path].filter(path => path !== undefined)
  const bad = paths.find(path => !pathAllowed(props.plugin, path))
  return bad === undefined ? null : `The path ${bad} is not a route of plugin ${props.plugin}, the dashboard does not call it.`
})

const error = ref(null)
const loading = ref(false)

// Key value and table: one read of the source.
const data = ref(null)
const load = async () => {
  if (refused.value || !sourcePath.value) return
  loading.value = true
  try {
    const response = await read(sourcePath.value)
    data.value = response.data
    error.value = null
  } catch (failure) {
    error.value = `Could not load the data: ${failure?.message ?? 'unknown error'}`
  } finally {
    loading.value = false
  }
}

const keyValueRows = computed(() => {
  const object = data.value && typeof data.value === 'object' && !Array.isArray(data.value) ? data.value : {}
  return Object.entries(object).map(([key, value]) => ({ key, value: displayValue(value) }))
})

const table = computed(() => tableRows(Array.isArray(data.value) ? data.value : []))

// Action: a confirm text is asked for first, then the call is sent.
const confirming = ref(false)
const outcome = ref(null)
const runAction = async () => {
  confirming.value = false
  outcome.value = null
  try {
    await send({ method: props.block.method, url: props.block.path })
    outcome.value = { ok: true, text: `${props.block.label} done` }
  } catch (failure) {
    outcome.value = { ok: false, text: `${props.block.label} failed: ${failure?.message ?? 'unknown error'}` }
  }
}
const pressAction = () => {
  if (props.block.confirm) {
    confirming.value = true
    return
  }
  runAction()
}

// Status: one read at once, then one per refresh interval, and none while the page is hidden.
const status = ref(null)
const refreshMs = computed(() => (props.block?.refreshSeconds ?? 10) * 1000)
const readStatus = async () => {
  if (refused.value || !sourcePath.value) return
  try {
    status.value = (await read(sourcePath.value)).data
    error.value = null
  } catch (failure) {
    error.value = `Could not read the status: ${failure?.message ?? 'unknown error'}`
  }
}
const { pause, resume } = useIntervalFn(readStatus, refreshMs, { immediate: false })
const visibility = useDocumentVisibility()
watch(visibility, (state) => {
  if (props.block?.type !== 'status') return
  if (state === 'hidden') {
    pause()
  } else {
    readStatus()
    resume()
  }
}, { immediate: true })

// Form: the source fills the form, and the form is sent to its target.
const formData = ref({})
const formMessage = ref(null)
const loadForm = async () => {
  if (refused.value || !props.block?.source) return
  try {
    formData.value = (await read(props.block.source)).data ?? {}
  } catch (failure) {
    error.value = `Could not load the form: ${failure?.message ?? 'unknown error'}`
  }
}
const saveForm = async () => {
  formMessage.value = null
  try {
    await send({ method: props.block.method, url: props.block.target, data: formData.value })
    formMessage.value = { ok: true, text: 'Saved' }
  } catch (failure) {
    formMessage.value = { ok: false, text: `Not saved: ${failure?.message ?? 'unknown error'}` }
  }
}

if (props.block?.type === 'keyValue' || props.block?.type === 'table') {
  load()
} else if (props.block?.type === 'form') {
  loadForm()
}
</script>

<template>
  <section
    v-if="known"
    class="flex flex-col gap-3"
    :data-block="block.type"
    :data-ingot-block="block.type"
    data-ingot-component="plugin-block"
  >
    <p
      v-if="refused"
      role="alert"
      class="md-body-medium text-error"
    >
      {{ refused }}
    </p>

    <p
      v-if="block.type === 'text'"
      class="md-body-large text-on-surface whitespace-pre-line"
    >
      {{ block.text }}
    </p>

    <template v-else-if="block.type === 'keyValue'">
      <dl class="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2">
        <template
          v-for="row in keyValueRows"
          :key="row.key"
        >
          <dt class="md-label-large text-on-surface-variant">
            {{ row.key }}
          </dt>
          <dd class="md-body-large text-on-surface break-all">
            {{ row.value }}
          </dd>
        </template>
      </dl>
    </template>

    <template v-else-if="block.type === 'table'">
      <div class="overflow-x-auto">
        <table class="w-full text-left">
          <thead>
            <tr>
              <th
                v-for="column in block.columns"
                :key="column.key"
                scope="col"
                class="md-label-large px-2 py-2"
              >
                {{ column.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, index) in table.shown"
              :key="index"
            >
              <td
                v-for="column in block.columns"
                :key="column.key"
                class="md-body-medium px-2 py-2 break-all"
              >
                {{ displayValue(row[column.key]) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p
        v-if="table.cut"
        class="md-body-small text-on-surface-variant"
      >
        Showing {{ table.shown.length }} of {{ table.total }} rows.
      </p>
    </template>

    <template v-else-if="block.type === 'form'">
      <JsonForms
        :schema="block.schema"
        :uischema="block.uiSchema"
        :data="formData"
        :renderers="renderers"
        @change="({ data }) => (formData = data)"
      />
      <button
        type="button"
        class="md-label-large text-primary"
        @click="saveForm"
      >
        Save
      </button>
      <p
        v-if="formMessage"
        role="status"
        class="md-body-medium"
      >
        {{ formMessage.text }}
      </p>
    </template>

    <template v-else-if="block.type === 'action'">
      <div class="flex flex-wrap items-center gap-3">
        <button
          type="button"
          class="md-label-large rounded-corner-full px-4 py-2"
          :class="block.danger ? 'bg-error text-on-error' : 'bg-primary text-on-primary'"
          @click="pressAction"
        >
          {{ block.label }}
        </button>
      </div>
      <div
        v-if="confirming"
        class="flex flex-col gap-2 rounded-corner-medium bg-surface-container p-4"
      >
        <p class="md-body-large">
          {{ block.confirm }}
        </p>
        <div class="flex gap-3">
          <button
            type="button"
            data-test="confirm"
            class="md-label-large text-primary"
            @click="runAction"
          >
            Confirm
          </button>
          <button
            type="button"
            class="md-label-large text-on-surface-variant"
            @click="confirming = false"
          >
            Cancel
          </button>
        </div>
      </div>
      <p
        v-if="outcome"
        role="status"
        class="md-body-medium"
      >
        {{ outcome.text }}
      </p>
    </template>

    <template v-else-if="block.type === 'status'">
      <div
        v-if="status"
        class="flex flex-col gap-1"
      >
        <p class="md-title-medium">
          {{ status.state }}
        </p>
        <p class="md-body-large text-on-surface-variant">
          {{ status.message }}
        </p>
        <progress
          v-if="typeof status.progress === 'number'"
          class="w-full"
          :value="status.progress"
          max="1"
        />
      </div>
    </template>

    <p
      v-if="error"
      data-ingot-state="error"
      role="alert"
      class="md-body-medium text-error"
    >
      {{ error }}
    </p>
    <p
      v-if="loading"
      data-ingot-state="loading"
      class="md-body-small text-on-surface-variant"
    >
      Loading
    </p>
  </section>

  <p
    v-else
    class="md-body-medium text-on-surface-variant"
    data-ingot-component="plugin-block"
  >
    Unsupported block: {{ block?.type ?? 'none' }}. This version of the dashboard does not draw it.
  </p>
</template>
