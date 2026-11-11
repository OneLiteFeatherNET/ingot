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

import { createHash } from 'node:crypto'

/*
 * A name that belongs to one test. It is built from the worker index, a slug of the test title and a
 * short hash of the full title path, so it is the same on every run and differs between tests. The
 * server is shared by all workers, so this is what keeps two tests from creating the same repository.
 */
// The server refuses repository names longer than this, and records a failure when one is configured anyway.
const MAX_REPOSITORY_NAME = 64

const scopedName = (testInfo, label) => {
  const slug = testInfo.title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 24)
    .replace(/-+$/, '')
  const hash = createHash('sha1').update(testInfo.titlePath.join('/')).digest('hex').slice(0, 6)
  const name = `e2e-w${testInfo.workerIndex}-${slug}-${hash}-${label}`
  if (name.length > MAX_REPOSITORY_NAME) {
    throw new Error(`the name ${name} is ${name.length} characters, the server allows ${MAX_REPOSITORY_NAME}; use a shorter label`)
  }
  return name
}

/* The same name without hyphens, for a test that checks where a long name breaks. */
const unbrokenName = (testInfo, label) => scopedName(testInfo, label).replace(/-/g, '')

export { scopedName, unbrokenName }
