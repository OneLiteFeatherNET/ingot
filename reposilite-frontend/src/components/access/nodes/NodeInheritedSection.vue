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
import { ref, type PropType } from 'vue'
import MdBadge from '../../md/MdBadge.vue'
import MdDisclosure from '../../md/MdDisclosure.vue'
import MdList from '../../md/MdList.vue'
import MdListItem from '../../md/MdListItem.vue'
import type { InheritedEntry } from './inheritedNodes'
import { inheritedSourceLabel, isOverridden } from './nodeList'
import type { NodeEntry } from './permissionNodesApi'

/*
 * The nodes a subject gets from teams, read only. The section is a disclosure, open to start with. A node that the subject also holds directly (same node text) is still listed and
 * is marked as overridden, because the direct node decides first.
 */
defineProps({
  /** [{ node, allowed, source, subject, depth }] */
  inherited: {
    type: Array as PropType<InheritedEntry[]>,
    required: true
  },
  /** The nodes held directly: the draft list of the editor. */
  direct: {
    type: Array as PropType<NodeEntry[]>,
    default: () => []
  },
  /** One line for each team whose nodes could not be read. */
  warnings: {
    type: Array as PropType<string[]>,
    default: () => []
  }
})

const open = ref(true)
</script>

<template>
  <section
    class="flex flex-col gap-2"
    data-test="inherited-section"
    data-ingot-component="node-inherited-section"
  >
    <MdDisclosure v-model:open="open">
      <template #summary>
        Inherited from teams ({{ inherited.length }})
      </template>
      <div class="flex flex-col gap-2">
        <p class="md-body-medium text-on-surface-variant">
          These nodes come from teams and cannot be changed here. A direct node that matches the same permission decides first.
        </p>
        <p
          v-for="warning in warnings"
          :key="warning"
          role="alert"
          class="md-body-medium text-warning"
          data-test="inherited-warning"
        >
          {{ warning }}
        </p>
        <div
          v-if="inherited.length > 0"
          class="rounded-corner-medium border border-outline-variant"
        >
          <MdList divided>
            <MdListItem
              v-for="entry in inherited"
              :key="`${entry.source}:${entry.subject}:${entry.node}`"
              data-test="inherited-row"
              :data-node="entry.node"
            >
              <code class="md-code-medium text-on-surface break-all"><template
                v-for="(part, index) in entry.node.split('.')"
                :key="index"
              >{{ part }}<template v-if="index < entry.node.split('.').length - 1">.<wbr></template></template></code>
              <p
                class="md-body-small text-on-surface-variant"
                data-test="inherited-source"
              >
                {{ inheritedSourceLabel(entry) }}
              </p>
              <MdBadge
                v-if="isOverridden(entry, direct)"
                class="mt-1"
                data-test="inherited-overridden"
              >
                Overridden by a direct node
              </MdBadge>
              <template #trailing>
                <span
                  class="md-label-large"
                  :class="entry.allowed ? 'text-on-surface' : 'text-error'"
                  data-test="inherited-state"
                >{{ entry.allowed ? 'Allowed' : 'Denied' }}</span>
              </template>
            </MdListItem>
          </MdList>
        </div>
      </div>
    </MdDisclosure>
  </section>
</template>
