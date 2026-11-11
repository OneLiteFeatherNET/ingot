/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The page state of the wizard, in memory only (design D4). Nothing here is persisted: a reload empties it,
 * and the jobs on the server are what a reload finds again. The literal password is never in this state; it
 * lives in the password holder, which `reset` also empties. A connection is kept without its password field,
 * even if a caller passes one.
 */

import { reactive } from 'vue'
import { createPasswordHolder } from './password'

const createWizardStore = ({ passwords = createPasswordHolder() } = {}) => {
  const state = reactive({
    connection: null,
    discovery: null,
    mapping: null,
    acceptance: null,
    jobs: [],
  })

  return {
    state,
    passwords,
    setConnection(connection) {
      const kept = { ...connection }
      delete kept.password
      state.connection = kept
    },
    setDiscovery(report) {
      state.discovery = report
    },
    setMapping(repositories, conflictPolicy) {
      state.mapping = {
        repositories: repositories.map(repository => ({ ...repository })),
        conflictPolicy,
      }
    },
    accept(acceptance) {
      state.acceptance = { ...acceptance }
    },
    setJobs(jobs) {
      state.jobs = jobs
    },
    // The page facts the gating rules read.
    gate() {
      return {
        connectionTested: state.connection !== null,
        discovered: state.discovery !== null,
        mapping: state.mapping,
      }
    },
    reset() {
      state.connection = null
      state.discovery = null
      state.mapping = null
      state.acceptance = null
      state.jobs = []
      passwords.forget()
    },
  }
}

// The one wizard of the dashboard. The tests build their own with createWizardStore.
const wizard = createWizardStore()

const useWizard = () => wizard

export { createWizardStore, useWizard }
