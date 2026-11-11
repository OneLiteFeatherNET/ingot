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
package net.onelitefeather.ingot.nexusimport.jobs

enum class JobStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

/**
 * Why a job waits. [READ_ONLY] ends by itself when read-only mode ends, [OPERATOR] waits for the operator's resume, and
 * [CREDENTIALS_NEEDED] waits until the operator supplies the password again.
 */
enum class PauseReason { READ_ONLY, CREDENTIALS_NEEDED, OPERATOR }

/** The outcome of one file. Only [COPIED] and [SKIPPED_SAME] mean the file is in the target. */
enum class AssetResult { COPIED, SKIPPED_SAME, CONFLICT, FAILED, CHECKSUM_FAILED, GONE }

/** The state of one repository of a job. [COPIED] means every file was handled; only [VERIFIED] means the verify pass agreed. */
enum class RepositoryStatus { PLANNED, COPIED, CONFLICTED, FAILED, VERIFIED, MISMATCH }
