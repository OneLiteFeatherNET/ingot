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
// The runner entry point: `npm run load -- --topology <t> --profile <p> --scenario <s> [--reference] [--parallelism n]`.
// The Ingot image tag is read from INGOT_IMAGE_TAG. The namespace is fixed to the load test namespace; the TestRun and
// the steps refuse any other one before a kubectl call. The effects below call kubectl, helm and docker (for k6 archive).

import { execFile } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { INGOT_RELEASE, K6_IMAGE, LOAD_NAMESPACE, parseRunOptions } from '../lib/config.ts';
import { labelledNodesCommand } from './nodes.ts';
import { buildTestRun } from './testrun.ts';
import { runSteps, type RunnerPorts } from './steps.ts';

const exec = promisify(execFile);
const loadtestRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const chartPath = join(loadtestRoot, '..', 'charts', 'ingot');

const READY_TIMEOUT_MS = 15 * 60_000;
/** The longest profile is the soak (2 hours) on top of the plateau series; the deadline leaves room for both. */
const COMPLETION_TIMEOUT_MS = 4 * 60 * 60_000;
const POLL_MS = 10_000;

async function kubectl(args: string[]): Promise<string> {
  const { stdout } = await exec('kubectl', args, { maxBuffer: 16 * 1024 * 1024 });
  return stdout;
}

function flagValue(argv: readonly string[], flag: string): string | undefined {
  const index = argv.indexOf(flag);
  return index === -1 ? undefined : argv[index + 1];
}

async function main(argv: readonly string[]): Promise<void> {
  const options = parseRunOptions(argv);
  const reference = argv.includes('--reference');
  const parallelism = Number(flagValue(argv, '--parallelism') ?? '1');
  if (!Number.isInteger(parallelism) || parallelism < 1) {
    throw new Error(`--parallelism must be a whole number of at least 1, got "${flagValue(argv, '--parallelism')}"`);
  }
  const ingotVersion = process.env.INGOT_IMAGE_TAG;
  if (!ingotVersion) {
    throw new Error('set INGOT_IMAGE_TAG to the Ingot image tag under test');
  }
  const testId = `lt-${Date.now().toString(36)}`;
  const configMap = `${testId}-scripts`;
  const archiveFile = `${options.scenario}.tar`;
  const testRun = buildTestRun({
    namespace: LOAD_NAMESPACE,
    testId,
    topology: options.topology,
    profile: options.profile,
    scenario: options.scenario,
    ingotVersion,
    parallelism,
    configMap,
    archiveFile,
  });
  const workDir = mkdtempSync(join(tmpdir(), 'ingot-load-'));
  const manifestPath = join(workDir, 'testrun.json');
  writeFileSync(manifestPath, JSON.stringify(testRun, null, 2));

  const ports: RunnerPorts = {
    labelledNodes: async () =>
      (await kubectl(labelledNodesCommand())).split('\n').filter((line) => line.trim() !== '').length,
    install: async () => {
      await exec('helm', [
        'upgrade', '--install', INGOT_RELEASE, chartPath,
        '--namespace', LOAD_NAMESPACE,
        '--values', join(loadtestRoot, 'topologies', `${options.topology}.yaml`),
        '--set', `image.tag=${ingotVersion}`,
        '--wait=false',
      ]);
    },
    ready: async () => {
      const statuses = (
        await kubectl([
          '-n', LOAD_NAMESPACE, 'get', 'pods',
          '-l', `app.kubernetes.io/instance=${INGOT_RELEASE}`,
          '-o', 'jsonpath={range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\\n"}{end}',
        ])
      ).split('\n').filter((line) => line.trim() !== '');
      return statuses.length > 0 && statuses.every((status) => status === 'True');
    },
    archive: async () => {
      // k6 archive runs in the pinned k6 image, so the archive matches the k6 that runs it in the cluster.
      await exec('docker', [
        'run', '--rm', '-v', `${loadtestRoot}:/work:ro`, '-v', `${workDir}:/out`, '-w', '/work',
        K6_IMAGE, 'archive', `scenarios/${options.scenario}.ts`, '-O', `/out/${archiveFile}`,
      ]);
      await kubectl([
        '-n', LOAD_NAMESPACE, 'create', 'configmap', configMap, '--from-file', join(workDir, archiveFile),
      ]);
    },
    apply: async () => {
      await kubectl(['apply', '-f', manifestPath]);
    },
    finished: async () => {
      const stage = (
        await kubectl([
          '-n', LOAD_NAMESPACE, 'get', 'testrun', testRun.metadata.name, '-o', 'jsonpath={.status.stage}',
        ])
      ).trim();
      if (stage === 'error') {
        throw new Error(`TestRun ${testRun.metadata.name} ended in the error stage`);
      }
      return stage === 'finished';
    },
    // The record step is `recordStep` of record.ts. The runner does not yet apply a pod size or read the measured outcome,
    // so a run writes no record until both are wired, rather than a record with a size that was never applied.
    record: async () => {},
    uninstall: async () => {
      await exec('helm', ['uninstall', INGOT_RELEASE, '--namespace', LOAD_NAMESPACE]);
    },
    pause: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  };

  await runSteps(ports, { now: () => Date.now() }, {
    namespace: LOAD_NAMESPACE,
    topology: options.topology,
    reference,
    readyTimeoutMs: READY_TIMEOUT_MS,
    completionTimeoutMs: COMPLETION_TIMEOUT_MS,
    pollMs: POLL_MS,
  });
}

main(process.argv.slice(2)).catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
