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

import { describe, it, expect } from 'vitest'
import { classifyListingError } from './listingError'

const failedWithStatus = (status) => ({ response: { status, data: {} } })

describe('classifyListingError', () => {
  it('classifies a 404 response as not-found', () => {
    expect(classifyListingError(failedWithStatus(404)), 'a 404 is a missing directory').toBe('not-found')
  })

  it('classifies a 403 response as forbidden', () => {
    expect(classifyListingError(failedWithStatus(403)), 'a 403 is an access problem').toBe('forbidden')
  })

  it('classifies a request that never got a response as network', () => {
    const error = { message: 'Network Error', request: {} }

    expect(classifyListingError(error), 'no response means the repository was not reached').toBe('network')
  })

  it('classifies a server failure as unknown', () => {
    expect(classifyListingError(failedWithStatus(500)), 'a 500 is neither missing nor forbidden').toBe('unknown')
  })

  it('classifies a non-HTTP failure as unknown instead of network', () => {
    expect(classifyListingError(new TypeError('x is undefined')), 'a programming error is not a network error').toBe('unknown')
  })

  it('does not throw for a missing error', () => {
    expect(classifyListingError(undefined), 'an absent error still needs a kind').toBe('unknown')
  })
})
