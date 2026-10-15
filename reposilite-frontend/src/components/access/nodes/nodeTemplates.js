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
 * Templates are lists of nodes with a {repository} placeholder. The server hands the lists out;
 * the client fills the placeholder. Pure.
 */

const PLACEHOLDER = '{repository}'

/** True when the template has a {repository} placeholder and so needs a repository. */
const needsRepository = (template) => (template?.nodes ?? []).some(node => node.includes(PLACEHOLDER))

/** The nodes of a template with the placeholder replaced by the repository. */
const fillTemplate = (template, repository) =>
  (template?.nodes ?? []).map(node => node.split(PLACEHOLDER).join(repository ?? ''))

/**
 * What applying a template does to a list: { added, skipped }. `added` holds the entries that are
 * new (allowed), `skipped` the node texts that were already listed. A node already listed keeps
 * its own allowed value.
 */
const applyTemplate = (template, repository, existing) => {
  const listed = new Set((existing ?? []).map(entry => entry.node))
  const added = []
  const skipped = []
  for (const node of fillTemplate(template, repository)) {
    if (listed.has(node)) {
      skipped.push(node)
    } else {
      listed.add(node)
      added.push({ node, allowed: true })
    }
  }
  return { added, skipped }
}

export { PLACEHOLDER, applyTemplate, fillTemplate, needsRepository }
