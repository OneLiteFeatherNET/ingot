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
// The five reference pod sizes (design D11). The one file that defines them. Requests always equal limits.

export interface ReferenceSize {
  readonly name: 'N' | 'T' | 'S' | 'M' | 'L';
  /** Kubernetes CPU quantity, used for both the request and the limit. */
  readonly cpu: string;
  /** Kubernetes memory quantity, used for both the request and the limit. */
  readonly memory: string;
}

export const REFERENCE_SIZES: readonly ReferenceSize[] = [
  { name: 'N', cpu: '250m', memory: '256Mi' },
  { name: 'T', cpu: '500m', memory: '512Mi' },
  { name: 'S', cpu: '1', memory: '1Gi' },
  { name: 'M', cpu: '2', memory: '2Gi' },
  { name: 'L', cpu: '4', memory: '4Gi' },
];

export function sizeByName(name: string): ReferenceSize {
  const size = REFERENCE_SIZES.find((candidate) => candidate.name === name);
  if (size === undefined) {
    throw new Error(`"${name}" is not a reference size; expected one of N, T, S, M, L`);
  }
  return size;
}

export interface ContainerResources {
  readonly requests: { readonly cpu: string; readonly memory: string };
  readonly limits: { readonly cpu: string; readonly memory: string };
}

export function resourcesFor(size: ReferenceSize): ContainerResources {
  const quantities = { cpu: size.cpu, memory: size.memory };
  return { requests: { ...quantities }, limits: { ...quantities } };
}
