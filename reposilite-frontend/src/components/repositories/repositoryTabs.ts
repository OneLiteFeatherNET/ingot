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
 * The tabs of the page of one repository, in the order the design (D5) gives them. Every field of the repository
 * entry is on exactly one tab, or is the type, which the type section above the tabs draws. The keys are the values
 * of the `tab` query of the address (D7).
 */
export interface RepositoryTab {
  key: string
  label: string
  fields: string[]
}

export const REPOSITORY_TABS: RepositoryTab[] = [
  { key: 'general', label: 'General', fields: ['id', 'description', 'tags'] },
  { key: 'storage', label: 'Storage', fields: ['storageProvider', 'preserveSnapshots'] },
  { key: 'access', label: 'Access', fields: ['visibility'] },
  { key: 'mirror', label: 'Mirror and proxy', fields: ['proxied', 'storagePolicy', 'metadataMaxAge', 'protectedGroupPrefixes'] },
  { key: 'policy', label: 'Policy', fields: ['redeployment', 'readOnly', 'versionPolicy', 'uploadChecksumPolicy'] },
  { key: 'advanced', label: 'Advanced', fields: ['resolutionCacheMaxEntries', 'format'] },
]

// The fields of the other tabs. The form of one tab leaves them out, so each field is drawn on its own tab only.
export const fieldsOutside = (key: string): string[] => REPOSITORY_TABS
  .filter(tab => tab.key !== key)
  .flatMap(tab => tab.fields)
