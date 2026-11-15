/*
 * Copyright (c) 2023 dzikoysk
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

const parser = new DOMParser()

export default function useMetadata() {
  const groupId = (metadata?: Document): string | null | undefined =>
    metadata
      ?.getElementsByTagName('groupId')[0]
      ?.firstChild
      ?.nodeValue

  const artifactId = (metadata?: Document): string | null | undefined =>
    metadata
      ?.getElementsByTagName('artifactId')[0]
      ?.firstChild
      ?.nodeValue

  const versions = (metadata: Document): string[] => {
    return [...(metadata.querySelector('versioning versions') as Element).children]
      ?.map(node => (node.firstChild as ChildNode).nodeValue as string)
      ?? ['{unknown}']
  }

  const parseMetadata = (source: string) => {
    const metadata = parser.parseFromString(source, 'text/xml')
    const availableVersions = versions(metadata)

    return {
      metadata,
      groupId: groupId(metadata),
      artifactId: artifactId(metadata),
      versions: availableVersions
    }
  }
  
  return {
    parseMetadata,
    groupId,
    artifactId,
    versions
  }
}