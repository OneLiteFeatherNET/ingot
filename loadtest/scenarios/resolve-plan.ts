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

// The request list of the resolve scenario and the fixture it reads. Pure: no k6 module, so the Node unit tests run it.

import { ARTIFACT_SIZES, generateArtifact } from '../lib/artifacts.ts';

export type ResolveClient = 'maven' | 'gradle';
export type ResolveKind = 'pom' | 'jar' | 'jar-sha1' | 'module';

export interface Coordinate {
  readonly groupId: string;
  readonly artifactId: string;
  readonly version: string;
}

export interface ResolveRequest {
  readonly kind: ResolveKind;
  readonly path: string;
}

export interface FixtureFile {
  readonly path: string;
  readonly body: string | Uint8Array;
}

export const RESOLVE_REPOSITORY = 'releases';
export const RESOLVE_EXPECTED_STATUSES: readonly number[] = [200];
export const RESOLVE_FIXTURE_COORDINATE: Coordinate = {
  groupId: 'net.onelitefeather.loadtest',
  artifactId: 'resolve-fixture',
  version: '1.0.0',
};

/** The path of an artifact without its extension, in the Maven layout. */
export function artifactBase(repository: string, coordinate: Coordinate): string {
  const directory = coordinate.groupId.split('.').join('/');
  const file = `${coordinate.artifactId}-${coordinate.version}`;
  return `/${repository}/${directory}/${coordinate.artifactId}/${coordinate.version}/${file}`;
}

/** A Maven client asks for the pom, the jar and the jar checksum. A Gradle client reads the module file, then the jar. */
export function resolveRequests(client: ResolveClient, coordinate: Coordinate): ResolveRequest[] {
  const base = artifactBase(RESOLVE_REPOSITORY, coordinate);
  const jar: ResolveRequest = { kind: 'jar', path: `${base}.jar` };
  const jarSha1: ResolveRequest = { kind: 'jar-sha1', path: `${base}.jar.sha1` };
  if (client === 'maven') {
    return [{ kind: 'pom', path: `${base}.pom` }, jar, jarSha1];
  }
  return [{ kind: 'module', path: `${base}.module` }, jar, jarSha1];
}

/** The files that the resolve scenario reads. The checksum is computed by the caller, so this module hashes nothing. */
export function resolveFixture(
  coordinate: Coordinate,
  seed: number,
  sha1: (bytes: Uint8Array) => string,
): FixtureFile[] {
  const base = artifactBase(RESOLVE_REPOSITORY, coordinate);
  const jar = generateArtifact(seed, ARTIFACT_SIZES['1KB']);
  return [
    { path: `${base}.pom`, body: pomOf(coordinate) },
    { path: `${base}.jar`, body: jar },
    { path: `${base}.jar.sha1`, body: sha1(jar) },
    { path: `${base}.module`, body: moduleOf(coordinate) },
  ];
}

function pomOf(coordinate: Coordinate): string {
  return [
    '<project>',
    '  <modelVersion>4.0.0</modelVersion>',
    `  <groupId>${coordinate.groupId}</groupId>`,
    `  <artifactId>${coordinate.artifactId}</artifactId>`,
    `  <version>${coordinate.version}</version>`,
    '</project>',
    '',
  ].join('\n');
}

function moduleOf(coordinate: Coordinate): string {
  const component = { group: coordinate.groupId, module: coordinate.artifactId, version: coordinate.version };
  return JSON.stringify({ formatVersion: '1.1', component }, null, 2);
}
