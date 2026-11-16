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

package net.onelitefeather.ingot.deploy

/**
 * The singleton background jobs of design F2. They run once per cluster, so only an instance that runs the background
 * jobs submits them. The per-process duties (statistics flush, settings reload, status snapshots, cluster event prune)
 * are not listed here and never pass through [BackgroundJobGate].
 */
enum class SingletonJob(val id: String) {
    CLEANUP_SCHEDULE("cleanup-schedule"),
    AUDIT_PURGE("audit-purge"),
    SEARCH_OUTBOX("search-outbox"),
    SEARCH_REBUILD("search-rebuild"),
    SEARCH_VERIFY("search-verify"),
    SEARCH_TOKEN_SWEEP("search-token-sweep"),
}

/**
 * Submits a singleton job only on an instance that runs the background jobs, as decided by
 * [BackgroundJobsSetting.resolve]. A skipped job returns `null` and is never scheduled, so this instance takes no claim
 * for it.
 */
class BackgroundJobGate(private val enabled: Boolean) {

    /** Runs [submission] when this instance runs the singleton jobs. [job] names the call site. */
    @Suppress("UNUSED_PARAMETER")
    fun <T> submit(job: SingletonJob, submission: () -> T): T? = if (enabled) submission() else null

}
