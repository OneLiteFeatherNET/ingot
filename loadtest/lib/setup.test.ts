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
import { createFixtures, tokenRequest, type FixtureClient, type FixtureRequest, type Fixtures } from './setup.ts';

/** A fake client that records every request and answers with the given status. */
function fakeClient(status = 200) {
  const requests: FixtureRequest[] = [];
  const client: FixtureClient = {
    send: (request) => {
      requests.push(request);
      return { status };
    },
  };
  return { requests, client };
}

const fixtures: Fixtures = {
  files: [
    { path: '/releases/net/example/a/1.0.0/a-1.0.0.pom', body: '<project/>' },
    { path: '/releases/net/example/a/1.0.0/a-1.0.0.jar', body: new Uint8Array([1, 2, 3]) },
  ],
  tokens: [{ name: 'loadtest-reader', secret: 'reader-secret', readPaths: ['/private'] }],
};

test('every fixture is created through the admin client and no other client is called', () => {
  const admin = fakeClient();
  const reader = fakeClient();

  createFixtures({ admin: admin.client, reader: reader.client }, fixtures);

  assert.equal(admin.requests.length, 3, 'two files and one token go through the admin client');
  assert.deepEqual(reader.requests, [], 'the reader client of the scenario is never used for fixtures');
});

test('the tokens are created before the files, in the order they are given', () => {
  const admin = fakeClient();

  createFixtures({ admin: admin.client, reader: fakeClient().client }, fixtures);

  assert.deepEqual(
    admin.requests.map((request) => request.path),
    ['/api/tokens/loadtest-reader', '/releases/net/example/a/1.0.0/a-1.0.0.pom', '/releases/net/example/a/1.0.0/a-1.0.0.jar'],
  );
});

test('the token request is a PUT of a persistent token with read access to its paths', () => {
  const request = tokenRequest({ name: 'loadtest-reader', secret: 'reader-secret', readPaths: ['/private'] });

  assert.equal(request.method, 'PUT');
  assert.equal(request.path, '/api/tokens/loadtest-reader');
  assert.equal(request.headers['Content-Type'], 'application/json');
  assert.deepEqual(JSON.parse(request.body as string), {
    type: 'PERSISTENT',
    secret: 'reader-secret',
    permissions: [],
    routes: [{ path: '/private', permissions: ['r'] }],
  });
});

test('a failed fixture call fails the setup and names the path and the status', () => {
  const admin = fakeClient(500);

  assert.throws(
    () => createFixtures({ admin: admin.client, reader: fakeClient().client }, fixtures),
    /\/api\/tokens\/loadtest-reader.*500/,
  );
  assert.equal(admin.requests.length, 1, 'the setup stops at the first failed fixture');
});

test('a file that a release repository already holds from an earlier run counts as present', () => {
  const admin = fakeClient(409);
  const onlyFiles: Fixtures = { files: fixtures.files, tokens: [] };

  createFixtures({ admin: admin.client, reader: fakeClient().client }, onlyFiles);

  assert.equal(admin.requests.length, 2);
});

test('a token name that would leave the token path is refused before any call', () => {
  const admin = fakeClient();
  const bad: Fixtures = { files: [], tokens: [{ name: '../users', secret: 's', readPaths: [] }] };

  assert.throws(() => createFixtures({ admin: admin.client, reader: fakeClient().client }, bad), /token name/);
  assert.deepEqual(admin.requests, []);
});
