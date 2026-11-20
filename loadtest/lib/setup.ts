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
// The fixtures of setup() (design D10): tokens and artifact files, created through the Ingot admin API only, the same
// path a user takes. Nothing here writes to the database or the bucket. No k6 module is imported: the caller hands in
// the clients, so the k6 setup() passes clients over k6/http and the Node unit tests pass fakes.

import type { StatusResponse } from './checks.ts';

export interface FixtureRequest {
  readonly method: 'PUT';
  readonly path: string;
  readonly body: string | Uint8Array;
  readonly headers: Record<string, string>;
  /** The statuses that count as the fixture being present. */
  readonly expected: readonly number[];
}

/** A client that sends one request and answers with its status. Synchronous, as k6/http is. */
export interface FixtureClient {
  send(request: FixtureRequest): StatusResponse;
}

export interface SetupClients {
  /** The client that carries the admin credentials. The only one the fixtures go through. */
  readonly admin: FixtureClient;
  /** The client the scenario iterations read with. Never used for fixtures. */
  readonly reader: FixtureClient;
}

export interface FixtureFile {
  readonly path: string;
  readonly body: string | Uint8Array;
}

export interface TokenFixture {
  readonly name: string;
  readonly secret: string;
  /** The repository paths the token may read. */
  readonly readPaths: readonly string[];
}

export interface Fixtures {
  readonly files: readonly FixtureFile[];
  readonly tokens: readonly TokenFixture[];
}

/** 200 for a new file. 409 means an earlier run with the same seed left identical bytes in a release repository. */
const FILE_STATUSES: readonly number[] = [200, 201, 409];
const TOKEN_STATUSES: readonly number[] = [200];
const TOKEN_NAME = /^[A-Za-z0-9][A-Za-z0-9._-]*$/;

/** The admin API request that creates or updates a persistent token with read access to its paths. */
export function tokenRequest(token: TokenFixture): FixtureRequest {
  if (!TOKEN_NAME.test(token.name)) {
    throw new Error(`refusing the token name "${token.name}": it must be a single path segment`);
  }
  return {
    method: 'PUT',
    path: `/api/tokens/${token.name}`,
    body: JSON.stringify({
      type: 'PERSISTENT',
      secret: token.secret,
      permissions: [],
      routes: token.readPaths.map((path) => ({ path, permissions: ['r'] })),
    }),
    headers: { 'Content-Type': 'application/json' },
    expected: TOKEN_STATUSES,
  };
}

function fileRequest(file: FixtureFile): FixtureRequest {
  return { method: 'PUT', path: file.path, body: file.body, headers: {}, expected: FILE_STATUSES };
}

/**
 * Creates every fixture through the admin client: the tokens first, then the files. A fixture that answers with a
 * status outside its expected set fails the setup at once, so a run never measures against missing data.
 */
export function createFixtures(clients: SetupClients, fixtures: Fixtures): void {
  const requests = [...fixtures.tokens.map(tokenRequest), ...fixtures.files.map(fileRequest)];
  for (const request of requests) {
    const { status } = clients.admin.send(request);
    if (!request.expected.includes(status)) {
      throw new Error(
        `setup failed: ${request.method} ${request.path} answered ${status}, expected one of ${request.expected.join(', ')}`,
      );
    }
  }
}
