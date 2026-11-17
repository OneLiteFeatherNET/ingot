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

// The request lists of the resolve-miss, metadata, auth, search-browse and dashboard scenarios. Pure: no k6 module, so the
// Node unit tests run it. The k6 scenario files only send these requests.

import { basicAuthHeader, bearerAuthHeader } from '../lib/auth.ts';
import type { Coordinate } from './resolve-plan.ts';

/** A group repository that merges the releases of the namespace. The runner must configure it under this name. */
export const GROUP_REPOSITORY = 'public';
/** A repository that answers only to callers with a token. The runner must configure it under this name. */
export const PRIVATE_REPOSITORY = 'private';

export const MISS_VERSION = '0.0.0-missing';
/** A group repository answers 404 for an artifact that no member holds. That is the expected outcome of a miss. */
export const MISS_EXPECTED_STATUSES: readonly number[] = [200, 404];
export const METADATA_EXPECTED_STATUSES: readonly number[] = [200];
export const AUTH_EXPECTED_STATUSES: readonly number[] = [200];
export const BROWSE_EXPECTED_STATUSES: readonly number[] = [200];
export const DASHBOARD_EXPECTED_STATUSES: readonly number[] = [200];

export const SEARCH_PATH = '/api/maven/search';
export const LOGIN_PATH = '/api/auth/login';
export const REPOSITORIES_PATH = '/api/maven/repositories';

function groupDirectory(coordinate: Coordinate): string {
  return coordinate.groupId.split('.').join('/');
}

/** A version that no repository holds, so the group answers with a miss. */
export function missPath(coordinate: Coordinate): string {
  const directory = groupDirectory(coordinate);
  const file = `${coordinate.artifactId}-${MISS_VERSION}`;
  return `/${GROUP_REPOSITORY}/${directory}/${coordinate.artifactId}/${MISS_VERSION}/${file}.jar`;
}

/** The metadata file that Maven reads to find the versions of an artifact. */
export function metadataPath(repository: string, coordinate: Coordinate): string {
  return `/${repository}/${groupDirectory(coordinate)}/${coordinate.artifactId}/maven-metadata.xml`;
}

export type AuthScheme = 'basic' | 'token';

export interface AuthRequest {
  readonly scheme: AuthScheme;
  readonly path: string;
  readonly headers: Record<string, string>;
}

export interface AuthCredentials {
  readonly user: string;
  readonly password: string;
  readonly token: string;
}

/** The same read of a private file, once with Basic credentials and once with a Bearer token. */
export function authRequests(path: string, credentials: AuthCredentials): AuthRequest[] {
  return [
    { scheme: 'basic', path, headers: basicAuthHeader(credentials.user, credentials.password) },
    { scheme: 'token', path, headers: bearerAuthHeader(credentials.token) },
  ];
}

export function directoryPath(repository: string, coordinate: Coordinate): string {
  return `/${repository}/${groupDirectory(coordinate)}/${coordinate.artifactId}/`;
}

export function searchPath(query: string): string {
  return `${SEARCH_PATH}?q=${encodeURIComponent(query)}`;
}

export function loginBody(username: string, password: string): string {
  return JSON.stringify({ username, password });
}
