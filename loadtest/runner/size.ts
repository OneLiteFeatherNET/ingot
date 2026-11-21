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
// The pod size of a run (design D11). The runner reads `--size` and passes the helm arguments below to the install, so
// the overlays carry no numbers of their own. The heap follows the memory limit through the percentage in the overlays.

import { resourcesFor, sizeByName, type ReferenceSize } from '../lib/sizes.ts';

/** Reads `--size`. Without it the run keeps the chart default resources, and its record names no reference size. */
export function parseSizeOption(argv: readonly string[]): ReferenceSize | undefined {
  const index = argv.indexOf('--size');
  if (index === -1) {
    return undefined;
  }
  const value = argv[index + 1];
  if (value === undefined || value.startsWith('--')) {
    throw new Error('--size needs a value; expected one of N, T, S, M, L');
  }
  return sizeByName(value);
}

/**
 * The helm arguments that apply a size with requests equal to limits. They set the top-level resources only: the
 * all-in-one and split server use them, and the read, write and worker Deployments of scalable mode fall back to them,
 * so one size covers all three. Quantities go in as strings, so helm never turns "1" into a number.
 */
export function sizeHelmArguments(size: ReferenceSize): string[] {
  const { requests, limits } = resourcesFor(size);
  return [
    '--set-string', `resources.requests.cpu=${requests.cpu}`,
    '--set-string', `resources.requests.memory=${requests.memory}`,
    '--set-string', `resources.limits.cpu=${limits.cpu}`,
    '--set-string', `resources.limits.memory=${limits.memory}`,
  ];
}
