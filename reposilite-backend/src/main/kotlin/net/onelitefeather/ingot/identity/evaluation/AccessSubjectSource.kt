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

package net.onelitefeather.ingot.identity.evaluation

import com.reposilite.token.AccessTokenIdentifier
import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.PrincipalRecord

/** Loads the subject of a persistent token or of a principal from the identity tables. Null when nothing matches. */
internal interface AccessSubjectSource {
    fun subjectOf(identifier: AccessTokenIdentifier): AccessSubject?

    /** The subject of a principal acting directly, such as a browser session. Null when the principal does not exist. */
    fun subjectOfPrincipal(principalId: Int): AccessSubject?

    fun principalOf(principalId: Int): PrincipalRecord?
}
