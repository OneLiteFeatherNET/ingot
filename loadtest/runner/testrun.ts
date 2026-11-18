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
// The k6-operator TestRun of one run (design D6). Built as a plain object and applied as JSON, so the unit tests check
// the manifest without a cluster. The namespace is checked first, before anything is built.

import {
  INGOT_RELEASE,
  K6_IMAGE,
  LOADTEST_NODE_LABEL,
  assertLoadNamespace,
} from '../lib/config.ts';
import { runnerEnvironment, type RunIdentity } from './environment.ts';

export interface TestRunInput extends RunIdentity {
  readonly namespace: string;
  readonly parallelism: number;
  /** The ConfigMap that holds the `k6 archive` file. */
  readonly configMap: string;
  readonly archiveFile: string;
}

export interface TestRunManifest {
  readonly apiVersion: 'k6.io/v1alpha1';
  readonly kind: 'TestRun';
  readonly metadata: { readonly name: string; readonly namespace: string };
  readonly spec: {
    readonly parallelism: number;
    readonly script: { readonly configMap: { readonly name: string; readonly file: string } };
    readonly arguments: string;
    readonly cleanup: 'post';
    readonly runner: {
      readonly image: string;
      readonly env: readonly { readonly name: string; readonly value: string }[];
      readonly nodeSelector: Readonly<Record<string, string>>;
      readonly tolerations: readonly { readonly key: string; readonly operator: 'Exists'; readonly effect: 'NoSchedule' }[];
      readonly affinity: {
        readonly podAntiAffinity: {
          readonly requiredDuringSchedulingIgnoredDuringExecution: readonly {
            readonly labelSelector: { readonly matchLabels: Readonly<Record<string, string>> };
            readonly topologyKey: string;
          }[];
        };
      };
    };
  };
}

export function buildTestRun(input: TestRunInput): TestRunManifest {
  assertLoadNamespace(input.namespace);
  const environment = runnerEnvironment(input);
  return {
    apiVersion: 'k6.io/v1alpha1',
    kind: 'TestRun',
    metadata: { name: `ingot-load-${input.testId}`, namespace: input.namespace },
    spec: {
      parallelism: input.parallelism,
      script: { configMap: { name: input.configMap, file: input.archiveFile } },
      arguments: environment.arguments.join(' '),
      cleanup: 'post',
      runner: {
        image: K6_IMAGE,
        env: environment.variables,
        nodeSelector: { [LOADTEST_NODE_LABEL]: 'true' },
        tolerations: [{ key: LOADTEST_NODE_LABEL, operator: 'Exists', effect: 'NoSchedule' }],
        affinity: {
          podAntiAffinity: {
            requiredDuringSchedulingIgnoredDuringExecution: [
              {
                labelSelector: { matchLabels: { 'app.kubernetes.io/instance': INGOT_RELEASE } },
                topologyKey: 'kubernetes.io/hostname',
              },
            ],
          },
        },
      },
    },
  };
}
