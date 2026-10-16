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

package com.reposilite.scheduling

import java.time.Clock
import java.time.ZoneOffset

/**
 * Decides which instance of a cluster runs a scheduled job. Every instance that shares a database runs the same
 * schedule, so each job is claimed under a key before it starts, and only the first claim of a key is granted.
 */
interface ScheduledJobClaims {

    /** Returns true for exactly one caller per [key] among the instances that share the database. Keys are not reused. */
    fun claim(key: String): Boolean

    /** Without a shared database there is no other instance to coordinate with, so every claim is granted. */
    object Unshared : ScheduledJobClaims {
        override fun claim(key: String): Boolean = true
    }

}

/** The key of a job that runs once per UTC day, for example `cleanup:nightly:2026-10-15`. */
fun utcDayKey(job: String, clock: Clock): String =
    "$job:${clock.instant().atZone(ZoneOffset.UTC).toLocalDate()}"
