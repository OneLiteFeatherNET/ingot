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
import useMetadata from './metadata'

const { parseMetadata } = useMetadata()

const ARTIFACT_LEVEL = `<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>eu.cloudnetservice.cloudnet</groupId>
  <artifactId>cloudnet</artifactId>
  <versioning>
    <versions>
      <version>2.9.0</version>
      <version>3.0.0</version>
    </versions>
  </versioning>
</metadata>`

const VERSION_LEVEL_SNAPSHOT = `<?xml version="1.0" encoding="UTF-8"?>
<metadata modelVersion="1.1.0">
  <groupId>eu.cloudnetservice.cloudnet</groupId>
  <artifactId>wrapper-jvm-api</artifactId>
  <version>4.0.0-RC16-SNAPSHOT</version>
  <versioning>
    <snapshot>
      <localCopy>true</localCopy>
    </snapshot>
    <lastUpdated>20260101000000</lastUpdated>
  </versioning>
</metadata>`

describe('parseMetadata versions', () => {
  it('lists the versions of artifact-level metadata in order', () => {
    expect(parseMetadata(ARTIFACT_LEVEL).versions).toEqual(['2.9.0', '3.0.0'])
  })

  it('reads no versions from version-level snapshot metadata instead of failing', () => {
    expect(() => parseMetadata(VERSION_LEVEL_SNAPSHOT), 'the parser does not throw without a versions list').not.toThrow()
    expect(parseMetadata(VERSION_LEVEL_SNAPSHOT).versions, 'there is no list of versions to read').toEqual([])
  })

  it('keeps the group and artifact of version-level snapshot metadata', () => {
    const parsed = parseMetadata(VERSION_LEVEL_SNAPSHOT)

    expect(parsed.groupId).toBe('eu.cloudnetservice.cloudnet')
    expect(parsed.artifactId).toBe('wrapper-jvm-api')
  })
})
