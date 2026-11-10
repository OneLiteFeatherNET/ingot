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

package net.onelitefeather.ingot.readonly

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade

/**
 * The read-only state of this instance, for the request dispatch, the scheduled runs and the status, and the toggle that
 * stores a change of it.
 */
class ReadOnlyFacade internal constructor(
    private val cache: ReadOnlyStateCache,
    private val store: ReadOnlyStore,
    private val setting: ReadOnlySetting,
    private val extensions: Extensions,
) : Facade {

    fun state(): ReadOnlyState =
        cache.current()

    fun isReadOnly(): Boolean =
        state().enabled

    /** Re-reads the stored state at once, for a change that another instance announced. */
    fun refresh() =
        cache.refresh()

    /**
     * Stores [enabled] as the state. Nothing is stored when the environment forces the state or when the stored state already
     * has the value. A change is written with a compare-and-save, so a toggle that lost a race answers [ToggleOutcome.Conflict]
     * and stores nothing. Every stored change writes one audit event with [actor].
     */
    fun set(enabled: Boolean, actor: AuditActor): ToggleOutcome {
        if (setting.forced) return ToggleOutcome.ForcedByEnvironment

        val stored = store.read()
        if ((stored?.enabled ?: false) == enabled) {
            return ToggleOutcome.Unchanged(ReadOnlyState(enabled, ReadOnlySource.STORED))
        }
        if (!store.compareAndSave(expectedRevision = stored?.revision, enabled = enabled)) {
            return ToggleOutcome.Conflict
        }

        cache.refresh()
        extensions.emitEvent(AuditEvent(action = AuditAction.READ_ONLY_CHANGED, actor = actor, details = mapOf("enabled" to enabled)))
        return ToggleOutcome.Stored(ReadOnlyState(enabled, ReadOnlySource.STORED))
    }

}

/** The answer of a toggle. Only [Stored] changed anything. */
sealed interface ToggleOutcome {

    /** The stored state was changed to the requested value. */
    data class Stored(val state: ReadOnlyState) : ToggleOutcome

    /** The stored state already had the requested value, so nothing was written. */
    data class Unchanged(val state: ReadOnlyState) : ToggleOutcome

    /** The environment forces the state, so the stored state cannot be changed from here. */
    data object ForcedByEnvironment : ToggleOutcome

    /** Another writer changed the stored state while this toggle was handled. */
    data object Conflict : ToggleOutcome
}

/**
 * The requests that stay answered while read-only is on: login, session refresh and the toggle itself. Matching is by the
 * route template and the method, so a path that only shares a prefix with one of them is not exempt.
 */
object ReadOnlyRoutes {

    private val exempt = setOf(
        "POST /api/auth/login",
        "POST /api/auth/refresh",
        "POST /api/admin/read-only",
        "DELETE /api/admin/read-only",
    )

    fun isExempt(method: String, routeTemplate: String): Boolean =
        "$method $routeTemplate" in exempt

}
