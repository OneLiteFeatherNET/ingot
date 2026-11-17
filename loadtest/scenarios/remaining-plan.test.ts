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
import { basicAuthHeader, bearerAuthHeader } from '../lib/auth.ts';
import {
  AUTH_EXPECTED_STATUSES,
  BROWSE_EXPECTED_STATUSES,
  DASHBOARD_EXPECTED_STATUSES,
  METADATA_EXPECTED_STATUSES,
  MISS_EXPECTED_STATUSES,
  authRequests,
  directoryPath,
  loginBody,
  metadataPath,
  missPath,
  searchPath,
} from './remaining-plan.ts';

const COORDINATE = { groupId: 'net.onelitefeather.loadtest', artifactId: 'resolve-fixture', version: '1.0.0' } as const;

test('a miss asks a group repository for a version that no member holds', () => {
  assert.equal(
    missPath(COORDINATE),
    '/public/net/onelitefeather/loadtest/resolve-fixture/0.0.0-missing/resolve-fixture-0.0.0-missing.jar',
  );
});

test('a miss accepts 404 as expected, next to 200', () => {
  assert.deepEqual([...MISS_EXPECTED_STATUSES], [200, 404]);
});

test('the metadata scenario reads the maven-metadata.xml of the artifact', () => {
  assert.equal(
    metadataPath('releases', COORDINATE),
    '/releases/net/onelitefeather/loadtest/resolve-fixture/maven-metadata.xml',
  );
  assert.deepEqual([...METADATA_EXPECTED_STATUSES], [200]);
});

test('the auth scenario sends the same read once with Basic and once with a Bearer token', () => {
  const requests = authRequests('/private/a/b.jar', { user: 'loadtest', password: 'secret', token: 'tok' });

  assert.deepEqual(
    requests.map((request) => [request.scheme, request.path]),
    [
      ['basic', '/private/a/b.jar'],
      ['token', '/private/a/b.jar'],
    ],
  );
  assert.deepEqual(requests[0]?.headers, basicAuthHeader('loadtest', 'secret'));
  assert.deepEqual(requests[1]?.headers, bearerAuthHeader('tok'));
  assert.deepEqual([...AUTH_EXPECTED_STATUSES], [200]);
});

test('the browse scenario lists a directory with a trailing slash', () => {
  assert.equal(directoryPath('releases', COORDINATE), '/releases/net/onelitefeather/loadtest/resolve-fixture/');
  assert.deepEqual([...BROWSE_EXPECTED_STATUSES], [200]);
});

test('the search query is encoded so that a coordinate survives the URL', () => {
  assert.equal(searchPath('net.onelitefeather:resolve-fixture 1.0'), '/api/maven/search?q=net.onelitefeather%3Aresolve-fixture%201.0');
});

test('the login body carries the username and the password and no provider', () => {
  assert.deepEqual(JSON.parse(loginBody('loadtest', 'secret')), { username: 'loadtest', password: 'secret' });
  assert.deepEqual([...DASHBOARD_EXPECTED_STATUSES], [200]);
});
