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
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { createRecord, fileRecordWriter, recordStep, writeResultRecord, type RecordInput } from './record.ts';

const input: RecordInput = {
  ingotVersion: '2.0.0',
  topology: 'monolith',
  size: 'S',
  storage: 'filesystem',
  nodeType: { cpuModel: 'Example CPU', cores: 4, memoryGiB: 16 },
  k6Version: '1.8.2',
  date: '2026-11-19',
  reference: true,
  scenario: 'resolve',
  outcome: { kind: 'rate', rate: 120, p95Ms: 340 },
};

/** A fake writer that records every call, so a test can check the path and the content without a file system. */
function fakeWriter() {
  const calls: Array<{ path: string; content: string }> = [];
  return {
    calls,
    write: async (path: string, content: string) => {
      calls.push({ path, content });
    },
  };
}

test('the record path names the version, topology, size and scenario', () => {
  const record = createRecord(input);

  assert.equal(record.path, 'loadtest/results/2.0.0/monolith-S-resolve.json');
});

test('a rate outcome keeps its rate and its p95 in the record', () => {
  const record = createRecord(input);

  assert.deepEqual(record.outcome, { kind: 'rate', rate: 120, p95Ms: 340 });
});

test('a not-ready outcome carries its step and no rate', () => {
  const record = createRecord({ ...input, outcome: { kind: 'not-ready', step: 80 } });

  assert.deepEqual(record.outcome, { kind: 'not-ready', step: 80 });
  assert.equal('rate' in record.outcome, false, 'a not-ready record must not carry a rate');
});

test('an oom-killed outcome carries its step and no rate', () => {
  const record = createRecord({ ...input, outcome: { kind: 'oom-killed', step: 100 } });

  assert.deepEqual(record.outcome, { kind: 'oom-killed', step: 100 });
  assert.equal('rate' in record.outcome, false, 'an oom-killed record must not carry a rate');
});

test('the pod size of the record comes from the reference size table', () => {
  const record = createRecord(input);

  assert.deepEqual(record.pod, { size: 'S', cpu: '1', memory: '1Gi' });
});

test('a record of a shared-node run keeps the reference flag false', () => {
  const record = createRecord({ ...input, reference: false });

  assert.equal(record.reference, false);
});

test('the same input serializes to identical bytes', async () => {
  const first = fakeWriter();
  const second = fakeWriter();

  await writeResultRecord(createRecord(input), first.write);
  await writeResultRecord(createRecord(input), second.write);

  assert.equal(first.calls[0]?.content, second.calls[0]?.content);
});

test('writing a record calls the writer once with its path and a JSON document', async () => {
  const writer = fakeWriter();

  await writeResultRecord(createRecord(input), writer.write);

  assert.equal(writer.calls.length, 1, 'exactly one file is written per record');
  assert.equal(writer.calls[0]?.path, 'loadtest/results/2.0.0/monolith-S-resolve.json');
  assert.deepEqual(JSON.parse(writer.calls[0]?.content ?? '').outcome, { kind: 'rate', rate: 120, p95Ms: 340 });
});

test('a version that would leave the results folder is refused', () => {
  assert.throws(() => createRecord({ ...input, ingotVersion: '../../secrets' }), /ingot version/);
});

test('a version with a path separator is refused', () => {
  assert.throws(() => createRecord({ ...input, ingotVersion: 'feat/x' }), /ingot version/);
});

test('a size that is not a reference size is refused', () => {
  assert.throws(() => createRecord({ ...input, size: 'XL' as never }), /not a reference size/);
});

test('a negative rate is refused', () => {
  assert.throws(() => createRecord({ ...input, outcome: { kind: 'rate', rate: -1, p95Ms: 10 } }), /rate/);
});

test('a date that is not a real calendar day is refused', () => {
  assert.throws(() => createRecord({ ...input, date: '2026-02-31' }), /date/);
});

