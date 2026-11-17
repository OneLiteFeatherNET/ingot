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
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import {
  RESOLVE_EXPECTED_STATUSES,
  RESOLVE_FIXTURE_COORDINATE,
  resolveFixture,
  resolveRequests,
} from './resolve-plan.ts';

const sha1 = (bytes: Uint8Array): string => createHash('sha1').update(bytes).digest('hex');

test('a Maven client resolves the pom, the jar and the jar checksum', () => {
  const paths = resolveRequests('maven', RESOLVE_FIXTURE_COORDINATE).map((request) => request.kind);

  assert.deepEqual(paths, ['pom', 'jar', 'jar-sha1']);
});

test('a Gradle client resolves the module file, the jar and the jar checksum', () => {
  const kinds = resolveRequests('gradle', RESOLVE_FIXTURE_COORDINATE).map((request) => request.kind);

  assert.deepEqual(kinds, ['module', 'jar', 'jar-sha1']);
});

test('the requests address the Maven layout of the coordinate in the releases repository', () => {
  const [pom] = resolveRequests('maven', RESOLVE_FIXTURE_COORDINATE);

  assert.equal(pom?.path, '/releases/net/onelitefeather/loadtest/resolve-fixture/1.0.0/resolve-fixture-1.0.0.pom');
});

test('the fixture holds a file for every path that a Maven or a Gradle client requests', () => {
  const served = new Set(resolveFixture(RESOLVE_FIXTURE_COORDINATE, 42, sha1).map((file) => file.path));
  const requested = [...resolveRequests('maven', RESOLVE_FIXTURE_COORDINATE), ...resolveRequests('gradle', RESOLVE_FIXTURE_COORDINATE)];

  for (const request of requested) {
    assert.ok(served.has(request.path), `no fixture file for ${request.path}`);
  }
});

test('the jar checksum of the fixture is the SHA-1 of the jar it serves', () => {
  const files = resolveFixture(RESOLVE_FIXTURE_COORDINATE, 42, sha1);
  const jar = files.find((file) => file.path.endsWith('.jar'));
  const checksum = files.find((file) => file.path.endsWith('.jar.sha1'));

  assert.ok(jar && checksum);
  assert.equal(checksum.body, sha1(jar.body as Uint8Array));
});

test('the resolve scenario expects only 200 answers, because 404 belongs to resolve-miss', () => {
  assert.deepEqual([...RESOLVE_EXPECTED_STATUSES], [200]);
});
