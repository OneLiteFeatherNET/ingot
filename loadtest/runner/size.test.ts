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
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { REFERENCE_SIZES } from '../lib/sizes.ts';
import { parseSizeOption, sizeHelmArguments } from './size.ts';

test('parseSizeOption returns the reference size named by --size', () => {
  assert.deepEqual(parseSizeOption(['--topology', 'ha', '--size', 'M']), { name: 'M', cpu: '2', memory: '2Gi' });
});

test('parseSizeOption leaves the size unset when --size is absent, so the chart defaults stay', () => {
  assert.equal(parseSizeOption(['--topology', 'ha', '--profile', 'plateau']), undefined);
});

test('parseSizeOption refuses an unknown size and names it', () => {
  assert.throws(() => parseSizeOption(['--size', 'XL']), /"XL" is not a reference size/);
});

test('parseSizeOption refuses a --size without a value', () => {
  assert.throws(() => parseSizeOption(['--topology', 'ha', '--size']), /--size needs a value/);
});

test('the helm arguments of a size set the requests equal to the limits', () => {
  for (const size of REFERENCE_SIZES) {
    assert.deepEqual(
      sizeHelmArguments(size),
      [
        '--set-string', `resources.requests.cpu=${size.cpu}`,
        '--set-string', `resources.requests.memory=${size.memory}`,
        '--set-string', `resources.limits.cpu=${size.cpu}`,
        '--set-string', `resources.limits.memory=${size.memory}`,
      ],
      `the helm arguments of size ${size.name}`,
    );
  }
});

test('the helm arguments of a size set only the top-level resources, which the scalable workloads fall back to', () => {
  for (const size of REFERENCE_SIZES) {
    for (const assignment of sizeHelmArguments(size).filter((argument) => argument.includes('='))) {
      assert.match(assignment, /^resources\./, `"${assignment}" sets more than the top-level resources`);
    }
  }
});