test('a record without a Ingot version is refused', () => {
  assert.throws(() => createRecord({ ...input, ingotVersion: '' }), /ingot version/);
});

const { outcome: _measured, ...base } = input;

test('the record step writes the measured outcome of a completed run', async () => {
  const writer = fakeWriter();
  const step = recordStep({ base, measure: async () => ({ kind: 'rate', rate: 120, p95Ms: 340 }), write: writer.write });

  await step({ kind: 'completed' });

  assert.equal(writer.calls.length, 1);
  assert.equal(writer.calls[0]?.path, 'loadtest/results/2.0.0/monolith-S-resolve.json');
  assert.deepEqual(JSON.parse(writer.calls[0]?.content ?? '').outcome, { kind: 'rate', rate: 120, p95Ms: 340 });
});

test('the record step writes an oom-killed outcome that the measurement reports', async () => {
  const writer = fakeWriter();
  const step = recordStep({ base, measure: async () => ({ kind: 'oom-killed', step: 90 }), write: writer.write });

  await step({ kind: 'completed' });

  assert.deepEqual(JSON.parse(writer.calls[0]?.content ?? '').outcome, { kind: 'oom-killed', step: 90 });
});

test('the record step writes not-ready at step 0 without measuring when the pods never became ready', async () => {
  const writer = fakeWriter();
  let measured = false;
  const step = recordStep({
    base,
    measure: async () => {
      measured = true;
      return { kind: 'rate', rate: 1, p95Ms: 1 };
    },
    write: writer.write,
  });

  await step({ kind: 'not-ready' });

  assert.equal(measured, false, 'a deployment that never came up has nothing to measure');
  assert.deepEqual(JSON.parse(writer.calls[0]?.content ?? '').outcome, { kind: 'not-ready', step: 0 });
});

test('the record step writes nothing when a completed run has no measurement', async () => {
  const writer = fakeWriter();
  const step = recordStep({ base, measure: async () => undefined, write: writer.write });

  await step({ kind: 'completed' });

  assert.deepEqual(writer.calls, []);
});

/** A fresh repository root per test, so no test sees the files of another. */
function temporaryRoot(): string {
  return mkdtempSync(join(tmpdir(), 'ingot-record-'));
}

test('the file writer writes the record under loadtest/results/<version> of the repository root', async () => {
  const root = temporaryRoot();
  try {
    const path = await writeResultRecord(createRecord(input), fileRecordWriter(root));

    const written = readFileSync(join(root, path), 'utf8');
    assert.equal(path, 'loadtest/results/2.0.0/monolith-S-resolve.json');
    assert.deepEqual(JSON.parse(written).outcome, { kind: 'rate', rate: 120, p95Ms: 340 });
    assert.ok(written.endsWith('}\n'), 'the record ends with one newline, as a checked-in file should');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the file writer replaces the record of an earlier run of the same version, topology, size and scenario', async () => {
  const root = temporaryRoot();
  try {
    const writer = fileRecordWriter(root);
    await writeResultRecord(createRecord(input), writer);
    const path = await writeResultRecord(
      createRecord({ ...input, outcome: { kind: 'rate', rate: 150, p95Ms: 300 } }),
      writer,
    );

    assert.deepEqual(JSON.parse(readFileSync(join(root, path), 'utf8')).outcome, { kind: 'rate', rate: 150, p95Ms: 300 });
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the file writer refuses a path outside the results folder and writes nothing', async () => {
  const root = temporaryRoot();
  try {
    const writer = fileRecordWriter(root);

    await assert.rejects(writer('loadtest/results/../../escape.json', '{}\n'), /outside loadtest\/results/);
    await assert.rejects(writer('docs/record.json', '{}\n'), /outside loadtest\/results/);
    assert.equal(existsSync(join(root, 'escape.json')), false, 'a refused record must not be written');
    assert.equal(existsSync(join(root, 'docs')), false, 'a refused record must not create a folder');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
