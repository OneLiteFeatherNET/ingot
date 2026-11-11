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

// The words of the job states, the pause reasons and the statuses. Colour never carries a state on its own: each
// one is named in text here, and the views add an icon or a shape next to the text.

const JOB_STATUS_TEXT = {
  QUEUED: 'Queued',
  RUNNING: 'Running',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
}

const PAUSE_REASON_TEXT = {
  READ_ONLY: 'Ingot is in read-only mode. The job resumes by itself when read-only mode ends.',
  CREDENTIALS_NEEDED: 'Nexus needs the password again. Enter it to let the job continue.',
  OPERATOR: 'The job waits for you. Resume it to continue.',
}

const LOSS_STATUSES = ['MAPPED', 'PARTIAL', 'MANUAL', 'UNSUPPORTED', 'EXCLUDED']

const LOSS_STATUS_TEXT = {
  MAPPED: 'Mapped',
  PARTIAL: 'Partial',
  MANUAL: 'Manual',
  UNSUPPORTED: 'Unsupported',
  EXCLUDED: 'Excluded',
}

const REPOSITORY_RESULT_TEXT = {
  PLANNED: 'Planned',
  COPIED: 'Copied',
  CONFLICTED: 'Conflicted',
  FAILED: 'Failed',
  VERIFIED: 'Verified',
  MISMATCH: 'Mismatch',
}

// The results a repository has when its work for the job is over. A repository still planned or copying is not.
const SETTLED_RESULTS = ['COPIED', 'CONFLICTED', 'FAILED', 'VERIFIED', 'MISMATCH']

const statusText = (status) => JOB_STATUS_TEXT[status] ?? String(status ?? 'Unknown')
const pauseText = (reason) => PAUSE_REASON_TEXT[reason] ?? ''
const lossStatusText = (status) => LOSS_STATUS_TEXT[status] ?? String(status ?? '')
const repositoryResultText = (status) => REPOSITORY_RESULT_TEXT[status] ?? String(status ?? '')

export {
  LOSS_STATUSES,
  SETTLED_RESULTS,
  statusText,
  pauseText,
  lossStatusText,
  repositoryResultText,
}
