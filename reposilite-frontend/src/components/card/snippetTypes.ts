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

// One tab of the snippet card: the build tool it names and the language of its snippet.
export interface SnippetConfiguration {
  name: string
  lang: string
}

// The repository a snippet is written for. The type tag tells it apart from an artifact.
export interface RepositorySnippetData {
  type: 'repository'
  repoId: string
  title: string
  domain: string
}

// The artifact a snippet is written for, at the version that is shown. The metadata may lack the ids.
export interface ArtifactSnippetData {
  type: 'artifact'
  groupId: string | null | undefined
  artifactId: string | null | undefined
  version: string
}

// The card starts with no data and fills in one of the two shapes above once the path is known.
export type SnippetData = RepositorySnippetData | ArtifactSnippetData | Record<string, never>
