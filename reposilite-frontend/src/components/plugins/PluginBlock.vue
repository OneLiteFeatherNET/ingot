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

<script setup lang="ts">
/*
 * One block of a plugin page. The block is data: its text and cells are drawn by Vue interpolation, so markup a
 * plugin sends is shown as characters. A path is called only when it is below the prefix of the plugin.
 */
import { computed, inject, reactive, ref, watch, type PropType } from 'vue'
import axios, { type AxiosRequestHeaders, type Method } from 'axios'
import { useDebounceFn, useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { JsonForms } from '@jsonforms/vue'
import { createURL, handleRequest } from '../../store/client'
import { useConfiguration } from '../../store/configuration'
import { copyToClipboard, selectText } from '../card/copySnippet'
import {
  displayValue, facetCount, failureText, filterKeyAllowed, linkAllowed, listQuery, listRows, pathAllowed, PLUGIN_FILTER_SCOPE, tableRows,
  BLOCK_TYPES, READER_REFUSED_TYPES, type FilterControlDefinition, type PluginBlockDefinition, type PluginFilterScope,
} from './pluginBlocks'
import MdButton from '../md/MdButton.vue'
import MdCard from '../md/MdCard.vue'
import MdDataTable from '../md/MdDataTable.vue'
import MdLinearProgress from '../md/MdLinearProgress.vue'
import MdSelect from '../md/MdSelect.vue'
import MdTextField from '../md/MdTextField.vue'

const props = defineProps({
  plugin: { type: String, required: true },
  block: { type: Object as PropType<PluginBlockDefinition>, required: true },
  // True on a reader page: the blocks that write or poll (form, action, status) are not drawn there and send nothing.
  reader: { type: Boolean, default: false },
})

const { renderers } = useConfiguration()

// The filters and the list of a page share one scope. A block drawn alone gets a scope of its own.
const scope = inject(PLUGIN_FILTER_SCOPE, null) ?? reactive<PluginFilterScope>({ values: {}, facets: {} })

// The request of a block: credentials and the CSRF header, the same as every other call of the dashboard.
const send = (config: { method?: Method; url?: string; data?: unknown }) => axios.request(handleRequest({ withCredentials: true, headers: {} as AxiosRequestHeaders, ...config, url: createURL(config.url as string) }))
const read = (path: string, params?: Record<string, string>) => axios.get(createURL(path), handleRequest({ withCredentials: true, headers: {} as AxiosRequestHeaders, params }))

const known = computed(() => BLOCK_TYPES.includes(props.block?.type ?? '')
  && !(props.reader && READER_REFUSED_TYPES.includes(props.block?.type ?? '')))

// Code: the copy action writes the code text and nothing else. A refused clipboard selects the text instead.
const codeText = ref<HTMLElement>()
const copyStatus = ref('')
const copyCode = async () => {
  if (await copyToClipboard(props.block.text ?? '', navigator.clipboard)) {
    copyStatus.value = 'Copied'
    return
  }
  selectText(codeText.value)
  copyStatus.value = 'The browser refused the copy. The text is selected, copy it with the keyboard.'
}

// Link: a same-origin path only, opened in the same tab.
const linkOk = computed(() => linkAllowed(props.block?.href))

// The path a block reads from or writes to, and whether the dashboard may call it at all.
const sourcePath = computed(() => props.block?.source)
const refused = computed(() => {
  const paths = [props.block?.source, props.block?.target, props.block?.path].filter(path => path !== undefined)
  const bad = paths.find(path => !pathAllowed(props.plugin, path))
  return bad === undefined ? null : `The path ${bad} is not a route of plugin ${props.plugin}, the dashboard does not call it.`
})

const error = ref<string | null>(null)
const loading = ref(false)

// Key value and table: one read of the source.
const data = ref<unknown>(null)
const load = async () => {
  if (refused.value || !sourcePath.value) return
  loading.value = true
  try {
    const response = await read(sourcePath.value)
    data.value = response.data
    error.value = null
  } catch (failure) {
    error.value = `Could not load the data: ${failureText(failure)}`
  } finally {
    loading.value = false
  }
}

const keyValueRows = computed(() => {
  const object = data.value && typeof data.value === 'object' && !Array.isArray(data.value) ? data.value : {}
  return Object.entries(object).map(([key, value]) => ({ key, value: displayValue(value) }))
})

const table = computed(() => tableRows(Array.isArray(data.value) ? data.value : []))
const tableData = computed(() => table.value.shown as Record<string, unknown>[])

// List: the first page at once, then the next page on request. The rows of every page are kept, and the cut is stated.
const listItems = ref<unknown[]>([])
const listNext = ref<string | null>(null)
const listLoaded = ref(false)
const list = computed(() => listRows(listItems.value))
const loadList = async (cursor?: string) => {
  if (refused.value || !sourcePath.value) return
  loading.value = true
  try {
    const response = await read(sourcePath.value, listQuery(scope.values, cursor))
    const page = Array.isArray(response.data?.items) ? response.data.items : []
    listItems.value = cursor ? [...listItems.value, ...page] : page
    listNext.value = typeof response.data?.next === 'string' ? response.data.next : null
    if (response.data?.facets && typeof response.data.facets === 'object' && !Array.isArray(response.data.facets)) {
      scope.facets = response.data.facets
    }
    listLoaded.value = true
    error.value = null
  } catch (failure) {
    error.value = `Could not load the list: ${failureText(failure)}`
  } finally {
    loading.value = false
  }
}
const loadMore = () => {
  if (listNext.value) loadList(listNext.value)
}
// A change of a filter value starts the list again from its first page, so the cursor of the old values is dropped.
watch(() => JSON.stringify(scope.values), () => {
  if (props.block?.type !== 'list') return
  listItems.value = []
  loadList()
})

// Action: a confirm text is asked for first, then the call is sent.
const confirming = ref(false)
const outcome = ref<{ ok: boolean; text: string } | null>(null)
const runAction = async () => {
  confirming.value = false
  outcome.value = null
  try {
    await send({ method: props.block.method, url: props.block.path })
    outcome.value = { ok: true, text: `${props.block.label} done` }
  } catch (failure) {
    outcome.value = { ok: false, text: `${props.block.label} failed: ${failureText(failure)}` }
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
const status = ref<{ state: string; message: string; progress?: number } | null>(null)
const refreshMs = computed(() => (props.block?.refreshSeconds ?? 10) * 1000)
const readStatus = async () => {
  if (refused.value || !sourcePath.value) return
  try {
    status.value = (await read(sourcePath.value)).data
    error.value = null
  } catch (failure) {
    error.value = `Could not read the status: ${failureText(failure)}`
  }
}
const { pause, resume } = useIntervalFn(readStatus, refreshMs, { immediate: false })
const visibility = useDocumentVisibility()
watch(visibility, (state) => {
  if (props.block?.type !== 'status' || !known.value) return
  if (state === 'hidden') {
    pause()
  } else {
    readStatus()
    resume()
  }
}, { immediate: true })

// Filter: a select or a text field writes its value to the scope of the page. An empty value is not written, so it is not sent.
const drafts = reactive<Record<string, string>>({})
const setValue = (key: string, value: string) => {
  if (value === '') {
    delete scope.values[key]
  } else {
    scope.values[key] = value
  }
}
const applyText = useDebounceFn(setValue, 300)
const typeText = (key: string, value: string) => {
  drafts[key] = value
  applyText(key, value)
}
const selectOptions = (control: FilterControlDefinition & { key: string }) => [
  { value: '', label: 'All' },
  ...(control.options ?? []).map(option => ({ value: option.value, label: optionLabel(control.key, option) })),
]
const controls = computed(() =>
  (props.block?.controls ?? []).filter((control): control is FilterControlDefinition & { key: string } => filterKeyAllowed(control.key)),
)
const optionLabel = (key: string, option: { value: string; label: string }) => {
  const count = facetCount(scope.facets, key, option.value)
  return count === null ? option.label : `${option.label} (${count})`
}

// Form: the source fills the form, and the form is sent to its target.
const formData = ref<Record<string, unknown>>({})
const formMessage = ref<{ ok: boolean; text: string } | null>(null)
const loadForm = async () => {
  if (refused.value || !props.block?.source) return
  try {
    formData.value = (await read(props.block.source)).data ?? {}
  } catch (failure) {
    error.value = `Could not load the form: ${failureText(failure)}`
  }
}
const saveForm = async () => {
  formMessage.value = null
  try {
    await send({ method: props.block.method, url: props.block.target, data: formData.value })
    formMessage.value = { ok: true, text: 'Saved' }
  } catch (failure) {
    formMessage.value = { ok: false, text: `Not saved: ${failureText(failure)}` }
  }
}

if (props.block?.type === 'keyValue' || props.block?.type === 'table') {
  load()
} else if (props.block?.type === 'form' && known.value) {
  loadForm()
} else if (props.block?.type === 'list') {
  loadList()
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

    <template v-else-if="block.type === 'code'">
      <p
        v-if="block.label"
        class="md-label-large text-on-surface-variant"
      >
        {{ block.label }}
      </p>
      <div class="flex justify-end">
        <MdButton
          variant="text"
          @click="copyCode"
        >
          Copy
        </MdButton>
      </div>
      <pre
        ref="codeText"
        class="md-body-medium text-on-surface whitespace-pre-wrap break-all rounded-corner-medium bg-surface-container p-4"
        :data-language="block.language ?? 'plain'"
      >{{ block.text }}</pre>
      <p
        role="status"
        class="md-body-small text-on-surface-variant"
      >
        {{ copyStatus }}
      </p>
    </template>

    <template v-else-if="block.type === 'link'">
      <a
        v-if="linkOk"
        :href="block.href"
        class="md-label-large text-primary break-all"
      >
        {{ block.label }}
      </a>
      <template v-else>
        <p class="md-body-large text-on-surface">
          {{ block.label }}
        </p>
        <p class="md-body-medium text-error">
          Link not shown
        </p>
      </template>
    </template>

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
      <MdDataTable
        :caption="block.label || `${plugin} table`"
        caption-hidden
        :columns="block.columns ?? []"
        :rows="tableData"
      >
        <template #cell="{ value }">
          {{ displayValue(value) }}
        </template>
      </MdDataTable>
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
      <MdButton
        variant="text"
        class="self-start"
        @click="saveForm"
      >
        Save
      </MdButton>
      <p
        v-if="formMessage"
        role="status"
        class="md-body-medium"
      >
        {{ formMessage.text }}
      </p>
    </template>

    <template v-else-if="block.type === 'list'">
      <ul
        v-if="list.shown.length"
        class="flex flex-col"
      >
        <li
          v-for="(row, index) in list.shown"
          :key="index"
          class="flex flex-col gap-1 py-3"
        >
          <a
            v-if="row.link"
            :href="row.link"
            class="md-title-medium text-primary break-all"
          >
            {{ row.title }}
          </a>
          <p
            v-else
            class="md-title-medium text-on-surface break-all"
          >
            {{ row.title }}
          </p>
          <p
            v-if="row.subtitle"
            class="md-body-medium text-on-surface-variant break-all"
          >
            {{ row.subtitle }}
          </p>
          <ul
            v-if="row.chips.length"
            class="flex flex-wrap gap-2"
          >
            <li
              v-for="(chip, chipIndex) in row.chips"
              :key="chipIndex"
              class="md-label-medium rounded-corner-full bg-surface-container px-2 py-1"
            >
              {{ chip }}
            </li>
          </ul>
          <p
            v-if="row.linkRefused"
            class="md-body-medium text-error"
          >
            Link not shown
          </p>
        </li>
      </ul>
      <p
        v-else-if="listLoaded && list.total === 0"
        class="md-body-large text-on-surface-variant"
      >
        Nothing to show.
      </p>
      <p
        v-if="list.cut"
        class="md-body-small text-on-surface-variant"
      >
        Showing {{ list.shown.length }} of {{ list.total }} items.
      </p>
      <p
        v-if="list.skipped"
        class="md-body-small text-on-surface-variant"
      >
        Skipped {{ list.skipped }} without a title.
      </p>
      <MdButton
        v-if="listNext && !list.cut"
        variant="text"
        class="self-start"
        @click="loadMore"
      >
        Load more
      </MdButton>
    </template>

    <div
      v-else-if="block.type === 'filter'"
      class="flex flex-col gap-3 medium:flex-row medium:flex-wrap"
    >
      <template
        v-for="(control, index) in controls"
        :key="index"
      >
        <MdTextField
          v-if="control.type === 'text'"
          :label="control.label ?? ''"
          :model-value="drafts[control.key] ?? ''"
          @update:model-value="typeText(control.key, $event)"
        />
        <MdSelect
          v-else-if="control.type === 'select'"
          :label="control.label ?? ''"
          :model-value="scope.values[control.key] ?? ''"
          :options="selectOptions(control)"
          @update:model-value="setValue(control.key, $event)"
        />
      </template>
    </div>

    <template v-else-if="block.type === 'action'">
      <div class="flex flex-wrap items-center gap-3">
        <MdButton
          :variant="block.danger ? 'error' : 'filled'"
          @click="pressAction"
        >
          {{ block.label }}
        </MdButton>
      </div>
      <MdCard
        v-if="confirming"
        tone="container"
        edge="none"
        class="flex flex-col gap-2"
      >
        <p class="md-body-large">
          {{ block.confirm }}
        </p>
        <div class="flex gap-3">
          <MdButton
            variant="text"
            data-test="confirm"
            @click="runAction"
          >
            Confirm
          </MdButton>
          <MdButton
            variant="text"
            @click="confirming = false"
          >
            Cancel
          </MdButton>
        </div>
      </MdCard>
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
        <MdLinearProgress
          v-if="typeof status.progress === 'number'"
          :value="status.progress"
          :label="status.state"
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
