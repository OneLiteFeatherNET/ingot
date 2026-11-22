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
import { imageHelmArguments } from './image.ts';

test('the dashboard image takes the tag of the server under test', () => {
  const args = imageHelmArguments('2.0.0');
  assert.ok(args.includes('dashboard.image.tag=2.0.0'), 'dashboard.image.tag is set');
  assert.ok(args.includes('image.tag=2.0.0'), 'image.tag is set');
});

test('the server digest is passed only when given', () => {
  assert.ok(!imageHelmArguments('nightly').some((a) => a.startsWith('image.digest=')));
  assert.ok(imageHelmArguments('nightly', 'sha256:abc').includes('image.digest=sha256:abc'));
});
